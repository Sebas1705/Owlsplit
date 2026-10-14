package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * In-memory server that mimics the P5 rules that matter to sync: chained versions, member-only
 * access (a group can always be created by its first member) and server time. It also counts
 * reads the way Firestore bills them: a query or a listener's first answer costs one read per
 * document (one at least), each later change one read, and a document get one read.
 */
class FakeRemoteStore(private val now: () -> Long) : RemoteStore {
    private val docs = MutableStateFlow<Map<String, RemoteDoc>>(emptyMap())
    var callerUid = "uid-ana"
    val scripted = ArrayDeque<PushResult>()
    val pushLog = mutableListOf<Pair<OutboxType, String>>()

    /** Runs when `push` is called, before the write is judged. */
    var beforePush: suspend () -> Unit = {}

    /** Runs after an accepted write and before `push` returns, e.g. to let listeners see it first. */
    var beforeAck: suspend () -> Unit = {}

    /** Accepted writes whose acknowledgement is lost: the server keeps them, `push` times out (Transient). */
    var lostAcks = 0

    /** The next [failingReads] fetches and pulls fail as if offline. */
    var failingReads = 0

    /** Billed reads so far. */
    var reads = 0L

    /** Listeners currently open, by group and collection. */
    val listening = mutableMapOf<Pair<String, RemoteCollection>, Int>()
    val pulls = mutableListOf<Pair<String, RemoteCollection>>()
    val fetches = mutableListOf<Pair<OutboxType, String>>()

    private var nextEntry = 0

    fun doc(type: OutboxType, id: String): RemoteDoc? = docs.value[key(type, id)]

    /** Another device wrote [doc] directly on the server. */
    fun serverWrite(doc: RemoteDoc) = docs.update { it + (key(doc.type, doc.id) to doc) }

    /**
     * Another device's edit as the app makes it: the document, then its history entry (server time
     * just after). Personal fields ([logged] false) stay out of the history.
     */
    fun editByOtherDevice(doc: RemoteDoc, author: String = "uid-luis", logged: Boolean = true) {
        serverWrite(doc)
        if (!logged) return
        val entry = ChangeLogEntry(
            id = "remote-log-${nextEntry++}",
            groupId = doc.groupId,
            entityType = EntityType.valueOf(doc.type.name),
            entityId = doc.id,
            action = ChangeAction.UPDATED,
            authorUid = author,
            diff = emptyList(),
            createdAt = doc.updatedAt,
        )
        serverWrite(RemoteDoc.ChangeLogDoc(entry, updatedAt = doc.updatedAt + 1))
    }

    override suspend fun push(type: OutboxType, groupId: String, entityId: String, version: Long, payload: String): PushResult {
        pushLog += type to entityId
        beforePush()
        scripted.removeFirstOrNull()?.let { return it }
        val current = doc(type, entityId)
        val allowed = when {
            type == OutboxType.CHANGE_LOG -> current == null && isMember(groupId)
            type == OutboxType.GROUP && current == null -> version == 1L
            else -> isMember(groupId) && version == (current?.version ?: 0L) + 1
        }
        if (!allowed) return PushResult.Rejected
        serverWrite(decodeRemoteDoc(type, payload, version, now(), callerUid))
        if (lostAcks > 0) {
            lostAcks--
            return PushResult.Transient
        }
        beforeAck()
        return PushResult.Ok
    }

    override suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult {
        fetches += type to entityId
        if (failingReads > 0) {
            failingReads--
            return FetchResult.Transient
        }
        reads++
        if (doc(OutboxType.GROUP, groupId) != null && !isMember(groupId)) return FetchResult.NoAccess
        return doc(type, entityId)?.let { FetchResult.Found(it) } ?: FetchResult.Missing
    }

    override suspend fun pull(groupId: String, collection: RemoteCollection, sinceMillis: Long): PullResult {
        pulls += groupId to collection
        if (failingReads > 0) {
            failingReads--
            return PullResult.Transient
        }
        if (!isMember(groupId)) return PullResult.NoAccess
        val found = matching(docs.value, groupId, collection, sinceMillis)
        reads += maxOf(1, found.size)
        return PullResult.Docs(found)
    }

    override fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>> =
        billed { all -> all.values.filterIsInstance<RemoteDoc.GroupDoc>().filter { uid in it.group.memberUids } }
            // Like the real adapter: always the full set of my groups.
            .let { changes -> flow { changes.collect { emit(myGroups(uid)) } } }

    override fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>> {
        val listener = groupId to collection
        val changes = billed { all -> matching(all, groupId, collection, sinceMillis) }
        return flow {
            listening[listener] = (listening[listener] ?: 0) + 1
            try {
                changes.collect { emit(it) }
            } finally {
                listening[listener] = listening.getValue(listener) - 1
            }
        }
    }

    /** Emits the first answer whole and then only what changed, billing as Firestore does. */
    private fun <T : RemoteDoc> billed(select: (Map<String, RemoteDoc>) -> List<T>): Flow<List<T>> = flow {
        var seen: Map<String, T>? = null
        docs.collect { all ->
            val now = select(all).associateBy { key(it.type, it.id) }
            val previous = seen
            val changed = if (previous == null) now.values.toList() else now.filter { (k, v) -> previous[k] != v }.values.toList()
            seen = now
            // A document leaving the result is news too (it costs no read).
            if (previous == null || changed.isNotEmpty() || previous.keys != now.keys) {
                reads += if (previous == null) maxOf(1, changed.size).toLong() else changed.size.toLong()
                emit(changed)
            }
        }
    }

    private fun myGroups(uid: String) =
        docs.value.values.filterIsInstance<RemoteDoc.GroupDoc>().filter { uid in it.group.memberUids }

    private fun matching(all: Map<String, RemoteDoc>, groupId: String, collection: RemoteCollection, since: Long) =
        all.values.filter { it.groupId == groupId && it.type == collection.type && it.updatedAt >= since }

    private fun isMember(groupId: String): Boolean =
        (doc(OutboxType.GROUP, groupId) as? RemoteDoc.GroupDoc)?.group?.memberUids?.contains(callerUid) == true

    private fun key(type: OutboxType, id: String) = "$type/$id"
}
