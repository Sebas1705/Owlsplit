package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.ledger.SplitIssue
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.expenses.DraftProblem
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupUseCasesTest {
    private val ledger = InMemoryLedger()
    private val accounts = FakeAccounts()
    private val profiles = FakeProfiles(UserProfile("Ana", "accent:BLUE", "EUR"))
    private val balances = ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers)
    private val actions = GroupActions(ledger.groups, profiles, ledger.members)
    private val memberActions = MemberActions(ledger.members)
    private val today = Today { LocalDate(2026, 10, 8) }
    private val save = SaveExpense(ledger.expenses, today)
    private val home = ObserveHome(
        ledger.groups,
        ledger.members,
        ledger.expenses,
        ledger.transfers,
        balances,
        accounts,
        profiles,
    )
    private val detail = ObserveGroupDetail(
        ledger.groups,
        ledger.members,
        ledger.expenses,
        ledger.transfers,
        balances,
        accounts,
    )

    private suspend fun trip(currency: String = "EUR") =
        actions.create(GroupInput("  Ibiza 2026 ", "plane", "BLUE", currency))

    /** Ana pays 30,00 € for her and Luis, equally. */
    private suspend fun dinner(groupId: String): Pair<String, String> {
        val luis = memberActions.add(groupId, "Luis")
        val members = ledger.members.observeMembers(groupId).first()
        val ana = members.mine("uid-ana")!!.id
        val draft = ExpenseDrafts.new(members, ana).copy(amountMinor = 3_000, title = "Cena")
        assertTrue(save(groupId, null, draft, "EUR", members))
        return ana to luis
    }

    @Test
    fun theCreatorJoinsWithTheProfileNameAndTheNameIsTrimmed() = runTest {
        val g = trip()
        val group = ledger.groups.observeGroup(g).first()!!
        assertEquals("Ibiza 2026", group.name)
        assertEquals("Ana", ledger.members.observeMembers(g).first().single().name)
    }

    @Test
    fun homeShowsMyBalanceAndTotalsByCurrency() = runTest {
        val eur = trip()
        dinner(eur)
        val usd = trip("USD")
        dinner(usd)

        val data = home().first()
        assertEquals(2, data.groups.size)
        assertEquals(usd, data.groups.first().group.id) // most recent activity first
        assertEquals(1_500, data.total)
        assertEquals(mapOf("USD" to 1_500L), data.otherCurrencies)
        assertEquals(2, data.groups.first().activeMembers)
    }

    @Test
    fun archivedGroupsLeaveHome() = runTest {
        val g = trip()
        actions.setArchived(g, true)
        assertTrue(home().first().groups.isEmpty())
    }

    @Test
    fun groupDetailKnowsMyMemberAndSettlingClosesTheDebt() = runTest {
        val g = trip()
        val (ana, luis) = dinner(g)
        val before = detail(g).first()!!
        assertEquals(ana, before.myMemberId)
        val payment = before.balances!!.suggestedPayments.single()
        assertEquals(luis, payment.fromMemberId)
        assertEquals(1_500, payment.amountMinor)

        SettleUp(ledger.transfers, today)(g, payment, "EUR")

        val after = detail(g).first()!!
        assertTrue(after.balances!!.balances.values.all { it == 0L })
        assertEquals(LocalDate(2026, 10, 8), after.transfers.single().date)
    }

    @Test
    fun archivedGroupsAreApartAndGroupsILeftAreGone() = runTest {
        val kept = actions.create(GroupInput("Piso", "home", "GREEN", "EUR"))
        val archived = actions.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"))
        val left = actions.create(GroupInput("Cena", "food", "PINK", "EUR"))
        actions.setArchived(archived, true)
        actions.leave(left)

        val data = home().first()
        assertEquals(listOf(kept), data.groups.map { it.group.id })
        assertEquals(listOf(archived), data.archived.map { it.id })
    }

    @Test
    fun iCannotDeactivateOrDeleteMyself() = runTest {
        val g = trip()
        val me = ledger.members.observeMembers(g).first().single()
        assertFailsWith<IllegalArgumentException> { memberActions.setActive(me, false, "uid-ana") }
        assertFailsWith<IllegalArgumentException> { memberActions.delete(me, "uid-ana") }
    }

    @Test
    fun aMemberWithMovementsIsDeactivatedNotDeleted() = runTest {
        val g = trip()
        val (_, luis) = dinner(g)
        val member = ledger.members.observeMembers(g).first().first { it.id == luis }
        assertFalse(memberActions.delete(member, "uid-ana"))
        memberActions.setActive(member, false, "uid-ana")
        assertFalse(ledger.members.observeMembers(g).first().first { it.id == luis }.active)
    }

    @Test
    fun draftsValidateEveryKindOfSplit() = runTest {
        val g = trip()
        val (ana, luis) = dinner(g)
        val members = ledger.members.observeMembers(g).first()
        val base = ExpenseDrafts.new(members, ana).copy(amountMinor = 1_000, title = "Taxi")

        assertEquals(mapOf(ana to 500L, luis to 500L), ExpenseDrafts.check(base, members).shares)

        val exact = ExpenseDrafts.withType(
            base,
            SplitType.EXACT,
            members
        ).copy(values = mapOf(ana to 700L, luis to 100L))
        assertEquals(DraftProblem.Split(SplitIssue.ExactSumMismatch(200)), ExpenseDrafts.check(exact, members).problem)

        val percent = ExpenseDrafts.withType(base, SplitType.PERCENT, members)
        assertEquals(mapOf(ana to 5_000L, luis to 5_000L), percent.values)
        assertTrue(ExpenseDrafts.check(percent, members).canSave)

        val weights = ExpenseDrafts.withType(
            base,
            SplitType.WEIGHTS,
            members
        ).copy(values = mapOf(ana to 2L, luis to 1L))
        assertEquals(mapOf(ana to 667L, luis to 333L), ExpenseDrafts.check(weights, members).shares)

        val onlyLuis = ExpenseDrafts.toggle(base, members.first { it.id == ana }, members)
        assertEquals(mapOf(luis to 1_000L), ExpenseDrafts.check(onlyLuis, members).shares)

        assertEquals(DraftProblem.MissingTitle, ExpenseDrafts.check(base.copy(title = " "), members).problem)
        assertEquals(DraftProblem.MissingAmount, ExpenseDrafts.check(base.copy(amountMinor = 0), members).problem)
        assertFalse(save(g, null, exact, "EUR", members))
    }

    @Test
    fun editingKeepsTheOriginalDate() = runTest {
        val g = trip()
        dinner(g)
        val expense = ledger.expenseList.value.single()
        val members = ledger.members.observeMembers(g).first()
        val draft = ExpenseDrafts.of(expense.copy(date = LocalDate(2026, 7, 12))).copy(category = ExpenseCategory.FOOD)
        assertTrue(save(g, expense.id, draft, "EUR", members))
        assertEquals(LocalDate(2026, 7, 12), ledger.expenseList.value.single().date)
        assertNull(ledger.expenseList.value.single().note)
    }

    @Test
    fun aManualTransferNeedsTwoMembersAndAnAmount() = runTest {
        val g = trip()
        val (ana, luis) = dinner(g)
        val record = RecordTransfer(ledger.transfers, today)
        assertFalse(record(g, ana, ana, 500, "EUR", null))
        assertFalse(record(g, luis, ana, 0, "EUR", null))
        assertTrue(record(g, luis, ana, 1_500, "EUR", "  Bizum  "))
        val transfer = ledger.transferList.value.single()
        assertEquals("Bizum", transfer.note)
        assertTrue(detail(g).first()!!.balances!!.balances.values.all { it == 0L })
    }
}
