package es.sebas1705.owlsplit.feature.home

import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.core.datastore.UpdateSettings
import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed
import es.sebas1705.owlsplit.domain.usecases.update.CheckForUpdate
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.data.repository.fx.FxQuote
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.usecases.fx.ConvertTotal
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    private fun viewModel(checkForUpdate: CheckForUpdate? = null, updates: UpdateSettings? = null) = HomeViewModel(
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
        ConvertTotal(
            object : FxRates {
                override suspend fun latest(from: String, to: String) = FxQuote(FxRate.parse("0.9"), "2026-10-07")
            },
        ),
        nudge,
        updates = if (checkForUpdate != null && updates != null) UpdateOffer(checkForUpdate, updates) else null,
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

    @Test
    fun groupsInOtherCurrenciesAreAddedAtTodaysRate() = runTest {
        val vm = viewModel()
        val usd = ledger.groups.create(GroupInput("NYC", "plane", "BLUE", "USD"), "Ana")
        val luis = ledger.members.add(usd, MemberInput("Luis"))
        val ana = ledger.memberList.value.first { it.groupId == usd && it.userUid == "uid-ana" }.id
        ledger.expenses.create(
            usd,
            ExpenseInput(
                "Taxi", ExpenseCategory.TRANSPORT, null,
                LocalDate(2026, 10, 8), "USD", 2_000, FxRate.SCALE, mapOf(ana to 2_000L),
                SplitType.EQUAL, mapOf(ana to 1L, luis to 1L),
            ),
        )
        assertEquals(900L, vm.uiState.value.total) // +10,00 USD × 0,9
        assertTrue(vm.uiState.value.approximate)
        assertTrue(vm.uiState.value.otherCurrencies.isEmpty())
    }

    @Test
    fun aNewerReleaseIsOfferedUntilItIsPutOff() = runTest {
        val beta5 = LatestRelease("1.0.0-beta.5", 190, "https://x/a.apk")
        val feed = object : ReleaseFeed {
            override suspend fun latest() = beta5
        }
        val updates = object : UpdateSettings {
            override val dismissedUpdate = MutableStateFlow<Long?>(null)
            override suspend fun dismissUpdate(versionCode: Long) {
                dismissedUpdate.value = versionCode
            }
        }
        val vm = viewModel(CheckForUpdate(feed, installedCode = 180), updates)
        assertEquals(beta5, vm.uiState.value.update)

        vm.onEvent(HomeIntent.DismissUpdate)
        assertNull(vm.uiState.value.update)
        assertNull(viewModel(CheckForUpdate(feed, installedCode = 180), updates).uiState.value.update)
    }
}
