package es.sebas1705.owlsplit.domain.ledger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DebtSimplifierTest {
    @Test
    fun `largest debtor pays largest creditor first`() {
        val balances = mapOf("ana" to 5_820L, "sebas" to 2_350L, "marta" to -1_170L, "luis" to -7_000L)
        assertEquals(
            listOf(
                SuggestedPayment("luis", "ana", 5_820),
                SuggestedPayment("luis", "sebas", 1_180),
                SuggestedPayment("marta", "sebas", 1_170),
            ),
            DebtSimplifier.simplify(balances, four),
        )
    }

    @Test
    fun `ties follow member order`() {
        val balances = mapOf("ana" to 100L, "sebas" to 100L, "marta" to -100L, "luis" to -100L)
        assertEquals(
            listOf(SuggestedPayment("marta", "ana", 100), SuggestedPayment("luis", "sebas", 100)),
            DebtSimplifier.simplify(balances, four),
        )
    }

    @Test
    fun `nothing to settle`() {
        assertTrue(DebtSimplifier.simplify(mapOf("ana" to 0L, "sebas" to 0L), four).isEmpty())
    }

    @Test
    fun `balances must sum to zero`() {
        assertFailsWith<IllegalArgumentException> { DebtSimplifier.simplify(mapOf("ana" to 1L), four) }
    }
}
