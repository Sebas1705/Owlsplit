package es.sebas1705.owlsplit.domain.usecases.stats

import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.usecases.groups.hasMember
import es.sebas1705.owlsplit.domain.usecases.groups.mine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.datetime.LocalDate

/** The stretch of time the spending summary covers, up to today. */
enum class SpendingPeriod { MONTH, YEAR, ALL }

/** One of my groups with what the summary needs; [myMemberId] null when I have no member there. */
data class SpendingGroup(
    val group: Group,
    val members: List<Member>,
    val expenses: List<Expense>,
    val myMemberId: String?,
)

/** My groups, my currency and today's rate from each other group currency into it (missing: no rate). */
data class SpendingSource(
    val groups: List<SpendingGroup>,
    val currency: String,
    val rates: Map<String, FxRate>,
)

data class GroupSpending(val group: Group, val amountMinor: Long)

/**
 * What I spent across all my groups, in my currency: my part of each expense (what I consumed), not
 * what I paid for others. Groups in another currency are converted at today's rate ([approximate]);
 * those without a rate are left out of every figure and kept apart in [unconverted].
 */
data class MySpending(
    val currency: String,
    val shareMinor: Long,
    val paidMinor: Long,
    /** Expenses in which I had a part or paid something. */
    val expenseCount: Int,
    /** Largest first; only groups where I spent something. */
    val groups: List<GroupSpending>,
    /** Largest first; only categories with spending. */
    val categories: List<CategoryStats>,
    /** In time order, every month from my first expense in the period to its end (today, or my last one). */
    val months: List<MonthStats>,
    val approximate: Boolean,
    /** My part in group currencies without a rate, per currency. */
    val unconverted: Map<String, Long>,
) {
    companion object {
        @Suppress("CyclomaticComplexMethod", "LongMethod") // One pass that fills every breakdown.
        fun of(source: SpendingSource, period: SpendingPeriod, today: LocalDate): MySpending {
            val target = Currency.of(source.currency)
            var share = 0L
            var paid = 0L
            var count = 0
            var approximate = false
            val byGroup = mutableListOf<GroupSpending>()
            val byCategory = HashMap<ExpenseCategory, Long>()
            val byMonth = HashMap<Pair<Int, Int>, Long>()
            val dates = mutableListOf<LocalDate>()
            val unconverted = HashMap<String, Long>()
            for (item in source.groups) {
                val me = item.myMemberId ?: continue
                val base = Currency.of(item.group.baseCurrency)
                val rate = source.rates[base.code]
                val convert: (Long) -> Long? = when {
                    base.code == target.code -> { minor -> minor }
                    rate == null -> { _ -> null }
                    else -> { minor -> Conversion.convert(minor, base, target, rate).truncated }
                }
                val ledger = item.members.map { LedgerMember(it.id, it.sortKey) }
                var groupShare = 0L
                val involved = item.expenses.filter { period.covers(it.date, today) }.mapNotNull { expense ->
                    // Inconsistent entries are left out, as in the balances.
                    val myShareInBase = expense.sharesInBase(ledger, base)?.get(me) ?: return@mapNotNull null
                    val myPaidInBase = expense.payers[me]?.let { expense.inBase(it, base) } ?: 0L
                    Triple(expense, myShareInBase, myPaidInBase).takeIf { myShareInBase != 0L || myPaidInBase != 0L }
                }
                for ((expense, myShareInBase, myPaidInBase) in involved) {
                    val myShare = convert(myShareInBase)
                    if (myShare == null) {
                        unconverted[base.code] = (unconverted[base.code] ?: 0L) + myShareInBase
                        continue
                    }
                    if (base.code != target.code) approximate = true
                    share += myShare
                    paid += convert(myPaidInBase) ?: 0L
                    count++
                    groupShare += myShare
                    dates += expense.date
                    if (myShare > 0) {
                        byCategory[expense.category] = (byCategory[expense.category] ?: 0L) + myShare
                        val month = expense.date.year to expense.date.month.ordinal + 1
                        byMonth[month] = (byMonth[month] ?: 0L) + myShare
                    }
                }
                if (groupShare > 0) byGroup += GroupSpending(item.group, groupShare)
            }
            // From my first expense in the period, not from its start: no row of empty months ahead.
            val first = dates.minOrNull()
            val last = if (period == SpendingPeriod.ALL) dates.maxOrNull() else today
            return MySpending(
                currency = source.currency,
                shareMinor = share,
                paidMinor = paid,
                expenseCount = count,
                groups = byGroup.sortedByDescending { it.amountMinor },
                categories = byCategory.entries.filter { it.value > 0 }
                    .sortedWith(compareByDescending<Map.Entry<ExpenseCategory, Long>> { it.value }.thenBy { it.key })
                    .map { CategoryStats(it.key, it.value) },
                months = if (count == 0) {
                    emptyList()
                } else {
                    monthsBetween(first, last).map { (y, m) -> MonthStats(y, m, byMonth[y to m] ?: 0L) }
                },
                approximate = approximate,
                unconverted = unconverted.filterValues { it != 0L },
            )
        }
    }
}

/** The first day of the period that ends [today]; null for [SpendingPeriod.ALL]. */
fun SpendingPeriod.start(today: LocalDate): LocalDate? = when (this) {
    SpendingPeriod.MONTH -> LocalDate(today.year, today.month, 1)
    SpendingPeriod.YEAR -> LocalDate(today.year, 1, 1)
    SpendingPeriod.ALL -> null
}

private fun SpendingPeriod.covers(date: LocalDate, today: LocalDate): Boolean {
    val start = start(today) ?: return true
    return date in start..today
}

/**
 * My groups (archived ones too: they are still spending) with their members and expenses, my preferred
 * currency, and today's rates into it. Rates come from the cache, or the network once per pair.
 */
class ObserveMySpending(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
    private val rates: FxRates,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<SpendingSource> =
        combine(groups.observeGroups(), accounts.session, profiles.profile) { all, session, profile ->
            Triple(all.filter { it.hasMember(session?.uid) }, session?.uid, profile?.currency ?: DEFAULT_CURRENCY)
        }.flatMapLatest { (mine, uid, currency) ->
            if (mine.isEmpty()) return@flatMapLatest flowOf(emptyList<SpendingGroup>() to currency)
            combine(
                mine.map { group ->
                    combine(members.observeMembers(group.id), expenses.observeExpenses(group.id)) { memberList, list ->
                        SpendingGroup(group, memberList, list, memberList.mine(uid)?.id)
                    }
                },
            ) { it.toList() to currency }
        }.mapLatest { (items, currency) ->
            val foreign = items.map { it.group.baseCurrency }.filter { it != currency }.toSet()
            SpendingSource(
                groups = items,
                currency = currency,
                rates = foreign.mapNotNull { code -> rates.latest(code, currency)?.let { code to it.rate } }.toMap(),
            )
        }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}
