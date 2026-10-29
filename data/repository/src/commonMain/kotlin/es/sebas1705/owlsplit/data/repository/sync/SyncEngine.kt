package es.sebas1705.owlsplit.data.repository.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.Writer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.random.Random

/**
 * Synchronises the local database with the server (docs/SYNC.md). [start] runs the upload loop
 * and the remote listeners ("my groups", plus one changelog listener per group not archived by me,
 * see [GroupPuller]) until its scope is cancelled; [pushNow] is the hook for platform triggers
 * (WorkManager, app back in foreground) and [refreshGroup] the one for a group on screen.
 */
class SyncEngine(
    db: AppDatabase,
    private val remote: RemoteStore,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
    random: Random = Random.Default,
) : SyncControl {
    private val writer = Writer(db, ctx)
    private val local = SyncLocalStore(db, writer)
    private val applier = RemoteApplier(db, writer)
    private val inFlight = InFlightUpload()
    private val processor = OutboxProcessor(local, applier, remote, random, inFlight)
    private val pushLock = Mutex()
    private val puller = GroupPuller(local, PullMarks(db), remote, ::receive)
    private val refreshRequests =
        MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Pending changes that can still be uploaded: writes to groups that dropped me never will. */
    override val pendingCount: Flow<Long> = db.outboxQueries.countUploadable().asFlow().mapToOne(io)

    override fun isAccessLost(groupId: String): Boolean = local.isAccessLost(groupId)

    suspend fun pushNow() = withContext(io) { pushLock.withLock { processor.processReady() } }

    /**
     * Uploads everything that can be uploaded now, retry delays ignored (sign out, account deletion).
     * False when something is left, e.g. offline. Writes to groups I lost access to do not count.
     */
    override suspend fun flush(): Boolean = withContext(io) {
        pushLock.withLock {
            var left = uploadable()
            while (left > 0) {
                processor.processReady(ignoreSchedule = true)
                val now = uploadable()
                if (now >= left) return@withLock false
                left = now
            }
            true
        }
    }

    private fun uploadable() = local.all().count { !local.isAccessLost(it.group_id) }

    fun start(scope: CoroutineScope, uid: String): Job = scope.launch(io) {
        launch { pushLoop() }
        launch { pullMyGroups(uid) }
    }

    private suspend fun pushLoop() {
        pendingCount.collectLatest { pending ->
            if (pending == 0L) return@collectLatest
            while (true) {
                // Each upload deletes its outbox row, which changes the count and makes collectLatest
                // cancel this block while the next upload awaits its ack. The server has already
                // accepted that write, but the row stays and would be pushed again (a create-only
                // history entry is then refused). So a pass always finishes; only the wait is cancelled.
                withContext(NonCancellable) { pushNow() }
                val next = local.nextWakeAt()
                if (next < 0) break
                val wait = next - local.now()
                if (wait > 0) delay(wait) else yield()
            }
        }
    }

    /** Queues a background catch-up of [groupId] (see [SyncControl.refreshGroup]); dropped while stopped. */
    override fun refreshGroup(groupId: String) {
        refreshRequests.tryEmit(groupId)
    }

    private suspend fun pullMyGroups(uid: String) = coroutineScope {
        val listeners = HashMap<String, Job>()
        val groups = MutableStateFlow<MyGroups?>(null)
        launch { serveRefreshes(groups) }
        remote.observeMyGroups(uid).collect { docs ->
            // Before applying: once applied, a group that dropped me no longer lists me locally either.
            val known = local.syncedGroupIdsOf(uid)
            docs.forEach(::receive)
            val ids = docs.filterNot { it.group.meta.deleted }.mapTo(HashSet()) { it.id }
            (known - ids).forEach { local.setAccessLost(it, lost = true) }
            ids.filter(local::isAccessLost).forEach { local.setAccessLost(it, lost = false) }
            // Archived by me: no listener. It catches up when opened, or from its cursor once unarchived.
            val active = docs.filter { it.id in ids && uid !in it.group.archivedBy }.mapTo(HashSet()) { it.id }
            groups.value = MyGroups(ids, active)
            (listeners.keys - active).forEach { listeners.remove(it)?.cancel() }
            (active - listeners.keys).forEach { id -> listeners[id] = launch { puller.listen(id) } }
        }
    }

    /**
     * One catch-up at a time, at most one per group every [REFRESH_INTERVAL_MILLIS] (a screen reopened).
     * A refresh while the group had a listener only pulled its members, so it does not hold back the
     * changelog catch-up once I archive it.
     */
    private suspend fun serveRefreshes(groups: StateFlow<MyGroups?>) {
        val refreshed = HashMap<String, Refresh>()
        refreshRequests.collect { groupId ->
            val mine = groups.filterNotNull().first()
            val listened = groupId in mine.listened
            val last = refreshed[groupId]
            val now = local.now()
            val recent = last != null && last.listened == listened && now - last.at < REFRESH_INTERVAL_MILLIS
            if (groupId !in mine.all || recent) return@collect
            if (puller.catchUp(groupId, listened) == PullStep.DONE) refreshed[groupId] = Refresh(now, listened)
        }
    }

    private class Refresh(val at: Long, val listened: Boolean)

    /** The groups that list me (not deleted) and, among them, those with a live listener (not archived by me). */
    private class MyGroups(val all: Set<String>, val listened: Set<String>)

    /**
     * Applies a listener document, unless it is our own write: the echo of the upload in flight, or of
     * one whose acknowledgement was lost. Then it acknowledges it instead of taking it as a conflict.
     */
    private fun receive(doc: RemoteDoc) {
        val echoed = inFlight.echoed(doc) ?: local.ownPendingWrite(doc)?.let { it to doc.version }
        if (echoed != null) local.onPushed(echoed.first, echoed.second) else applier.apply(doc)
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 5 * 60 * 1_000L
    }
}
