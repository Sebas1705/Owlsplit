package es.sebas1705.owlsplit.data.repository.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.repository.Writer
import es.sebas1705.owlsplit.data.repository.json
import es.sebas1705.owlsplit.data.repository.toModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** Outbox bookkeeping, pull cursors and access flags. Every operation is atomic. */
internal class SyncLocalStore(private val db: AppDatabase, private val writer: Writer) {
    val sent = SentWrites(db)

    fun now(): Long = writer.now()

    /** The whole outbox in upload order. */
    fun all(): List<Outbox> = db.outboxQueries.selectAll().executeAsList()

    /**
     * When the next upload pass can make progress: the earliest retry time among the first entry
     * of each group (later entries wait behind their group head). -1 when the outbox is empty.
     */
    fun nextWakeAt(): Long =
        all().groupBy { it.group_id }.values.minOfOrNull { it.first().next_attempt_at } ?: -1L

    fun scheduleRetry(entry: Outbox, at: Long, error: String) = db.outboxQueries.scheduleRetry(at, error, entry.id)

    fun isAccessLost(groupId: String): Boolean = db.metaQueries.get(accessLostKey(groupId)).executeAsOneOrNull() == "1"

    /** Lost: the group dropped me. Not lost: back in it (maybe with another account), it uploads again. */
    fun setAccessLost(
        groupId: String,
        lost: Boolean
    ) = db.metaQueries.put(accessLostKey(groupId), if (lost) "1" else "0")

    /** Local groups already known to the server that list [uid] as member. */
    fun syncedGroupIdsOf(uid: String): Set<String> =
        db.groupEntityQueries.selectVisible().executeAsList()
            .map { it.toModel() }
            .filter { it.meta.serverVersion > 0 && uid in it.memberUids }
            .mapTo(HashSet()) { it.id }

    fun cursor(groupId: String, collection: RemoteCollection): Long =
        db.syncCursorQueries.get(groupId, collection.name).executeAsOneOrNull() ?: 0L

    fun advanceCursor(groupId: String, collection: RemoteCollection, updatedAt: Long) = db.transaction {
        if (updatedAt > cursor(groupId, collection)) db.syncCursorQueries.put(groupId, collection.name, updatedAt)
    }

    /** After [entry] was written remotely as [version]. */
    /**
     * The pending entry [doc] is the server copy of: our own write whose acknowledgement was lost.
     * Offline, `push` gives up while Firestore keeps the write queued and sends it once back online.
     */
    fun ownPendingWrite(doc: RemoteDoc): Outbox? =
        if (doc is RemoteDoc.ChangeLogDoc) {
            null
        } else {
            db.outboxQueries.selectByEntity(doc.type.name, doc.id).executeAsOneOrNull()
                ?.takeIf { it.base_version + 1 == doc.version && doc.sameContentAs(it.payload) }
        }

    /** A write of ours other than the pending payload landed as [version]: the pending edit chains on top of it. Returns the entry. */
    fun onEarlierWriteLanded(entry: Outbox, version: Long): Outbox? = db.transactionWithResult {
        val current = db.outboxQueries.selectById(entry.id).executeAsOneOrNull() ?: return@transactionWithResult null
        db.outboxQueries.advanceBase(version, current.id)
        setServerVersion(OutboxType.valueOf(current.entity_type), current.entity_id, version)
        sent.clear(current)
        db.outboxQueries.selectById(current.id).executeAsOneOrNull()
    }

    fun onPushed(entry: Outbox, version: Long) = db.transaction {
        val current = db.outboxQueries.selectById(entry.id).executeAsOneOrNull() ?: return@transaction
        val type = OutboxType.valueOf(entry.entity_type)
        if (current.payload == entry.payload) {
            db.outboxQueries.deleteById(entry.id)
            markSynced(type, entry.entity_id, version)
            sent.clear(entry)
        } else {
            // Edited again while uploading: the next upload chains on what was just written.
            db.outboxQueries.advanceBase(version, entry.id)
            setServerVersion(type, entry.entity_id, version)
        }
    }

    private fun markSynced(type: OutboxType, id: String, version: Long) {
        when (type) {
            OutboxType.GROUP -> db.groupEntityQueries.markSynced(version, id)
            OutboxType.MEMBER -> db.memberEntityQueries.markSynced(version, id)
            OutboxType.EXPENSE -> db.expenseEntityQueries.markSynced(version, id)
            OutboxType.TRANSFER -> db.transferEntityQueries.markSynced(version, id)
            OutboxType.CHANGE_LOG -> db.changeLogQueries.markSynced(id)
        }
    }

    private fun setServerVersion(type: OutboxType, id: String, version: Long) {
        when (type) {
            OutboxType.GROUP -> db.groupEntityQueries.setServerVersion(version, id)
            OutboxType.MEMBER -> db.memberEntityQueries.setServerVersion(version, id)
            OutboxType.EXPENSE -> db.expenseEntityQueries.setServerVersion(version, id)
            OutboxType.TRANSFER -> db.transferEntityQueries.setServerVersion(version, id)
            OutboxType.CHANGE_LOG -> Unit
        }
    }
}

/** Meta key marking a group that dropped me (Outbox.sq countUploadable reads the same prefix). */
internal fun accessLostKey(groupId: String) = "accessLost:$groupId"

/** What [GroupPuller] remembers between runs, besides the cursors. */
internal class PullMarks(private val db: AppDatabase) {
    /** The group had its first full pull by query; from then on the changelog drives what is fetched. */
    fun isBootstrapped(groupId: String): Boolean =
        db.metaQueries.get(bootstrappedKey(groupId)).executeAsOneOrNull() == "1"

    fun setBootstrapped(groupId: String) = db.metaQueries.put(bootstrappedKey(groupId), "1")

    fun observeBootstrapped(groupId: String, io: CoroutineDispatcher): Flow<Boolean> =
        db.metaQueries.get(bootstrappedKey(groupId)).asFlow().mapToOneOrNull(io).map { it == "1" }

    /** The history entry is already here: written on this device, or pulled with what it points at. */
    fun isChangeLogKnown(id: String): Boolean = db.changeLogQueries.selectById(id).executeAsOneOrNull() != null

    private fun bootstrappedKey(groupId: String) = "pulled:$groupId"
}

/**
 * What an upload that gave up handed to Firestore: the SDK keeps such a write queued and may send it later,
 * even after the document was edited again (docs/SYNC.md, "Writes that outlive their upload").
 */
internal class SentWrites(private val db: AppDatabase) {
    fun remember(entry: Outbox, payload: String = entry.payload) {
        val payloads = payloads(entry)
        if (payload !in payloads) put(entry, payloads + payload)
    }

    fun clear(entry: Outbox) {
        if (payloads(entry).isNotEmpty()) put(entry, emptyList())
    }

    fun contains(entry: Outbox, doc: RemoteDoc): Boolean = payloads(entry).any { doc.sameContentAs(it) }

    /** The pending entry [doc] is the server copy of an earlier payload of: ours landing late, not a conflict. */
    fun earlierOwnWrite(doc: RemoteDoc): Outbox? =
        if (doc is RemoteDoc.ChangeLogDoc) {
            null
        } else {
            db.outboxQueries.selectByEntity(doc.type.name, doc.id).executeAsOneOrNull()
                ?.takeIf { it.base_version + 1 == doc.version && contains(it, doc) }
        }

    private fun payloads(entry: Outbox): List<String> =
        db.metaQueries.get(key(entry)).executeAsOneOrNull()
            ?.let { json.decodeFromString(ListSerializer(String.serializer()), it) }.orEmpty()

    private fun put(entry: Outbox, payloads: List<String>) =
        db.metaQueries.put(key(entry), json.encodeToString(ListSerializer(String.serializer()), payloads))

    private fun key(entry: Outbox) = "sent:${entry.entity_type}:${entry.entity_id}"
}
