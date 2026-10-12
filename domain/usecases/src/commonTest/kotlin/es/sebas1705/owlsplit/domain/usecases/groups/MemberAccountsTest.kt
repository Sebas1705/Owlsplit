package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberAccountsTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private fun member(id: String, uid: String?, past: List<String> = emptyList()) =
        Member(id, "g", id, null, 1, uid, true, 0, meta, pastUids = past)

    private var at = 0L
    private fun entry(member: String, author: String, action: ChangeAction, before: String?, after: String?) =
        ChangeLogEntry(
            "e${++at}",
            "g",
            EntityType.MEMBER,
            member,
            action,
            author,
            listOf(FieldChange("userUid", before, after)),
            at,
        )

    @Test
    fun formerAccountsComeFromPastUidsAndFromTheHistoryOfOlderGroups() {
        val members = listOf(
            member("carlos", null, past = listOf("uid-c1")),
            member("marta", null),
            member("ana", "uid-a")
        )
        // Marta left before P14: only the history knows she was uid-m.
        val history = listOf(
            entry("marta", "uid-m", ChangeAction.JOINED, null, "uid-m"),
            entry("marta", "uid-m", ChangeAction.UPDATED, "uid-m", null),
        )
        val former = MemberAccounts.former(members, history)
        assertEquals(setOf("uid-c1"), former["carlos"])
        assertEquals(setOf("uid-m"), former["marta"])
        assertEquals(emptySet(), former["ana"])
        assertEquals("marta", MemberAccounts.reclaimable("uid-m", members, history)?.id)
        assertNull(MemberAccounts.reclaimable("uid-stranger", members, history))
    }

    @Test
    fun rejoinsTellTheSameAccountFromAnotherOne() {
        val history = listOf(
            entry("carlos", "uid-c1", ChangeAction.JOINED, null, "uid-c1"),
            entry("carlos", "uid-c1", ChangeAction.UPDATED, "uid-c1", null),
            entry("carlos", "uid-c1", ChangeAction.JOINED, null, "uid-c1"),
            entry("carlos", "uid-c1", ChangeAction.UPDATED, "uid-c1", null),
            entry("carlos", "uid-c2", ChangeAction.JOINED, null, "uid-c2"),
        )
        val rejoins = MemberAccounts.rejoins(history)
        assertNull(rejoins["e1"])
        assertEquals(Rejoin.SAME_ACCOUNT, rejoins["e3"])
        assertEquals(Rejoin.OTHER_ACCOUNT, rejoins["e5"])
    }

    @Test
    fun leavingAndJoiningAgainGivesMeMyMemberBackWithoutAsking() = runTest {
        val ledger = InMemoryLedger()
        val accounts = FakeAccounts(Session("uid-luis", isAnonymous = true, providers = emptyList()))
        val claim = ClaimMember(ledger.members, FakeProfiles(UserProfile("Luis", "", "EUR")), accounts)
        val g = ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")

        ledger.uid = "uid-luis"
        claim(g, null)
        ledger.groups.leave(g)
        val left = ledger.members.observeMembers(g).first().first { it.name == "Luis" }
        assertTrue(left.abandoned)
        assertEquals(listOf("uid-luis"), left.pastUids)

        assertTrue(claim.reclaim(g))
        val back = ledger.members.observeMembers(g).first().first { it.id == left.id }
        assertEquals("uid-luis", back.userUid)
        assertFalse(back.abandoned)
        assertEquals(2, ledger.members.observeMembers(g).first().size)
    }

    @Test
    fun anotherAccountIsNotGivenSomebodysMemberOnItsOwn() = runTest {
        val ledger = InMemoryLedger()
        val g = ledger.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")
        ledger.uid = "uid-luis"
        ClaimMember(
            ledger.members,
            FakeProfiles(UserProfile("Luis", "", "EUR")),
            FakeAccounts(Session("uid-luis", true, emptyList()))
        )(g, null)
        ledger.groups.leave(g)

        ledger.uid = "uid-luis-2"
        val other = ClaimMember(
            ledger.members,
            FakeProfiles(UserProfile("Luis", "", "EUR")),
            FakeAccounts(Session("uid-luis-2", true, emptyList()))
        )
        assertFalse(other.reclaim(g))
        assertEquals(setOf("uid-luis"), other.formerAccounts(g).values.flatten().toSet())
    }
}
