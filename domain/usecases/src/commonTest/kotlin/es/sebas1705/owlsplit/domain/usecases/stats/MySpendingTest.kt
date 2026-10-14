package es.sebas1705.owlsplit.domain.usecases.stats

import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MySpendingTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val today = LocalDate(2026, 10, 14)

    private fun group(id: String, currency: String = "EUR") =
        Group(id, id, "users", "BLUE", currency, null, null, "code", listOf("me"), emptyList(), "me", 1, meta)

    private fun members(groupId: String) = listOf("me", "luis").mapIndexed { i, id ->
        Member("$groupId-$id", groupId, id, null, 1, if (id == "me") "me" else null, true, i.toLong(), meta)
    }

    @Suppress("LongParameterList") // Defaults keep each test case to what it changes.
    private fun expense(
        groupId: String,
        amount: Long,
        date: LocalDate,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        payer: String = "me",
        currency: String = "EUR",
        split: Map<String, Long> = mapOf("$groupId-me" to 1, "$groupId-luis" to 1),
    ) = Expense(
        "$groupId-$amount-$date", groupId, "x", category, null, date, currency, amount, 1_000_000_000,
        mapOf("$groupId-$payer" to amount), SplitType.EQUAL, split, null, null, "u", 1, meta,
    )

    private fun spending(group: Group, vararg expenses: Expense) =
        SpendingGroup(group, members(group.id), expenses.toList(), "${group.id}-me")

    @Test
    fun myPartAcrossGroupsByGroupCategoryAndMonth() {
        val trip = group("trip")
        val flat = group("flat")
        val source = SpendingSource(
            listOf(
                spending(
                    trip,
                    expense("trip", 4_000, LocalDate(2026, 9, 2)),
                    expense("trip", 1_000, LocalDate(2026, 10, 1), ExpenseCategory.TRANSPORT, payer = "luis"),
                ),
                spending(flat, expense("flat", 10_000, LocalDate(2026, 10, 5), ExpenseCategory.HOME)),
            ),
            "EUR",
            emptyMap(),
        )

        val all = MySpending.of(source, SpendingPeriod.ALL, today)

        assertEquals(7_500, all.shareMinor)
        assertEquals(14_000, all.paidMinor)
        assertEquals(3, all.expenseCount)
        assertEquals(listOf("flat" to 5_000L, "trip" to 2_500L), all.groups.map { it.group.id to it.amountMinor })
        assertEquals(
            listOf(ExpenseCategory.HOME, ExpenseCategory.FOOD, ExpenseCategory.TRANSPORT),
            all.categories.map { it.category },
        )
        assertEquals(listOf(MonthStats(2026, 9, 2_000), MonthStats(2026, 10, 5_500)), all.months)
        assertFalse(all.approximate)
    }

    @Test
    fun periodsCountOnlyTheirExpenses() {
        val source = SpendingSource(
            listOf(
                spending(
                    group("g"),
                    expense("g", 2_000, LocalDate(2025, 12, 31)),
                    expense("g", 4_000, LocalDate(2026, 3, 10)),
                    expense("g", 6_000, LocalDate(2026, 10, 2)),
                ),
            ),
            "EUR",
            emptyMap(),
        )

        assertEquals(3_000, MySpending.of(source, SpendingPeriod.MONTH, today).shareMinor)
        val year = MySpending.of(source, SpendingPeriod.YEAR, today)
        assertEquals(5_000, year.shareMinor)
        // From March, the first month with spending, to October; the months between included.
        assertEquals(8, year.months.size)
        assertEquals(6_000, MySpending.of(source, SpendingPeriod.ALL, today).shareMinor)
    }

    @Test
    fun otherCurrenciesAreConvertedAtTodaysRateOrKeptApart() {
        val source = SpendingSource(
            listOf(
                spending(group("usa", "USD"), expense("usa", 2_000, LocalDate(2026, 10, 1), currency = "USD")),
                spending(group("uk", "GBP"), expense("uk", 3_000, LocalDate(2026, 10, 1), currency = "GBP")),
            ),
            "EUR",
            // 1 USD = 0.9 EUR; no rate for GBP.
            mapOf("USD" to FxRate(900_000_000)),
        )

        val spending = MySpending.of(source, SpendingPeriod.ALL, today)

        assertEquals(900, spending.shareMinor)
        assertTrue(spending.approximate)
        assertEquals(mapOf("GBP" to 1_500L), spending.unconverted)
        assertEquals(listOf("usa"), spending.groups.map { it.group.id })
    }

    @Test
    fun groupsWithoutMeAndExpensesWithoutMeDoNotCount() {
        val g = group("g")
        val source = SpendingSource(
            listOf(
                spending(g, expense("g", 2_000, LocalDate(2026, 10, 1), payer = "luis", split = mapOf("g-luis" to 1))),
                SpendingGroup(group("other"), members("other"), listOf(expense("other", 5_000, today)), null),
            ),
            "EUR",
            emptyMap(),
        )

        val spending = MySpending.of(source, SpendingPeriod.ALL, today)

        assertEquals(0, spending.expenseCount)
        assertEquals(emptyList(), spending.groups)
        assertEquals(emptyList(), spending.months)
    }
}
