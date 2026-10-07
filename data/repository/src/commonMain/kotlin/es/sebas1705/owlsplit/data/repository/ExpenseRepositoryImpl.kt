package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.local.db.Expense_entity
import es.sebas1705.owlsplit.data.local.db.Expense_payer
import es.sebas1705.owlsplit.data.local.db.Expense_split
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : ExpenseRepository {
    private val writer = Writer(db, ctx)
    private val q get() = db.expenseEntityQueries

    // Every write rewrites the expense row, so observing expense_entity is enough to
    // re-read the child tables when payers or splits change.
    override fun observeExpenses(groupId: String): Flow<List<Expense>> =
        q.selectVisibleByGroup(groupId).asFlow().mapToList(io).map { rows ->
            val payers = q.payersByGroup(groupId).executeAsList()
                .groupBy({ it.expense_id }, { it.member_id to it.amount_minor })
            val splits = q.splitsByGroup(groupId).executeAsList()
                .groupBy({ it.expense_id }, { it.member_id to it.split_value })
            rows.map { it.toModel(payers[it.id].orEmpty().toMap(), splits[it.id].orEmpty().toMap()) }
        }.flowOn(io)

    override suspend fun get(id: String): Expense? = withContext(io) { load(id) }

    override suspend fun create(groupId: String, input: ExpenseInput): String = withContext(io) {
        db.transactionWithResult {
            val expense = Expense(
                id = writer.newId(), groupId = groupId, title = input.title, category = input.category,
                note = input.note, date = input.date, currency = input.currency, amountMinor = input.amountMinor,
                fxRateScaled = input.fxRateScaled, payers = input.payers, splitType = input.splitType,
                splitValues = input.splitValues, receiptUrl = null, recurrenceId = null, createdBy = writer.uid(),
                createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(expense, old = null, action = ChangeAction.CREATED)
            expense.id
        }
    }

    override suspend fun update(id: String, input: ExpenseInput) = edit(id, ChangeAction.UPDATED) { old ->
        old.copy(
            title = input.title, category = input.category, note = input.note, date = input.date,
            currency = input.currency, amountMinor = input.amountMinor, fxRateScaled = input.fxRateScaled,
            payers = input.payers, splitType = input.splitType, splitValues = input.splitValues,
            meta = writer.editedMeta(old.meta),
        )
    }

    override suspend fun delete(id: String) = edit(id, ChangeAction.DELETED) { old ->
        old.copy(meta = writer.editedMeta(old.meta, deleted = true))
    }

    override suspend fun restore(id: String) = edit(id, ChangeAction.RESTORED) { old ->
        old.copy(meta = writer.editedMeta(old.meta, deleted = false))
    }

    private suspend fun edit(id: String, action: ChangeAction, change: (Expense) -> Expense) = withContext(io) {
        db.transaction {
            val old = requireNotNull(load(id)) { "Unknown expense $id" }
            save(change(old), old, action)
        }
    }

    private fun load(id: String): Expense? = q.selectById(id).executeAsOneOrNull()?.let(::withChildren)

    private fun withChildren(row: Expense_entity): Expense = row.toModel(
        payers = q.payersByExpense(row.id).executeAsList().associate { it.member_id to it.amount_minor },
        splitValues = q.splitsByExpense(row.id).executeAsList().associate { it.member_id to it.split_value },
    )

    private fun save(expense: Expense, old: Expense?, action: ChangeAction) {
        q.upsert(expense.toRow())
        q.deletePayers(expense.id)
        expense.payers.forEach { (member, amount) -> q.insertPayer(Expense_payer(expense.id, member, amount)) }
        q.deleteSplits(expense.id)
        expense.splitValues.forEach { (member, value) -> q.insertSplit(Expense_split(expense.id, member, value)) }
        writer.enqueue(
            expense.groupId,
            OutboxType.EXPENSE,
            expense.id,
            old?.meta?.serverVersion ?: 0,
            json.encodeToString(Expense.serializer(), expense),
        )
        val diff = if (action == ChangeAction.UPDATED) Diffs.expense(old, expense) else emptyList()
        writer.log(expense.groupId, EntityType.EXPENSE, expense.id, action, diff)
    }
}
