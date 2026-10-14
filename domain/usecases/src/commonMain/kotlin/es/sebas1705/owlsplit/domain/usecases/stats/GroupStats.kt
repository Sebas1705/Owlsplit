package es.sebas1705.owlsplit.domain.usecases.stats

import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.ledger.ShareCalculator
import es.sebas1705.owlsplit.domain.ledger.SplitResult
import es.sebas1705.owlsplit.domain.ledger.SplitSpec
import es.sebas1705.owlsplit.domain.ledger.SplitType
import kotlinx.datetime.LocalDate

/** What one person put in and used, in base-currency minor units. */
data class MemberStats(val memberId: String, val paidMinor: Long, val shareMinor: Long)

data class CategoryStats(val category: ExpenseCategory, val amountMinor: Long)

/** [month] is 1..12. */
data class MonthStats(val year: Int, val month: Int, val amountMinor: Long)

/**
 * A summary of a group's spending, in its base currency. Transfers are not spending: they only
 * move balances. Expenses in other currencies count at the rate frozen on each one (truncated to
 * the minor unit, so totals can be a cent away from the balances, which share the remainder out).
 */
data class GroupStats(
    val baseCurrency: String,
    val totalMinor: Long,
    val expenseCount: Int,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    /** Days from the first to the last expense (or the trip dates, when set), at least 1. */
    val days: Int,
    val members: List<MemberStats>,
    /** Largest first; only categories with spending. */
    val categories: List<CategoryStats>,
    /** In time order, every month between the first and the last expense. */
    val months: List<MonthStats>,
) {
    val perDayMinor: Long get() = if (days == 0) 0 else totalMinor / days

    companion object {
        @Suppress("CyclomaticComplexMethod") // One pass that fills every breakdown.
        fun of(
            baseCurrency: String,
            members: List<Member>,
            expenses: List<Expense>,
            startDate: LocalDate? = null,
            endDate: LocalDate? = null,
        ): GroupStats {
            val base = Currency.of(baseCurrency)
            val ledger = members.map { LedgerMember(it.id, it.sortKey) }
            val paid = members.associateTo(LinkedHashMap()) { it.id to 0L }
            val shares = members.associateTo(LinkedHashMap()) { it.id to 0L }
            val byCategory = HashMap<ExpenseCategory, Long>()
            val byMonth = HashMap<Pair<Int, Int>, Long>()
            var total = 0L
            var counted = 0
            for (expense in expenses) {
                // Inconsistent entries are left out, as in the balances.
                val expenseShares = expense.sharesInBase(ledger, base) ?: continue
                val amount = expense.inBase(expense.amountMinor, base)
                total += amount
                counted++
                expense.payers.forEach { (id, minor) -> paid[id] = (paid[id] ?: 0L) + expense.inBase(minor, base) }
                expenseShares.forEach { (id, minor) -> shares[id] = (shares[id] ?: 0L) + minor }
                byCategory[expense.category] = (byCategory[expense.category] ?: 0L) + amount
                val month = expense.date.year to expense.date.month.ordinal + 1
                byMonth[month] = (byMonth[month] ?: 0L) + amount
            }
            val dates = expenses.map { it.date }
            val first = startDate ?: dates.minOrNull()
            val last = endDate ?: dates.maxOrNull()
            return GroupStats(
                baseCurrency = baseCurrency,
                totalMinor = total,
                expenseCount = counted,
                firstDate = dates.minOrNull(),
                lastDate = dates.maxOrNull(),
                days = if (first == null || last == null || last < first) 1 else first.daysUntil(last) + 1,
                members = members.map { MemberStats(it.id, paid.getValue(it.id), shares.getValue(it.id)) },
                categories = byCategory.entries.filter { it.value > 0 }
                    .sortedWith(compareByDescending<Map.Entry<ExpenseCategory, Long>> { it.value }.thenBy { it.key })
                    .map { CategoryStats(it.key, it.value) },
                months = monthsBetween(dates.minOrNull(), dates.maxOrNull()).map { (y, m) ->
                    MonthStats(y, m, byMonth[y to m] ?: 0L)
                },
            )
        }

        private fun LocalDate.daysUntil(other: LocalDate): Int = (other.toEpochDays() - toEpochDays()).toInt()
    }
}

/** [minor] units of this expense's currency in [base], at its frozen rate (truncated). */
fun Expense.inBase(minor: Long, base: Currency): Long =
    if (currency == base.code) {
        minor
    } else {
        Conversion.convert(minor, Currency.of(currency), base, FxRate(fxRateScaled)).truncated
    }

/** Each participant's part of this expense in [base]; null when the split is inconsistent. */
fun Expense.sharesInBase(members: List<LedgerMember>, base: Currency): Map<String, Long>? {
    val split = SplitSpec(SplitType.valueOf(splitType.name), splitValues)
    val result = ShareCalculator.shares(amountMinor, split, members) as? SplitResult.Ok ?: return null
    return result.shares.mapValues { inBase(it.value, base) }
}

/** Every (year, month 1..12) from the month of [first] to that of [last], in order; none without dates. */
internal fun monthsBetween(first: LocalDate?, last: LocalDate?): List<Pair<Int, Int>> {
    if (first == null || last == null) return emptyList()
    val months = mutableListOf<Pair<Int, Int>>()
    var year = first.year
    var month = first.month.ordinal + 1
    val end = last.year * MONTHS + last.month.ordinal
    while (year * MONTHS + month - 1 <= end) {
        months += year to month
        if (month == MONTHS) {
            year++
            month = 1
        } else {
            month++
        }
    }
    return months
}

private const val MONTHS = 12
