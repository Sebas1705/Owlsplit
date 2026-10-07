package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
