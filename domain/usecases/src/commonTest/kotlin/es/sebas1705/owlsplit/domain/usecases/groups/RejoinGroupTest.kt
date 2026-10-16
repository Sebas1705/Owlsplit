package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RejoinGroupTest {
    private val ledger = InMemoryLedger(uid = "uid-ana")
    private val accounts = FakeAccounts()
    private val invites = FakeInvites(ledger)
    private val profiles = FakeProfiles(UserProfile("Ana", "accent:BLUE", "EUR"))
    private val actions = GroupActions(ledger.groups, profiles, ledger.members, invites)
    private val rejoin = RejoinGroup(
        ledger.groups,
        PreviewInvite(invites),
        JoinGroup(invites, ledger.groups, accounts, arrival = 1.seconds),
        ClaimMember(ledger.members, profiles, accounts),
    )

    @Test
    fun leavingByMistakeICanGoBackInWithMyOwnMember() = runTest {
        val g = actions.create(GroupInput("Piso", "home", "GREEN", "EUR"))
        val mine = ledger.members.observeMembers(g).first().single()
        actions.leave(g)
        assertEquals(emptyList(), ledger.groups.observeGroup(g).first()!!.memberUids)

        assertEquals(RejoinResult.JOINED, rejoin(g))

        assertEquals(listOf("uid-ana"), ledger.groups.observeGroup(g).first()!!.memberUids)
        val member = ledger.members.observeMembers(g).first().single()
        // The same member, not a new one: its expenses and balance are mine again.
        assertEquals(mine.id, member.id)
        assertEquals("uid-ana", member.userUid)
    }

    @Test
    fun aCodeThatNoLongerWorksAsksForANewInvite() = runTest {
        val g = actions.create(GroupInput("Piso", "home", "GREEN", "EUR"))
        actions.leave(g)
        // Someone regenerated the code after I left.
        invites.published.clear()

        assertEquals(RejoinResult.NEEDS_INVITE, rejoin(g))
        assertTrue(ledger.groups.observeGroup(g).first()!!.memberUids.isEmpty())
    }

    @Test
    fun offlineNothingChanges() = runTest {
        val g = actions.create(GroupInput("Piso", "home", "GREEN", "EUR"))
        actions.leave(g)
        invites.online = false

        assertEquals(RejoinResult.OFFLINE, rejoin(g))
        invites.online = true
        invites.joinResult = AccountResult.Failed("full")
        assertEquals(RejoinResult.NEEDS_INVITE, rejoin(g))
    }
}
