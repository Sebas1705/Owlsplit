package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.Writer
import es.sebas1705.owlsplit.data.repository.toModel
import es.sebas1705.owlsplit.data.repository.toRow

/** Local side of synchronisation. Every public operation runs in its own transaction. */
internal class SyncLocalStore(private val db: AppDatabase, private val writer: Writer) {

    fun now(): Long = writer.now()

    fun ready(now: Long): List<Outbox> = db.outboxQueries.selectReady(now).executeAsList()

    /** -1 when the outbox is empty. */
    fun nextAttemptAt(): Long = db.outboxQueries.nextAttemptAt().executeAsOne()

    fun scheduleRetry(entry: Outbox, at: Long, error: String) = db.outboxQueries.scheduleRetry(at, error, entry.id)

    fun isAccessLost(groupId: String): Boolean = db.metaQueries.get(accessKey(groupId)).executeAsOneOrNull() == "1"

    fun markAccessLost(groupId: String) = db.metaQueries.put(accessKey(groupId), "1")

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

    /** The server kept someone else's version: store ours as a conflict and adopt theirs. */
    fun resolveConflict(entry: Outbox, remote: RemoteDoc) = db.transaction {
        recordConflict(entry, remote)
        write(remote)
    }

    /** Applies a remote document following docs/SYNC.md ("Bajada"). Returns true if local data changed. */
    fun apply(remote: RemoteDoc): Boolean = db.transactionWithResult {
        if (remote is RemoteDoc.ChangeLogDoc) {
            db.changeLogQueries.insert(remote.entry.toRow(SyncState.SYNCED))
            return@transactionWithResult true
        }
        val known = serverVersion(remote.type, remote.id)
        val pending = db.outboxQueries.selectByEntity(remote.type.name, remote.id).executeAsOneOrNull()
        when {
            known != null && remote.version < known -> false
            known != null && remote.version == known && pending != null -> false
            else -> {
                if (pending != null) recordConflict(pending, remote)
                write(remote)
                true
            }
        }
    }

    private fun recordConflict(entry: Outbox, remote: RemoteDoc) {
        db.conflictQueries.insert(entry.group_id, entry.entity_type, entry.entity_id, entry.payload, remote.version, now())
        db.outboxQueries.deleteById(entry.id)
        writer.log(remote.groupId, EntityType.valueOf(remote.type.name), remote.id, ChangeAction.CONFLICT, emptyList())
    }

    private fun write(remote: RemoteDoc) {
        when (remote) {
            is RemoteDoc.GroupDoc -> db.groupEntityQueries.upsert(remote.group.toRow())
            is RemoteDoc.MemberDoc -> db.memberEntityQueries.upsert(remote.member.toRow())
            is RemoteDoc.TransferDoc -> db.transferEntityQueries.upsert(remote.transfer.toRow())
            is RemoteDoc.ExpenseDoc -> {
                val q = db.expenseEntityQueries
                val e = remote.expense
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

    private fun accessKey(groupId: String) = "accessLost:$groupId"
}
