package es.sebas1705.owlsplit.domain.usecases.stats

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupStatsTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val members = listOf("ana", "luis").mapIndexed { i, id ->
        Member(id, "g", id, null, 1, null, true, i.toLong(), meta)
    }

    @Suppress("LongParameterList") // Defaults keep each test case to what it changes.
    private fun expense(
        id: String,
        amount: Long,
        date: LocalDate,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        payer: String = "ana",
        currency: String = "EUR",
        rate: Long = 1_000_000_000,
        split: Map<String, Long> = mapOf("ana" to 1, "luis" to 1),
    ) = Expense(
        id, "g", id, category, null, date, currency, amount, rate, mapOf(payer to amount), SplitType.EQUAL, split,
        null, null, "u", 1, meta,
    )

    @Test
    fun totalsPeopleCategoriesAndMonthsInTheBaseCurrency() {
        val stats = GroupStats.of(
            "EUR",
            members,
            listOf(
                expense("cena", 4_000, LocalDate(2026, 7, 30)),
                expense("taxi", 1_000, LocalDate(2026, 8, 1), ExpenseCategory.TRANSPORT, payer = "luis"),
                // 10 USD at 0.9 EUR each.
                expense(
                    "museo",
                    1_000,
                    LocalDate(2026, 9, 2),
                    ExpenseCategory.LEISURE,
                    currency = "USD",
                    rate = 900_000_000
                ),
            ),
        )
        assertEquals(5_900, stats.totalMinor)
        assertEquals(3, stats.expenseCount)
        assertEquals(35, stats.days)
        assertEquals(168, stats.perDayMinor)
        assertEquals(listOf(MemberStats("ana", 4_900, 2_950), MemberStats("luis", 1_000, 2_950)), stats.members)
        assertEquals(
            listOf(ExpenseCategory.FOOD, ExpenseCategory.TRANSPORT, ExpenseCategory.LEISURE),
            stats.categories.map { it.category },
        )
        assertEquals(listOf(4_000L, 1_000L, 900L), stats.months.map { it.amountMinor })
        assertEquals(listOf(7, 8, 9), stats.months.map { it.month })
    }

    @Test
    fun tripDatesSetTheDaysAndBrokenEntriesAreSkipped() {
        val stats = GroupStats.of(
            "EUR",
            members,
            listOf(
                expense("ok", 1_000, LocalDate(2026, 7, 1)),
                expense("nadie", 1_000, LocalDate(2026, 7, 1), split = emptyMap()),
            ),
            startDate = LocalDate(2026, 7, 1),
            endDate = LocalDate(2026, 7, 10),
        )
        assertEquals(1_000, stats.totalMinor)
        assertEquals(1, stats.expenseCount)
        assertEquals(10, stats.days)
        assertEquals(100, stats.perDayMinor)
    }

    @Test
    fun anEmptyGroupHasNothingToShow() {
        val stats = GroupStats.of("EUR", members, emptyList())
        assertEquals(0, stats.totalMinor)
        assertEquals(1, stats.days)
        assertEquals(emptyList(), stats.months)
        assertEquals(emptyList(), stats.categories)
    }
}
