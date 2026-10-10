package es.sebas1705.owlsplit.domain.usecases.expenses

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Session
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurringTest {
    private val ledger = InMemoryLedger()
    private var today = LocalDate(2026, 1, 31)
    private var series = 0
    private val save = SaveExpense(ledger.expenses, { today }, newSeriesId = { "s${++series}" })
    private val accounts = FakeAccounts(Session(ledger.uid, isAnonymous = true, providers = emptyList()))
    private val generate = GenerateRecurring(ledger.groups, ledger.expenses, accounts, { today })

    private suspend fun group(): Pair<String, List<es.sebas1705.owlsplit.data.models.Member>> {
        val id = ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")
        return id to ledger.members.observeMembers(id).first()
    }

    private fun rent(me: String, repeat: RepeatUnit? = RepeatUnit.MONTH) = ExpenseDraft(
        amountMinor = 80_000,
        title = "Alquiler",
        category = ExpenseCategory.HOME,
        payers = mapOf(me to 80_000L),
        values = mapOf(me to 1L),
        date = today,
        currency = "EUR",
        repeat = repeat,
    )

    private suspend fun dates(groupId: String) =
        ledger.expenses.observeExpenses(groupId).first().map { it.date }.sorted()

    @Test
    fun rulesRoundTripAndMonthsKeepTheirDay() {
        val rule = Recurrence("abc", RepeatUnit.MONTH)
        assertEquals("abc|M1", rule.encode())
        assertEquals(rule, Recurrence.parse("abc|M1"))
        assertTrue(Recurrence.parse("abc|stop")!!.stopped)
        assertNull(Recurrence.parse("nothing"))
        assertNull(Recurrence.parse("abc|Q1"))
        val anchor = LocalDate(2026, 1, 31)
        assertEquals(LocalDate(2026, 2, 28), rule.dateAt(anchor, 1))
        assertEquals(LocalDate(2026, 3, 31), rule.dateAt(anchor, 2))
        assertEquals(LocalDate(2026, 2, 14), Recurrence("w", RepeatUnit.WEEK, every = 2).dateAt(anchor, 1))
    }

    @Test
    fun dueExpensesAreAddedOnceAndCopyTheLatest() = runTest {
        val (g, members) = group()
        val me = members.single().id
        save(g, null, rent(me), "EUR", members)

        today = LocalDate(2026, 4, 2)
        assertEquals(2, generate(g))
        assertEquals(0, generate(g))
        assertEquals(
            listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2026, 3, 31)),
            dates(g),
        )
        val all = ledger.expenses.observeExpenses(g).first()
        assertEquals("s1_2026-03-31", all.maxBy { it.date }.id)
        assertTrue(all.all { it.recurrenceId == "s1|M1" && it.amountMinor == 80_000L })
    }

    @Test
    fun turningRepetitionOffStopsTheSeries() = runTest {
        val (g, members) = group()
        val me = members.single().id
        save(g, null, rent(me), "EUR", members)
        today = LocalDate(2026, 2, 28)
        generate(g)
        val february = ledger.expenses.observeExpenses(g).first().maxBy { it.date }

        save(g, february.id, ExpenseDrafts.of(february).copy(repeat = null), "EUR", members)
        today = LocalDate(2026, 6, 1)
        assertEquals(0, generate(g))
        assertEquals(2, dates(g).size)
    }

    @Test
    fun anotherPeriodStartsANewSeriesAndStopsTheOld() = runTest {
        val (g, members) = group()
        val me = members.single().id
        save(g, null, rent(me), "EUR", members)
        today = LocalDate(2026, 2, 28)
        generate(g)
        val february = ledger.expenses.observeExpenses(g).first().maxBy { it.date }

        save(g, february.id, ExpenseDrafts.of(february).copy(repeat = RepeatUnit.YEAR), "EUR", members)
        today = LocalDate(2027, 3, 1)
        generate(g)
        // The monthly series stopped; the yearly one goes on from February.
        assertEquals(listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2027, 2, 28)), dates(g))
    }

    @Test
    fun onlyTheCreatorGenerates() = runTest {
        val (g, members) = group()
        save(g, null, rent(members.single().id), "EUR", members)
        today = LocalDate(2026, 3, 31)
        val other = GenerateRecurring(
            ledger.groups,
            ledger.expenses,
            FakeAccounts(Session("uid-luis", true, emptyList())),
            { today },
        )
        assertEquals(0, other(g))
    }
}
