package es.sebas1705.owlsplit.feature.home

import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.groups.RejoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import kotlin.time.Duration.Companion.seconds
import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.core.datastore.UpdateSettings
import es.sebas1705.owlsplit.data.models.LatestRelease
import es.sebas1705.owlsplit.data.repository.update.ReleaseFeed
import es.sebas1705.owlsplit.domain.usecases.update.CheckForUpdate
import es.sebas1705.owlsplit.domain.usecases.update.NoStoreUpdates
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdate
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdates
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

    private class Dismissals : UpdateSettings {
        override val dismissedUpdate = MutableStateFlow<Long?>(null)
        override suspend fun dismissUpdate(versionCode: Long) {
            dismissedUpdate.value = versionCode
        }
    }

    /** Play as the tests drive it: what it reports, and what Home asked of it. */
    private class FakeStore : StoreUpdates {
        override val update = MutableStateFlow<StoreUpdate?>(null)
        var started = 0
        var completed = 0
        override suspend fun start() {
            started++
        }

        override suspend fun complete() {
            completed++
        }
    }

    /** Store installs: the download page is never asked (installedCode is null there). */
    private val noRelease = CheckForUpdate(
        object : ReleaseFeed {
            override suspend fun latest() = error("A store install never reads the download page")
        },
        installedCode = null,
    )

    private fun viewModel(
        checkForUpdate: CheckForUpdate? = null,
        updates: UpdateSettings? = null,
        store: StoreUpdates = NoStoreUpdates,
        rejoin: RejoinGroup? = null,
    ) = HomeViewModel(
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
        updates = if (checkForUpdate != null && updates != null) UpdateOffer(checkForUpdate, updates, store) else null,
        rejoinGroup = rejoin,
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
    fun aGroupILeftIsListedApartAndGoingBackInOpensIt() = runTest {
        val invites = FakeInvites(ledger)
        val actions = GroupActions(ledger.groups, profiles, ledger.members, invites)
        val g = actions.create(GroupInput("Piso", "home", "GREEN", "EUR"))
        actions.leave(g)
        val rejoin = RejoinGroup(
            ledger.groups,
            PreviewInvite(invites),
            JoinGroup(invites, ledger.groups, accounts, arrival = 1.seconds),
            ClaimMember(ledger.members, profiles, accounts),
        )
        val vm = viewModel(rejoin = rejoin)
        assertEquals(listOf(g), vm.uiState.value.left.map { it.id })
        assertTrue(vm.uiState.value.groups.isEmpty())

        vm.onEvent(HomeIntent.AskRejoin(vm.uiState.value.left.single()))
        vm.onEvent(HomeIntent.ConfirmRejoin)

        assertEquals(g, vm.uiState.value.rejoined)
        assertEquals(null, vm.uiState.value.rejoin)
        assertEquals(listOf(g), vm.uiState.value.groups.map { it.group.id })
        assertTrue(vm.uiState.value.left.isEmpty())
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
        val updates = Dismissals()
        val vm = viewModel(CheckForUpdate(feed, installedCode = 180), updates)
        assertEquals(beta5, vm.uiState.value.update)
        assertNull(vm.uiState.value.storeUpdate)

        vm.onEvent(HomeIntent.DismissUpdate)
        assertNull(vm.uiState.value.update)
        assertNull(viewModel(CheckForUpdate(feed, installedCode = 180), updates).uiState.value.update)
    }

    @Test
    fun aStoreUpdateDownloadsAndThenRestarts() = runTest {
        val store = FakeStore()
        val vm = viewModel(noRelease, Dismissals(), store)
        assertNull(vm.uiState.value.storeUpdate)
        assertNull(vm.uiState.value.update)

        store.update.value = StoreUpdate.Available(200)
        assertEquals(StoreUpdate.Available(200), vm.uiState.value.storeUpdate)
        vm.onEvent(HomeIntent.StartStoreUpdate)
        assertEquals(1, store.started)

        // Nothing to do while Play downloads it.
        store.update.value = StoreUpdate.Downloading(200)
        assertNull(vm.uiState.value.storeUpdate)

        store.update.value = StoreUpdate.Downloaded(200)
        assertEquals(StoreUpdate.Downloaded(200), vm.uiState.value.storeUpdate)
        vm.onEvent(HomeIntent.CompleteStoreUpdate)
        assertEquals(1, store.completed)
    }

    @Test
    fun aPutOffStoreUpdateWaitsForTheNextOne() = runTest {
        val store = FakeStore().apply { update.value = StoreUpdate.Available(200) }
        val dismissals = Dismissals()
        val vm = viewModel(noRelease, dismissals, store)

        vm.onEvent(HomeIntent.DismissUpdate)
        assertEquals(200L, dismissals.dismissedUpdate.value)
        assertNull(vm.uiState.value.storeUpdate)
        assertNull(viewModel(noRelease, dismissals, store).uiState.value.storeUpdate)

        store.update.value = StoreUpdate.Available(210)
        assertEquals(StoreUpdate.Available(210), vm.uiState.value.storeUpdate)
    }

    @Test
    fun aDownloadedStoreUpdateIsShownEvenIfItWasPutOff() {
        assertEquals(StoreUpdate.Downloaded(200), shownStoreUpdate(StoreUpdate.Downloaded(200), dismissed = 200))
        assertNull(shownStoreUpdate(StoreUpdate.Available(200), dismissed = 200))
        assertEquals(StoreUpdate.Available(200), shownStoreUpdate(StoreUpdate.Available(200), dismissed = 190))
        assertNull(shownStoreUpdate(StoreUpdate.Downloading(200), dismissed = null))
        assertNull(shownStoreUpdate(null, dismissed = null))
    }
}
