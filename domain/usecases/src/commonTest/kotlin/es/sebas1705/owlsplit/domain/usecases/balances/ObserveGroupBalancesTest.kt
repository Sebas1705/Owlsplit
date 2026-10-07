package es.sebas1705.owlsplit.domain.usecases.balances

import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObserveGroupBalancesTest {
    private val meta = SyncMeta(1, 0, null, "u", false, SyncState.PENDING)
    private val group = Group("g", "Piso", "home", "green", "EUR", null, null, "C", listOf("u"), emptyList(), "u", 0, meta)

    private fun member(id: String, sort: Long) = Member(id, "g", id, null, 1, null, true, sort, meta)

    private fun expense(id: String, amount: Long, payer: String, among: List<String>, values: Map<String, Long>? = null) =
        Expense(
            id, "g", id, ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", amount, 1_000_000_000,
            mapOf(payer to amount), SplitType.EQUAL, values ?: among.associateWith { 1L }, null, null, "u", 0, meta,
        )

    @Test
    fun `balances and suggested payments update when expenses change`() = runTest {
        val expenses = MutableStateFlow(listOf(expense("e1", 3_000, "ana", listOf("ana", "luis", "marta"))))
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(group)),
            FakeMembers(MutableStateFlow(listOf(member("ana", 0), member("luis", 1), member("marta", 2)))),
            FakeExpenses(expenses),
            FakeTransfers(MutableStateFlow(emptyList())),
        )

        useCase("g").test {
            val first = awaitItem()!!
            assertEquals(mapOf("ana" to 2_000L, "luis" to -1_000L, "marta" to -1_000L), first.balances)
            assertEquals(
                listOf(SuggestedPayment("luis", "ana", 1_000), SuggestedPayment("marta", "ana", 1_000)),
                first.suggestedPayments,
            )

            expenses.value = expenses.value + expense("e2", 1_000, "luis", listOf("luis", "marta"))
            assertEquals(mapOf("ana" to 2_000L, "luis" to -500L, "marta" to -1_500L), awaitItem()!!.balances)
        }
    }

    @Test
    fun `invalid entries are skipped and reported instead of breaking the group`() = runTest {
        val broken = expense("bad", 1_000, "ana", emptyList(), values = mapOf("ghost" to 1L))
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(group)),
            FakeMembers(MutableStateFlow(listOf(member("ana", 0)))),
            FakeExpenses(MutableStateFlow(listOf(broken))),
            FakeTransfers(MutableStateFlow(emptyList())),
        )
        useCase("g").test {
            val result = awaitItem()!!
            assertEquals(mapOf("ana" to 0L), result.balances)
            assertEquals(listOf("bad"), result.invalidEntryIds)
        }
    }

    @Test
    fun `unknown group gives null`() = runTest {
        val useCase = ObserveGroupBalances(
            FakeGroups(MutableStateFlow(null)),
            FakeMembers(MutableStateFlow(emptyList())),
            FakeExpenses(MutableStateFlow(emptyList())),
            FakeTransfers(MutableStateFlow(emptyList())),
        )
        useCase("g").test { assertNull(awaitItem()) }
    }
}
