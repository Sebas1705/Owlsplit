package es.sebas1705.owlsplit.data.repository.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.Writer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.random.Random

/**
 * Synchronises the local database with the server (docs/SYNC.md). [start] runs the upload loop
 * and the remote listeners until its scope is cancelled; [pushNow] is the hook for platform
 * triggers (WorkManager, app back in foreground).
 */
class SyncEngine(
    db: AppDatabase,
    private val remote: RemoteStore,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
    random: Random = Random.Default,
) {
    private val local = SyncLocalStore(db, Writer(db, ctx))
    private val processor = OutboxProcessor(local, remote, random)
    private val pushLock = Mutex()

    /** Number of writes waiting to be uploaded. */
    val pendingCount: Flow<Long> = db.outboxQueries.countAll().asFlow().mapToOne(io)

    fun isAccessLost(groupId: String): Boolean = local.isAccessLost(groupId)

    suspend fun pushNow() = withContext(io) { pushLock.withLock { processor.processReady() } }

    fun start(scope: CoroutineScope, uid: String): Job = scope.launch(io) {
        launch { pushLoop() }
        launch { pullMyGroups(uid) }
    }

    private suspend fun pushLoop() {
        pendingCount.collectLatest { pending ->
            if (pending == 0L) return@collectLatest
            while (true) {
                pushNow()
                val next = local.nextWakeAt()
                if (next < 0) break
                val wait = next - local.now()
                if (wait > 0) delay(wait) else yield()
            }
        }
    }

    private suspend fun pullMyGroups(uid: String) = coroutineScope {
        val listeners = HashMap<String, Job>()
        remote.observeMyGroups(uid).collect { docs ->
            // Before applying: once applied, a group that dropped me no longer lists me locally either.
            val known = local.syncedGroupIdsOf(uid)
            docs.forEach { local.apply(it) }
            val ids = docs.filterNot { it.group.meta.deleted }.mapTo(HashSet()) { it.id }
            (known - ids).forEach { local.markAccessLost(it) }
            (listeners.keys - ids).forEach { listeners.remove(it)?.cancel() }
            (ids - listeners.keys).forEach { id -> listeners[id] = launch { pullGroup(id) } }
        }
    }

    private suspend fun pullGroup(groupId: String) = coroutineScope {
        RemoteCollection.entries.forEach { collection ->
            launch {
                val since = (local.cursor(groupId, collection) - CURSOR_MARGIN_MILLIS).coerceAtLeast(0)
                remote.observe(groupId, collection, since).collect { docs ->
                    docs.forEach { local.apply(it) }
                    docs.maxOfOrNull { it.updatedAt }?.let { local.advanceCursor(groupId, collection, it) }
                }
            }
        }
    }

    private companion object {
        /** Re-read a little before the cursor: concurrent writes can carry close server times. */
        const val CURSOR_MARGIN_MILLIS = 5 * 60 * 1_000L
    }
}
