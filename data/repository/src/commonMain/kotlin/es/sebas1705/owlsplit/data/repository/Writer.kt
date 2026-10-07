package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState

/**
 * Shared write path (docs/SYNC.md, "Escritura local"). Must be called inside `db.transaction {}`.
 */
internal class Writer(private val db: AppDatabase, private val ctx: RepositoryContext) {

    fun now(): Long = ctx.clock.now().toEpochMilliseconds()

    fun uid(): String = ctx.uid.currentUid()

    fun newId(): String = ctx.ids.newId()

    fun newInviteCode(): String = ctx.inviteCodes.newCode()

    /** Metadata of a brand-new document. */
    fun createdMeta() = SyncMeta(
        version = 1,
        serverVersion = 0,
        updatedAt = null,
        updatedBy = uid(),
        deleted = false,
        syncState = SyncState.PENDING,
    )

    /** Metadata after a local edit of a document whose last synced version is [old].serverVersion. */
    fun editedMeta(old: SyncMeta, deleted: Boolean = old.deleted) = old.copy(
        version = old.serverVersion + 1,
        updatedBy = uid(),
        deleted = deleted,
        syncState = SyncState.PENDING,
    )

    /** Queues [payload] for upload, compacting with a pending entry of the same document. */
    fun enqueue(groupId: String, type: OutboxType, entityId: String, baseVersion: Long, payload: String) {
        val pending = db.outboxQueries.selectByEntity(type.name, entityId).executeAsOneOrNull()
        if (pending == null) {
            db.outboxQueries.insert(groupId, type.name, entityId, baseVersion, payload, now())
        } else {
            db.outboxQueries.replacePayload(payload, pending.id)
        }
    }

    fun log(groupId: String, type: EntityType, entityId: String, action: ChangeAction, diff: List<FieldChange>) {
        val entry = ChangeLogEntry(newId(), groupId, type, entityId, action, uid(), diff, now())
        db.changeLogQueries.insert(entry.toRow(SyncState.PENDING))
        enqueue(groupId, OutboxType.CHANGE_LOG, entry.id, 0, json.encodeToString(ChangeLogEntry.serializer(), entry))
    }
}
