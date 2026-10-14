package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.repository.Writer
import es.sebas1705.owlsplit.data.repository.toModel

/** Outbox bookkeeping, pull cursors and access flags. Every operation is atomic. */
internal class SyncLocalStore(private val db: AppDatabase, private val writer: Writer) {

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

    fun onPushed(entry: Outbox, version: Long) = db.transaction {
        val current = db.outboxQueries.selectById(entry.id).executeAsOneOrNull() ?: return@transaction
        val type = OutboxType.valueOf(entry.entity_type)
        if (current.payload == entry.payload) {
            db.outboxQueries.deleteById(entry.id)
            markSynced(type, entry.entity_id, version)
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
