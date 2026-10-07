package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.Writer
import es.sebas1705.owlsplit.data.repository.toRow

/** Writes server documents into the local database and resolves conflicts (docs/SYNC.md, "Bajada"). */
internal class RemoteApplier(private val db: AppDatabase, private val writer: Writer) {

    /** Applies a remote document. Returns true if local data changed. */
    fun apply(remote: RemoteDoc): Boolean = db.transactionWithResult {
        if (remote is RemoteDoc.ChangeLogDoc) {
            db.changeLogQueries.insert(remote.entry.toRow(SyncState.SYNCED))
            return@transactionWithResult true
        }
        val known = serverVersion(remote.type, remote.id)
        val pending = db.outboxQueries.selectByEntity(remote.type.name, remote.id).executeAsOneOrNull()
        when {
            known != null && remote.version < known -> false
            // The echo of our own upload while we already edited again: keep the pending edit.
            known != null && remote.version == known && pending != null -> false
            else -> {
                if (pending != null) recordConflict(pending, remote)
                write(remote)
                true
            }
        }
    }

    /** The server kept someone else's version: store ours (latest edit) as a conflict and adopt theirs. */
    fun resolveConflict(entry: Outbox, remote: RemoteDoc) = db.transaction {
        // A listener may have taken this conflict already, while the upload was in flight.
        val current = db.outboxQueries.selectById(entry.id).executeAsOneOrNull() ?: return@transaction
        recordConflict(current, remote)
        write(remote)
    }

    private fun recordConflict(entry: Outbox, remote: RemoteDoc) {
        db.conflictQueries.insert(
            entry.group_id,
            entry.entity_type,
            entry.entity_id,
            entry.payload,
            remote.version,
            writer.now(),
        )
        db.outboxQueries.deleteById(entry.id)
        writer.log(remote.groupId, EntityType.valueOf(remote.type.name), remote.id, ChangeAction.CONFLICT, emptyList())
    }

    private fun write(remote: RemoteDoc) {
        // What the server holds is synced by definition, whatever the adapter put in serverVersion.
        fun SyncMeta.synced() = copy(serverVersion = version, syncState = SyncState.SYNCED)
        when (remote) {
            is RemoteDoc.GroupDoc -> db.groupEntityQueries.upsert(
                remote.group.copy(meta = remote.group.meta.synced()).toRow()
            )
            is RemoteDoc.MemberDoc -> db.memberEntityQueries.upsert(
                remote.member.copy(meta = remote.member.meta.synced()).toRow()
            )
            is RemoteDoc.TransferDoc ->
                db.transferEntityQueries.upsert(remote.transfer.copy(meta = remote.transfer.meta.synced()).toRow())
            is RemoteDoc.ExpenseDoc -> {
                val q = db.expenseEntityQueries
                val e = remote.expense.copy(meta = remote.expense.meta.synced())
                q.upsert(e.toRow())
                q.deletePayers(e.id)
                e.payers.forEach { (member, amount) -> q.insertPayer(Expense_payer(e.id, member, amount)) }
                q.deleteSplits(e.id)
                e.splitValues.forEach { (member, value) -> q.insertSplit(Expense_split(e.id, member, value)) }
            }
            is RemoteDoc.ChangeLogDoc -> db.changeLogQueries.insert(remote.entry.toRow(SyncState.SYNCED))
        }
    }

    private fun serverVersion(type: OutboxType, id: String): Long? = when (type) {
        OutboxType.GROUP -> db.groupEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.MEMBER -> db.memberEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.EXPENSE -> db.expenseEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.TRANSFER -> db.transferEntityQueries.selectById(id).executeAsOneOrNull()?.server_version
        OutboxType.CHANGE_LOG -> db.changeLogQueries.selectById(id).executeAsOneOrNull()?.let { 1L }
    }
}
