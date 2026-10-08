package es.sebas1705.owlsplit.domain.usecases.invite

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class InviteUseCasesTest {
    private val ledger = InMemoryLedger()
    private val sync = FakeSync()
    private val accounts = FakeAccounts()

    private val server = FakeInvites(ledger)
    private val share = ShareInvite(ledger.groups, ledger.members, server, sync, "https://owlsplit.web.app")

    @Test
    fun codesAreNormalisedFromWhateverWasPasted() {
        assertEquals("ABCD2345", InviteCodes.normalize(" abcd-2345 "))
        assertEquals("ABCD2345", InviteCodes.normalize("https://owlsplit.web.app/j/ABCD2345"))
        assertTrue(InviteCodes.isValid("ABCD2345"))
        assertFalse(InviteCodes.isValid("ABCD0O1I")) // ambiguous characters are never generated
        assertEquals("ABCD 2345", InviteCodes.display("ABCD2345"))
    }

    @Test
    fun sharingNeedsTheServerAndPublishesTheCurrentCode() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        sync.online = false
        assertEquals(InviteResult.Offline, share(g))
        sync.online = true
        val ready = share(g) as InviteResult.Ready
        assertEquals("https://owlsplit.web.app/j/ABCDEFGH", ready.link.url)

        val regenerated = RegenerateInvite(ledger.groups, server, sync, share)(g) as InviteResult.Ready
        assertTrue(regenerated.link.code != "ABCDEFGH")
        assertEquals(setOf(regenerated.link.code), server.published.keys) // the old one is withdrawn
    }

    @Test
    fun joiningThenClaimingAGhostOrAddingMyself() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val ghost = ledger.members.add(g, MemberInput("Luis"))
        share(g)

        ledger.uid = "uid-luis"
        accounts.session.value = accounts.session.value!!.copy(uid = "uid-luis")
        val preview = (PreviewInvite(server)("abcd-efgh") as PreviewResult.Found).preview
        val joined = JoinGroup(server, ledger.groups, accounts, arrival = 1.seconds)(preview)
        assertEquals(JoinResult.Joined(g), joined)
        assertTrue("uid-luis" in ledger.groups.observeGroup(g).first()!!.memberUids)

        val claim = ClaimMember(ledger.members, FakeProfiles(UserProfile("Luis G", "accent:PINK", "EUR")), accounts)
        claim(g, ghost)
        claim(g, null) // already have a member: nothing more
        val members = ledger.members.observeMembers(g).first()
        assertEquals(2, members.size)
        assertEquals("uid-luis", members.first { it.id == ghost }.userUid)
    }

    @Test
    fun aStaleCodeIsRejected() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        server.joinResult = AccountResult.Failed("PERMISSION_DENIED")
        accounts.session.value = accounts.session.value!!.copy(uid = "uid-luis")
        val result =
            JoinGroup(
                server,
                ledger.groups,
                accounts,
                arrival = 1.seconds
            )(InvitePreview("OLDCODE2", g, "Ibiza", "plane", 1))
        assertEquals(JoinResult.Rejected, result)
        assertEquals(PreviewResult.NotFound, PreviewInvite(server)("nope"))
    }
}
