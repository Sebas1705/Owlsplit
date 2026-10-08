package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.Conflict
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Reapplying writes the lost version as a new edit through the normal repositories, so it is
 * queued, versioned and logged like any other change (and can conflict again).
 */
class ConflictRepositoryImpl(
    private val db: AppDatabase,
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
    private val io: CoroutineDispatcher,
) : ConflictRepository {

    override fun observe(): Flow<List<Conflict>> =
        db.conflictQueries.selectAll().asFlow().mapToList(io).map { rows ->
            rows.map { Conflict(it.id, it.group_id, EntityType.valueOf(it.entity_type), it.entity_id, it.created_at) }
        }

    override suspend fun reapply(conflictId: Long): Boolean {
        val row = withContext(io) { db.conflictQueries.selectById(conflictId).executeAsOneOrNull() } ?: return false
        val applied = when (EntityType.valueOf(row.entity_type)) {
            EntityType.EXPENSE -> reapplyExpense(json.decodeFromString(Expense.serializer(), row.local_payload))
            EntityType.TRANSFER -> reapplyTransfer(json.decodeFromString(Transfer.serializer(), row.local_payload))
            EntityType.GROUP -> reapplyGroup(json.decodeFromString(Group.serializer(), row.local_payload))
            EntityType.MEMBER -> reapplyMember(json.decodeFromString(Member.serializer(), row.local_payload))
        }
        if (applied) dismiss(conflictId)
        return applied
    }

    override suspend fun dismiss(conflictId: Long) {
        withContext(io) { db.conflictQueries.deleteById(conflictId) }
    }

    private suspend fun reapplyExpense(mine: Expense): Boolean {
        val current = expenses.get(mine.id) ?: return false
        if (mine.meta.deleted) {
            if (!current.meta.deleted) expenses.delete(mine.id)
            return true
        }
        if (current.meta.deleted) expenses.restore(mine.id)
        expenses.update(
            mine.id,
            ExpenseInput(
                mine.title, mine.category, mine.note, mine.date, mine.currency, mine.amountMinor, mine.fxRateScaled,
                mine.payers, mine.splitType, mine.splitValues,
            ),
        )
        return true
    }

    /** Transfers only change by being deleted or restored. */
    private suspend fun reapplyTransfer(mine: Transfer): Boolean {
        if (mine.meta.deleted) transfers.delete(mine.id) else transfers.restore(mine.id)
        return true
    }

    private suspend fun reapplyGroup(mine: Group): Boolean {
        groups.update(
            mine.id,
            GroupInput(mine.name, mine.icon, mine.accent, mine.baseCurrency, mine.startDate, mine.endDate)
        )
        return true
    }

    private suspend fun reapplyMember(mine: Member): Boolean {
        members.update(mine.id, MemberInput(mine.name, mine.avatar, mine.defaultWeight))
        members.setActive(mine.id, mine.active)
        return true
    }
}
