package es.sebas1705.owlsplit.feature.home

import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private class Nudge : LinkNudgeSettings {
        override val linkNudgeDismissed = MutableStateFlow(false)
        override suspend fun dismissLinkNudge() {
            linkNudgeDismissed.value = true
        }
    }

    private val ledger = InMemoryLedger()
    private val accounts = FakeAccounts()
    private val profiles = FakeProfiles(UserProfile("Ana", "accent:BLUE", "EUR"))
    private val sync = FakeSync()
    private val nudge = Nudge()

    private fun viewModel() = HomeViewModel(
        ObserveHome(
            ledger.groups,
            ledger.members,
            ledger.expenses,
            ledger.transfers,
            ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers),
            accounts,
            profiles,
        ),
        ObserveAccount(accounts, profiles),
        ObservePendingChanges(sync),
        nudge,
    )

    @Test
    fun groupsPendingChangesAndTheLinkSuggestion() = runTest {
        val vm = viewModel()
        assertTrue(vm.uiState.value.loaded)
        assertTrue(vm.uiState.value.groups.isEmpty())
        assertFalse(vm.uiState.value.suggestLinking)

        ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        sync.pendingCount.value = 3
        assertEquals(1, vm.uiState.value.groups.size)
        assertEquals(3, vm.uiState.value.pending)
        assertTrue(vm.uiState.value.suggestLinking)

        vm.onEvent(HomeIntent.DismissNudge)
        assertFalse(vm.uiState.value.suggestLinking)
    }

    @Test
    fun aQuickActionAsksWhichGroup() = runTest {
        val vm = viewModel()
        vm.onEvent(HomeIntent.Pick(QuickAction.SETTLE))
        assertEquals(QuickAction.SETTLE, vm.uiState.value.picking)
        vm.onEvent(HomeIntent.DismissPicker)
        assertEquals(null, vm.uiState.value.picking)
    }
}
