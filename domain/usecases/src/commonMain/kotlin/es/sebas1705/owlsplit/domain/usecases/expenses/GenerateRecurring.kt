package es.sebas1705.owlsplit.domain.usecases.expenses

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import kotlinx.coroutines.flow.first

/**
 * Adds the expenses of every series that are due by today, as copies of the latest one (so a rent
 * that went up keeps its new amount). Runs on the device, offline included, when a group or the home
 * screen opens. Two safeguards keep it from adding the same expense twice:
 * - only one person generates each series: whoever created it, or (when they left) the member with
 *   the lowest uid;
 * - each generated expense gets a fixed id from its series and date, and existing ids are skipped.
 */
class GenerateRecurring(
    private val groups: GroupRepository,
    private val expenses: ExpenseRepository,
    private val accounts: AccountService,
    private val today: Today = Today.System,
) {
    /** Every group of mine. Returns how many expenses were added. */
    suspend fun all(): Int = groups.observeGroups().first().sumOf { invoke(it.id) }

    suspend operator fun invoke(groupId: String): Int {
        val uid = accounts.currentSession()?.uid ?: return 0
        val group = groups.observeGroup(groupId).first() ?: return 0
        if (uid !in group.memberUids || group.meta.deleted) return 0
        val all = expenses.observeExpenses(groupId, includeDeleted = true).first()
        val ids = all.mapTo(HashSet()) { it.id }
        var added = 0
        all.filter { it.recurrenceId != null }
            .groupBy { Recurrence.parse(it.recurrenceId)?.seriesId }
            .forEach { (seriesId, series) ->
                if (seriesId == null) return@forEach
                val generator = series.minBy { it.createdAt }.createdBy.takeIf { it in group.memberUids }
                    ?: group.memberUids.min()
                if (generator == uid) added += generate(groupId, seriesId, series, ids)
            }
        return added
    }

    private suspend fun generate(groupId: String, seriesId: String, series: List<Expense>, ids: MutableSet<String>): Int {
        val rules = series.mapNotNull { Recurrence.parse(it.recurrenceId) }
        // Stopped anywhere means stopped; a series whose expenses were all deleted is gone too.
        if (rules.any { it.stopped }) return 0
        val template = series.filter { !it.meta.deleted }.maxByOrNull { it.date } ?: return 0
        val rule = Recurrence.parse(template.recurrenceId) ?: return 0
        val anchor = series.minOf { it.date }
        val latest = series.maxOf { it.date }
        val until = today()
        val due = generateSequence(1) { it + 1 }
            .map { rule.dateAt(anchor, it) }
            .takeWhile { it <= until }
            .filter { it > latest }
            .map { date -> date to "${seriesId}_$date" }
            .filter { (_, id) -> id !in ids }
            .take(MAX_PER_RUN)
            .toList()
        due.forEach { (date, id) ->
            expenses.create(groupId, template.toInput(date = date), id)
            ids += id
        }
        return due.size
    }

    private companion object {
        /** A series left alone for years catches up a bit at a time. */
        const val MAX_PER_RUN = 24
    }
}
