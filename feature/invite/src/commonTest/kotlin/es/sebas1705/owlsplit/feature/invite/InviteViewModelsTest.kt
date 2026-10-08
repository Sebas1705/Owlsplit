package es.sebas1705.owlsplit.feature.invite

import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import es.sebas1705.owlsplit.domain.usecases.invite.RegenerateInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class InviteViewModelsTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private val ledger = InMemoryLedger()
    private val accounts = FakeAccounts()
    private val profiles = FakeProfiles(UserProfile("Luis G", "accent:PINK", "EUR"))
    private val sync = FakeSync()
    private val server = FakeInvites(ledger)
    private val balances = ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers)
    private val detail =
        ObserveGroupDetail(ledger.groups, ledger.members, ledger.expenses, ledger.transfers, balances, accounts)
    private val share = ShareInvite(ledger.groups, ledger.members, server, sync, "https://owlsplit-dev.web.app")

    private fun inviteVm(groupId: String) =
        InviteViewModel(groupId, detail, share, RegenerateInvite(ledger.groups, server, sync, share))

    private fun joinVm(code: String? = null) = JoinViewModel(
        code,
        PreviewInvite(server),
        JoinGroup(server, ledger.groups, accounts, arrival = 1.seconds),
        ClaimMember(ledger.members, profiles, accounts),
        detail,
        ObserveAccount(accounts, profiles),
    )

    /** Ana's group with Luis apuntado (no account yet), shared; then Luis signs in on his phone. */
    private suspend fun sharedGroupAsLuis(): Pair<String, String> {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val luis = ledger.members.add(g, MemberInput("Luis"))
        share(g)
        ledger.uid = "uid-luis"
        accounts.session.value = accounts.session.value!!.copy(uid = "uid-luis")
        return g to luis
    }

    @Test
    fun theInviteScreenShowsTheLinkAndRegeneratesTheCode() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val vm = inviteVm(g)
        assertEquals(InviteStatus.READY, vm.uiState.value.status)
        assertEquals("Ibiza", vm.uiState.value.groupName)
        assertEquals("https://owlsplit-dev.web.app/j/ABCDEFGH", vm.uiState.value.link?.url)

        vm.onEvent(InviteIntent.AskRegenerate)
        assertEquals(true, vm.uiState.value.confirmRegenerate)
        vm.onEvent(InviteIntent.ConfirmRegenerate)
        assertNotEquals("ABCDEFGH", vm.uiState.value.link?.code)
        assertEquals(InviteStatus.READY, vm.uiState.value.status)
    }

    @Test
    fun withoutConnectionTheInviteAsksToRetry() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        sync.online = false
        val vm = inviteVm(g)
        assertEquals(InviteStatus.OFFLINE, vm.uiState.value.status)
        sync.online = true
        vm.onEvent(InviteIntent.Retry)
        assertEquals(InviteStatus.READY, vm.uiState.value.status)
    }

    @Test
    fun joiningByCodeAndClaimingTheGhost() = runTest {
        val (g, luis) = sharedGroupAsLuis()
        val vm = joinVm()
        vm.onEvent(JoinIntent.CodeChanged("abcd efg"))
        assertEquals(false, vm.uiState.value.canSearch)
        vm.onEvent(JoinIntent.CodeChanged("abcd efgh"))
        vm.onEvent(JoinIntent.Search)
        assertEquals(JoinStep.PREVIEW, vm.uiState.value.step)
        assertEquals("Ibiza", vm.uiState.value.preview?.groupName)
        assertEquals(2, vm.uiState.value.preview?.memberCount)

        vm.onEvent(JoinIntent.Join)
        assertEquals(JoinStep.PICK, vm.uiState.value.step)
        assertEquals(listOf(Ghost(luis, "Luis")), vm.uiState.value.ghosts)
        assertEquals("Luis G", vm.uiState.value.myName)

        vm.onEvent(JoinIntent.Pick(luis))
        assertEquals(g, vm.uiState.value.joinedGroupId)
        assertEquals("uid-luis", ledger.members.observeMembers(g).first().first { it.id == luis }.userUid)
    }

    @Test
    fun aLinkSearchesByItselfAndANewMemberCanBeAdded() = runTest {
        val (g, _) = sharedGroupAsLuis()
        val vm = joinVm("https://owlsplit-dev.web.app/j/ABCDEFGH")
        assertEquals("ABCD EFGH", vm.uiState.value.code)
        assertEquals(JoinStep.PREVIEW, vm.uiState.value.step)
        vm.onEvent(JoinIntent.Join)
        vm.onEvent(JoinIntent.Pick(null))
        assertEquals(g, vm.uiState.value.joinedGroupId)
        val members = ledger.members.observeMembers(g).first()
        assertEquals("Luis G", members.first { it.userUid == "uid-luis" }.name)
        assertEquals(3, members.size)
    }

    @Test
    fun aScannedLinkSearchesAndAForeignQrIsNotFound() = runTest {
        sharedGroupAsLuis()
        val vm = joinVm()
        vm.onEvent(JoinIntent.Scanned("https://example.com/menu"))
        assertEquals(JoinError.NOT_FOUND, vm.uiState.value.error)
        vm.onEvent(JoinIntent.Scanned("https://owlsplit-dev.web.app/j/ABCDEFGH"))
        assertEquals("ABCD EFGH", vm.uiState.value.code)
        assertEquals(JoinStep.PREVIEW, vm.uiState.value.step)
    }

    @Test
    fun unknownCodesStaleCodesAndNoConnectionAreExplained() = runTest {
        sharedGroupAsLuis()
        val vm = joinVm()
        vm.onEvent(JoinIntent.CodeChanged("ZZZZZZZZ"))
        vm.onEvent(JoinIntent.Search)
        assertEquals(JoinError.NOT_FOUND, vm.uiState.value.error)
        assertEquals(JoinStep.ENTER, vm.uiState.value.step)

        vm.onEvent(JoinIntent.CodeChanged("ABCDEFGH"))
        assertNull(vm.uiState.value.error)
        vm.onEvent(JoinIntent.Search)
        server.online = false
        vm.onEvent(JoinIntent.Join)
        assertEquals(JoinError.OFFLINE, vm.uiState.value.error)
        server.online = true
        server.joinResult = AccountResult.Failed("PERMISSION_DENIED")
        vm.onEvent(JoinIntent.Join)
        assertEquals(JoinError.REJECTED, vm.uiState.value.error)
        assertEquals(JoinStep.PREVIEW, vm.uiState.value.step)
    }
}
