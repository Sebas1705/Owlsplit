package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.PayMethods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberRepositoryTest {
    @Test
    fun `members get increasing sort keys and keep their order`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Viaje", "plane", "blue", "EUR"), "Ana")
        env.members.add(g, MemberInput("Luis"))
        env.members.add(g, MemberInput("Marta", defaultWeight = 2))
        val members = env.members.observeMembersOnce(g)
        assertEquals(listOf("Ana", "Luis", "Marta"), members.map { it.name })
        assertEquals(listOf(0L, 1L, 2L), members.map { it.sortKey })
        assertEquals(2L, members.last().defaultWeight)
    }

    @Test
    fun `a member with movements cannot be deleted, only deactivated`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Viaje", "plane", "blue", "EUR"), "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val zoe = env.members.add(g, MemberInput("Zoe"))
        env.expenses.create(
            g,
            ExpenseInput(
                "Taxi", ExpenseCategory.TRANSPORT, null, LocalDate(2026, 7, 12), "EUR", 2_000, 1_000_000_000,
                mapOf(ana to 2_000L), SplitType.EQUAL, mapOf(ana to 1L, luis to 1L),
            ),
        )

        assertFalse(env.members.deleteIfUnused(luis))
        env.members.setActive(luis, false)
        assertTrue(env.members.deleteIfUnused(zoe))

        val members = env.members.observeMembersOnce(g)
        assertEquals(listOf("Ana", "Luis"), members.map { it.name })
        assertFalse(members.first { it.id == luis }.active)
    }

    @Test
    fun `joining someone claims a ghost or adds a new member, both logged as joined`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val ghost = env.members.add(g, MemberInput("Luis"))
        env.uid = "uid-luis"
        env.members.claim(ghost)
        val luis = env.members.observeMembersOnce(g).first { it.id == ghost }
        assertEquals("uid-luis", luis.userUid)

        env.uid = "uid-marta"
        val marta = env.members.addMe(g, "Marta", null)
        assertEquals("uid-marta", env.members.observeMembersOnce(g).first { it.id == marta }.userUid)
        val joined = env.changeLog.observe(g).first().filter { it.action == ChangeAction.JOINED }
        assertEquals(setOf("uid-marta", "uid-luis"), joined.map { it.authorUid }.toSet())
    }

    @Test
    fun `leaving keeps who I was, once, and the history can tell I came back`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")
        val ana = env.members.observeMembersOnce(g).single()
        env.members.publishMyLinked(true)
        env.groups.leave(g)
        val left = env.members.observeMembersOnce(g).single()
        assertEquals(listOf("uid-ana"), left.pastUids)
        assertNull(left.userUid)
        assertFalse(left.linked)

        env.members.claim(ana.id)
        env.groups.leave(g)
        // Leaving again does not repeat me.
        assertEquals(listOf("uid-ana"), env.members.observeMembersOnce(g).single().pastUids)
    }

    @Test
    fun `pay handles reach my members in every group, stay out of the history and leave with me`() = runTest {
        val env = TestEnv(this)
        val ibiza = env.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val piso = env.groups.create(GroupInput("Piso", "home", "GREEN", "EUR"), "Ana")
        val luis = env.members.add(ibiza, MemberInput("Luis"))
        val historyBefore = env.changeLog.observe(ibiza).first().size
        val pay = PayMethods(bizum = "600111222")

        env.members.publishMyPayMethods(pay)

        suspend fun mine(g: String) = env.members.observeMembersOnce(g).first { it.userUid == env.uid }
        assertEquals(pay, mine(ibiza).payMethods)
        assertEquals(pay, mine(piso).payMethods)
        assertNull(env.members.observeMembersOnce(ibiza).first { it.id == luis }.payMethods)
        assertEquals(historyBefore, env.changeLog.observe(ibiza).first().size)

        env.groups.leave(ibiza)
        assertNull(env.members.observeMembersOnce(ibiza).first { it.name == "Ana" }.payMethods)
        assertEquals(pay, mine(piso).payMethods)
    }

    @Test
    fun `regenerating the invite code queues the group without history`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val before = env.changeLog.observe(g).first().size
        env.groups.regenerateInviteCode(g)
        assertEquals("CODE1", env.groups.observeGroup(g).first()!!.inviteCode)
        assertEquals(before, env.changeLog.observe(g).first().size)
    }
}
