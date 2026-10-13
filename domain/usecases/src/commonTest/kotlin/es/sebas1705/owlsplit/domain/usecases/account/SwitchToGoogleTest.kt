package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeGoogle
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.account.FakeSync
import es.sebas1705.owlsplit.core.testing.ledger.FakeInvites
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SwitchToGoogleTest {
    // A new install that started without signing in; the Google account already exists elsewhere.
    private val ledger = InMemoryLedger(uid = "uid-ana")
    private val fake = FakeAccounts()

    /** Signing in changes who the ledger writes as, like the app's session does. */
    private val accounts = object : AccountService by fake {
        override suspend fun signInWithGoogle(idToken: String, accessToken: String?): AccountResult =
            fake.signInWithGoogle(idToken, accessToken).also {
                if (it == AccountResult.Done) ledger.uid = fake.currentSession()!!.uid
            }
    }
    private val sync = FakeSync()
    private val invites = FakeInvites(ledger)
    private val profiles =
        FakeProfiles(UserProfile("Ana", "accent:BLUE", "EUR"), remote = UserProfile("Ana G", "accent:GREEN", "EUR"))
    private val carry = CarryGroups(
        ledger.groups,
        ledger.members,
        accounts,
        ShareInvite(ledger.groups, ledger.members, invites, sync, "https://owlsplit.web.app"),
        PreviewInvite(invites),
        JoinGroup(invites, ledger.groups, accounts, arrival = 1.seconds),
        ClaimMember(ledger.members, profiles, accounts),
    )
    private fun switch() = SwitchToGoogle(FakeGoogle(), accounts, profiles, sync, carry)

    @Test
    fun signsInToTheExistingAccountAndTakesThisDevicesGroupsWithTheSameMember() = runTest {
        val g = ledger.groups.create(GroupInput("Piso nuevo", "home", "GREEN", "EUR"), "Ana")
        val mine = ledger.members.observeMembers(g).first().single()

        val outcome = switch()()

        assertEquals(GoogleOutcome.Done(UserProfile("Ana G", "accent:GREEN", "EUR")), outcome)
        assertEquals("uid-google", accounts.currentSession()!!.uid)
        assertEquals(UserProfile("Ana G", "accent:GREEN", "EUR"), profiles.profile.value)
        val group = ledger.groups.observeGroup(g).first()!!
        assertEquals(listOf("uid-google"), group.memberUids)
        // The same member, with its expenses: now the Google account's, and it remembers who had it.
        val member = ledger.members.observeMembers(g).first().single()
        assertEquals(mine.id, member.id)
        assertEquals("uid-google", member.userUid)
        assertTrue("uid-ana" in member.pastUids)
    }

    @Test
    fun offlineNothingIsLeftAndNoSignInHappens() = runTest {
        val g = ledger.groups.create(GroupInput("Piso nuevo", "home", "GREEN", "EUR"), "Ana")
        sync.online = false

        val outcome = switch()()

        assertEquals(GoogleOutcome.Failed(AccountResult.Offline), outcome)
        assertFalse(fake.calls.any { it.startsWith("signInWithGoogle") })
        assertEquals(listOf("uid-ana"), ledger.groups.observeGroup(g).first()!!.memberUids)
    }

    @Test
    fun ifSigningInFailsTheGroupsAreRejoinedByTheAccountThatLeftThem() = runTest {
        val g = ledger.groups.create(GroupInput("Piso nuevo", "home", "GREEN", "EUR"), "Ana")
        val mine = ledger.members.observeMembers(g).first().single()
        fake.signInResult = AccountResult.Failed("network")

        switch()()

        assertEquals(listOf("uid-ana"), ledger.groups.observeGroup(g).first()!!.memberUids)
        assertEquals("uid-ana", ledger.members.observeMembers(g).first().single { it.id == mine.id }.userUid)
    }
}
