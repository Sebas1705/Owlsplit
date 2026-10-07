package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory server that mimics the P5 rules that matter to sync: chained versions, member-only
 * access (a group can always be created by its first member) and server time.
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

    fun doc(type: OutboxType, id: String): RemoteDoc? = docs.value[key(type, id)]

    /** Another device wrote [doc] directly on the server. */
    fun serverWrite(doc: RemoteDoc) = docs.update { it + (key(doc.type, doc.id) to doc) }

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
        beforeAck()
        return PushResult.Ok
    }

    override suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult {
        if (doc(OutboxType.GROUP, groupId) != null && !isMember(groupId)) return FetchResult.NoAccess
        return doc(type, entityId)?.let { FetchResult.Found(it) } ?: FetchResult.Missing
    }

    override fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>> =
        docs.map { all -> all.values.filterIsInstance<RemoteDoc.GroupDoc>().filter { uid in it.group.memberUids } }

    override fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>> =
        docs.map { all ->
            all.values.filter { it.groupId == groupId && it.type == collection.type && it.updatedAt >= sinceMillis }
        }

    private fun isMember(groupId: String): Boolean =
        (doc(OutboxType.GROUP, groupId) as? RemoteDoc.GroupDoc)?.group?.memberUids?.contains(callerUid) == true

    private fun key(type: OutboxType, id: String) = "$type/$id"
}
