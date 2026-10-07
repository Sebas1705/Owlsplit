package es.sebas1705.owlsplit.data.repository

import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.SplitType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseRepositoryTest {
    private val group = GroupInput("Piso", "home", "green", "EUR")

    private fun dinner(payer: String, members: List<String>, amount: Long = 4_860) = ExpenseInput(
        title = "Cena", category = ExpenseCategory.FOOD, note = null, date = LocalDate(2026, 7, 12),
        currency = "EUR", amountMinor = amount, fxRateScaled = 1_000_000_000,
        payers = mapOf(payer to amount), splitType = SplitType.EQUAL, splitValues = members.associateWith { 1L },
    )

    @Test
    fun `an expense round-trips with payers and split values`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))

        val id = env.expenses.create(g, dinner(ana, listOf(ana, luis)))

        env.expenses.observeExpenses(g).test {
            val e = awaitItem().single()
            assertEquals(id, e.id)
            assertEquals(mapOf(ana to 4_860L), e.payers)
            assertEquals(mapOf(ana to 1L, luis to 1L), e.splitValues)
            assertEquals(LocalDate(2026, 7, 12), e.date)
        }
    }

    @Test
    fun `editing replaces payers and records the diff`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val id = env.expenses.create(g, dinner(ana, listOf(ana, luis)))

        env.expenses.update(id, dinner(luis, listOf(ana, luis), amount = 5_000))

        val e = env.expenses.get(id)!!
        assertEquals(mapOf(luis to 5_000L), e.payers)
        env.changeLog.observe(g).test {
            val update = awaitItem().first { it.action == ChangeAction.UPDATED && it.entityId == id }
            assertEquals(setOf("amountMinor", "payers"), update.diff.map { it.field }.toSet())
            assertEquals("4860", update.diff.first { it.field == "amountMinor" }.before)
        }
    }

    @Test
    fun `deleted expenses disappear from the list and can be restored`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val id = env.expenses.create(g, dinner(ana, listOf(ana)))

        env.expenses.delete(id)
        env.expenses.observeExpenses(g).test { assertTrue(awaitItem().isEmpty()) }
        assertTrue(env.expenses.get(id)!!.meta.deleted)

        env.expenses.restore(id)
        env.expenses.observeExpenses(g).test { assertEquals(id, awaitItem().single().id) }
    }

    @Test
    fun `unknown expense is null`() = runTest {
        assertNull(TestEnv(this).expenses.get("nope"))
    }
}
