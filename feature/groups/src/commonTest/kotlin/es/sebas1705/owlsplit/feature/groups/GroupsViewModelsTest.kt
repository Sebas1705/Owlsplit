package es.sebas1705.owlsplit.feature.groups

import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.groups.DeleteTransfer
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.groups.MemberActions
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.RecordTransfer
import es.sebas1705.owlsplit.domain.usecases.groups.SettleUp
import es.sebas1705.owlsplit.feature.groups.detail.GroupIntent
import es.sebas1705.owlsplit.feature.groups.detail.GroupTab
import es.sebas1705.owlsplit.feature.groups.detail.GroupViewModel
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorIntent
import es.sebas1705.owlsplit.feature.groups.editor.GroupEditorViewModel
import es.sebas1705.owlsplit.feature.groups.members.MembersIntent
import es.sebas1705.owlsplit.feature.groups.members.MembersViewModel
import es.sebas1705.owlsplit.feature.groups.transfer.TransferIntent
import es.sebas1705.owlsplit.feature.groups.transfer.TransferViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupsViewModelsTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private val ledger = InMemoryLedger()
    private val accounts = FakeAccounts()
    private val profiles = FakeProfiles(UserProfile("Ana", "accent:BLUE", "USD"))
    private val balances = ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers)
    private val detail =
        ObserveGroupDetail(ledger.groups, ledger.members, ledger.expenses, ledger.transfers, balances, accounts)
    private val actions = GroupActions(ledger.groups, profiles, ledger.members)
    private val memberActions = MemberActions(ledger.members)

    /** Ana and Luis; Ana paid 30,00 for both. */
    private suspend fun dinnerGroup(): String {
        val g = actions.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"))
        memberActions.add(g, "Luis")
        val members = ledger.members.observeMembers(g).first()
        val ana = members.first { it.userUid == "uid-ana" }.id
        SaveExpense(
            ledger.expenses
        )(g, null, ExpenseDrafts.new(members, ana).copy(amountMinor = 3_000, title = "Cena"), "EUR", members)
        return g
    }

    @Test
    fun aNewGroupStartsInThePreferredCurrencyAndReportsItsId() = runTest {
        val vm = GroupEditorViewModel(null, actions, detail, ObserveAccount(accounts, profiles))
        assertEquals("USD", vm.uiState.value.currency)
        assertFalse(vm.uiState.value.canSave)
        vm.onEvent(GroupEditorIntent.NameChanged("Piso"))
        vm.onEvent(GroupEditorIntent.AccentChanged(GroupAccent.ORANGE))
        vm.onEvent(GroupEditorIntent.Save)
        val id = assertNotNull(vm.uiState.value.savedGroupId)
        val group = ledger.groups.observeGroup(id).first()!!
        assertEquals("ORANGE", group.accent)
        assertEquals("USD", group.baseCurrency)
    }

    @Test
    fun editingLoadsTheGroup() = runTest {
        val g = dinnerGroup()
        val vm = GroupEditorViewModel(g, actions, detail, ObserveAccount(accounts, profiles))
        assertTrue(vm.uiState.value.editing)
        assertEquals("Ibiza", vm.uiState.value.name)
        assertEquals("EUR", vm.uiState.value.currency)
    }

    @Test
    fun theGroupShowsMySharesAndSettlingClearsTheDebt() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(g, GroupTab.EXPENSES, detail, SettleUp(ledger.transfers), actions)
        val expense = ledger.expenseList.value.single()
        assertEquals(1_500L, vm.uiState.value.myShares[expense.id])

        val payment = vm.uiState.value.detail!!.balances!!.suggestedPayments.single()
        vm.onEvent(GroupIntent.AskSettle(payment))
        vm.onEvent(GroupIntent.ConfirmSettle)
        // A second confirmation before anything is recorded.
        assertEquals(payment, vm.uiState.value.finalSettle)
        assertTrue(ledger.transferList.value.isEmpty())
        vm.onEvent(GroupIntent.FinalSettle)
        assertTrue(vm.uiState.value.detail!!.balances!!.suggestedPayments.isEmpty())
        assertNull(vm.uiState.value.confirmSettle)
        assertNull(vm.uiState.value.finalSettle)
    }

    @Test
    fun theExplanationOpensWithTheBreakdownAndFollowsNewPayments() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(g, GroupTab.BALANCES, detail, SettleUp(ledger.transfers), actions)
        assertNull(vm.uiState.value.explanation)

        vm.onEvent(GroupIntent.ShowExplanation)
        val explanation = assertNotNull(vm.uiState.value.explanation)
        val ana = vm.uiState.value.detail!!.myMemberId
        val mine = explanation.members.first { it.memberId == ana }
        assertEquals(3_000L, mine.paidMinor)
        assertEquals(1_500L, mine.shareMinor)
        assertEquals(1_500L, mine.balanceMinor)
        assertEquals(vm.uiState.value.detail!!.balances!!.suggestedPayments, explanation.suggestedPayments)

        // Settling while it is open: the explanation follows the data.
        val payment = explanation.suggestedPayments.single()
        vm.onEvent(GroupIntent.AskSettle(payment))
        vm.onEvent(GroupIntent.ConfirmSettle)
        vm.onEvent(GroupIntent.FinalSettle)
        val after = assertNotNull(vm.uiState.value.explanation)
        assertTrue(after.settled)
        assertEquals(1_500L, after.members.first { it.memberId == ana }.receivedMinor)

        vm.onEvent(GroupIntent.DismissDialog)
        assertNull(vm.uiState.value.explanation)
    }

    @Test
    fun onlySomeoneWhoOwesMeCanBeRemindedOnce() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(g, GroupTab.SETTLE, detail, SettleUp(ledger.transfers), actions)
        // Ana paid the dinner: Luis owes her.
        val owedToMe = vm.uiState.value.detail!!.balances!!.suggestedPayments.single()
        assertEquals(vm.uiState.value.detail!!.myMemberId, owedToMe.toMemberId)

        vm.onEvent(GroupIntent.Remind(owedToMe))
        assertEquals(owedToMe, vm.uiState.value.reminder)
        vm.onEvent(GroupIntent.ReminderSent)
        assertNull(vm.uiState.value.reminder)
        // Reminding records nothing.
        assertTrue(ledger.transferList.value.isEmpty())

        // A debt that is mine, not theirs, has nobody to remind.
        val iOwe = owedToMe.copy(fromMemberId = owedToMe.toMemberId, toMemberId = owedToMe.fromMemberId)
        vm.onEvent(GroupIntent.Remind(iOwe))
        assertNull(vm.uiState.value.reminder)
    }

    @Test
    fun aRecordedPaymentCanBeDeletedAfterConfirming() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(
            g,
            GroupTab.SETTLE,
            detail,
            SettleUp(ledger.transfers),
            actions,
            DeleteTransfer(ledger.transfers)
        )
        val payment = vm.uiState.value.detail!!.balances!!.suggestedPayments.single()
        vm.onEvent(GroupIntent.AskSettle(payment))
        vm.onEvent(GroupIntent.ConfirmSettle)
        vm.onEvent(GroupIntent.FinalSettle)
        val transfer = vm.uiState.value.detail!!.transfers.single()

        vm.onEvent(GroupIntent.OpenTransfer(transfer))
        vm.onEvent(GroupIntent.AskDeleteTransfer)
        assertTrue(vm.uiState.value.confirmDeleteTransfer)
        vm.onEvent(GroupIntent.ConfirmDeleteTransfer)
        assertTrue(vm.uiState.value.detail!!.transfers.isEmpty())
        assertEquals(payment, vm.uiState.value.detail!!.balances!!.suggestedPayments.single())
        assertNull(vm.uiState.value.openTransfer)
    }

    @Test
    fun archivingTogglesAndLeavingClosesTheScreen() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(g, GroupTab.SETTLE, detail, SettleUp(ledger.transfers), actions)
        assertEquals(GroupTab.SETTLE, vm.uiState.value.tab)
        vm.onEvent(GroupIntent.ToggleArchive)
        assertTrue(vm.uiState.value.detail!!.archivedByMe)
        vm.onEvent(GroupIntent.AskLeave)
        vm.onEvent(GroupIntent.ConfirmLeave)
        assertTrue(vm.uiState.value.finalLeave)
        assertFalse(vm.uiState.value.closed)
        vm.onEvent(GroupIntent.FinalLeave)
        assertTrue(vm.uiState.value.closed)
    }

    @Test
    fun membersAreAddedEditedAndDeactivatedWhenInUse() = runTest {
        val g = dinnerGroup()
        val vm = MembersViewModel(g, detail, memberActions)
        vm.onEvent(MembersIntent.Add)
        vm.onEvent(MembersIntent.NameChanged("Marta"))
        vm.onEvent(MembersIntent.WeightChanged(2))
        vm.onEvent(MembersIntent.Save)
        val marta = vm.uiState.value.members.first { it.name == "Marta" }
        assertEquals(2, marta.defaultWeight)

        vm.onEvent(MembersIntent.Edit(marta))
        vm.onEvent(MembersIntent.Delete)
        assertNull(vm.uiState.value.form)
        assertTrue(vm.uiState.value.members.none { it.name == "Marta" })

        val luis = vm.uiState.value.members.first { it.name == "Luis" }
        vm.onEvent(MembersIntent.Edit(luis))
        vm.onEvent(MembersIntent.Delete)
        assertTrue(vm.uiState.value.form!!.inUse)
        assertFalse(vm.uiState.value.members.first { it.name == "Luis" }.active)

        val me = vm.uiState.value.members.first { it.userUid == "uid-ana" }
        vm.onEvent(MembersIntent.Edit(me))
        assertTrue(vm.uiState.value.form!!.isMe)
        vm.onEvent(MembersIntent.ActiveChanged(false))
        assertTrue(vm.uiState.value.form!!.active)
    }

    @Test
    fun tripDatesAreOptionalButOrdered() = runTest {
        val vm = GroupEditorViewModel(null, actions, detail, ObserveAccount(accounts, profiles))
        vm.onEvent(GroupEditorIntent.NameChanged("Ibiza"))
        vm.onEvent(GroupEditorIntent.StartChanged(LocalDate(2026, 7, 20)))
        vm.onEvent(GroupEditorIntent.EndChanged(LocalDate(2026, 7, 10)))
        assertFalse(vm.uiState.value.canSave)
        vm.onEvent(GroupEditorIntent.EndChanged(LocalDate(2026, 7, 27)))
        vm.onEvent(GroupEditorIntent.Save)
        val group = ledger.groups.observeGroup(vm.uiState.value.savedGroupId!!).first()!!
        assertEquals(LocalDate(2026, 7, 20), group.startDate)

        val edit = GroupEditorViewModel(group.id, actions, detail, ObserveAccount(accounts, profiles))
        edit.onEvent(GroupEditorIntent.NameChanged("Ibiza 26"))
        edit.onEvent(GroupEditorIntent.Save)
        assertEquals(LocalDate(2026, 7, 27), ledger.groups.observeGroup(group.id).first()!!.endDate) // kept on edit
    }

    @Test
    fun aManualPaymentStartsFromMeAndNeedsAnAmount() = runTest {
        val g = dinnerGroup()
        val vm = TransferViewModel(g, detail, RecordTransfer(ledger.transfers))
        val ana = vm.uiState.value.myMemberId
        assertEquals(ana, vm.uiState.value.fromId)
        assertFalse(vm.uiState.value.canSave)
        vm.onEvent(TransferIntent.ToChanged(ana!!))
        vm.onEvent(TransferIntent.AmountChanged("12,5"))
        assertTrue(vm.uiState.value.sameMember)
        val luis = vm.uiState.value.members.first { it.name == "Luis" }.id
        vm.onEvent(TransferIntent.FromChanged(luis))
        vm.onEvent(TransferIntent.AmountChanged("12,555")) // too many decimals: ignored
        assertEquals("12,5", vm.uiState.value.amountText)
        vm.onEvent(TransferIntent.Save)
        assertTrue(vm.uiState.value.confirming)
        vm.onEvent(TransferIntent.ConfirmSave)
        assertTrue(vm.uiState.value.done)
        assertEquals(1_250L, ledger.transferList.value.single().amountMinor)
    }

    @Test
    fun searchAndFiltersNarrowTheList() = runTest {
        val g = dinnerGroup()
        val vm = GroupViewModel(g, GroupTab.EXPENSES, detail, SettleUp(ledger.transfers), actions)
        vm.onEvent(GroupIntent.Search("cena"))
        assertEquals(1, vm.uiState.value.visibleExpenses.size)
        vm.onEvent(GroupIntent.Search("taxi"))
        assertTrue(vm.uiState.value.visibleExpenses.isEmpty())
        vm.onEvent(GroupIntent.ClearFilters)
        vm.onEvent(GroupIntent.FilterCategory(ExpenseCategory.FOOD))
        assertTrue(vm.uiState.value.visibleExpenses.isEmpty()) // the dinner was saved as OTHER
        vm.onEvent(GroupIntent.ClearFilters)
        assertFalse(vm.uiState.value.filter.active)
        assertEquals(1, vm.uiState.value.visibleExpenses.size)
    }
}
