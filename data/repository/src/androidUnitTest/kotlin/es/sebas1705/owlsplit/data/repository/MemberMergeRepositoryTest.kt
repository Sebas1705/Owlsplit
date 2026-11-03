package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.TransferInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberMergeRepositoryTest {
    private val group = GroupInput("Piso", "home", "green", "EUR")

    private fun dinner(payer: String, who: List<String>) = ExpenseInput(
        "Cena", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 6_000, 1_000_000_000,
        mapOf(payer to 6_000L), SplitType.EQUAL, who.associateWith { 1L },
    )

    @Test
    fun `merging moves expenses and payments to the member kept and deletes the other last`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembers(g).first().single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val luis2 = env.members.add(g, MemberInput("Luis (Bizum)"))
        val e = env.expenses.create(g, dinner(luis2, listOf(ana, luis, luis2)))
        val t = env.transfers.create(g, TransferInput(luis2, luis, 100, "EUR", 1_000_000_000, LocalDate(2026, 7, 13)))
        env.db.outboxQueries.selectAll().executeAsList().forEach { env.db.outboxQueries.deleteById(it.id) }

        assertEquals(luis, env.members.merge(luis2, luis))

        val expense = env.expenses.get(e)!!
        assertEquals(mapOf(luis to 6_000L), expense.payers)
        assertEquals(SplitType.WEIGHTS, expense.splitType)
        assertEquals(mapOf(ana to 1L, luis to 2L), expense.splitValues)
        // Paying yourself disappears.
        assertTrue(env.transfers.observeTransfers(g).first().isEmpty())
        assertEquals(setOf(ana, luis), env.members.observeMembers(g).first().map { it.id }.toSet())
        val order = env.outbox().map { OutboxType.valueOf(it.entity_type) to it.entity_id }.filter { it.first != OutboxType.CHANGE_LOG }
        assertEquals(listOf(OutboxType.EXPENSE to e, OutboxType.TRANSFER to t), order.take(2))
        assertEquals(OutboxType.MEMBER to luis2, order.last())
        val history = env.changeLog.observe(g).first().first { it.entityId == luis2 }
        assertEquals(ChangeAction.DELETED, history.action)
        assertTrue(history.diff.any { it.field == "mergedInto" && it.after == luis })
    }

    @Test
    fun `the member with an account is kept and two accounts cannot be merged`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(group, "Ana")
        val ana = env.members.observeMembers(g).first().single().id
        val luis = env.members.add(g, MemberInput("Luis"))

        assertEquals(ana, env.members.merge(ana, luis))
        assertNull(env.members.merge(ana, ana))
        val kept = env.members.observeMembers(g).first().single()
        assertEquals(ana, kept.id)
    }
}
