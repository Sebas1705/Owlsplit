package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.TransferInput
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Transfers are never edited: a wrong one is deleted and recreated. */
class TransferRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : TransferRepository {
    private val writer = Writer(db, ctx)

    override fun observeTransfers(groupId: String): Flow<List<Transfer>> = db.transferEntityQueries
        .selectVisibleByGroup(groupId)
        .asFlow()
        .mapToList(io)
        .map { rows -> rows.map { it.toModel() } }

    override suspend fun create(groupId: String, input: TransferInput): String = withContext(io) {
        db.transactionWithResult {
            val transfer = Transfer(
                id = writer.newId(), groupId = groupId, fromMemberId = input.fromMemberId,
                toMemberId = input.toMemberId, amountMinor = input.amountMinor, currency = input.currency,
                fxRateScaled = input.fxRateScaled, date = input.date, note = input.note, createdBy = writer.uid(),
                createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(transfer, old = null, action = ChangeAction.CREATED)
            transfer.id
        }
    }

    override suspend fun delete(id: String) = setDeleted(id, deleted = true, ChangeAction.DELETED)

    override suspend fun restore(id: String) = setDeleted(id, deleted = false, ChangeAction.RESTORED)

    private suspend fun setDeleted(id: String, deleted: Boolean, action: ChangeAction) = withContext(io) {
        db.transaction {
            val old = requireNotNull(db.transferEntityQueries.selectById(id).executeAsOneOrNull()) {
                "Unknown transfer $id"
            }.toModel()
            save(old.copy(meta = writer.editedMeta(old.meta, deleted = deleted)), old, action)
        }
    }

    private fun save(transfer: Transfer, old: Transfer?, action: ChangeAction) {
        db.transferEntityQueries.upsert(transfer.toRow())
        writer.enqueue(
            transfer.groupId,
            OutboxType.TRANSFER,
            transfer.id,
            old?.meta?.serverVersion ?: 0,
            json.encodeToString(Transfer.serializer(), transfer),
        )
        writer.log(transfer.groupId, EntityType.TRANSFER, transfer.id, action, emptyList())
    }
}
