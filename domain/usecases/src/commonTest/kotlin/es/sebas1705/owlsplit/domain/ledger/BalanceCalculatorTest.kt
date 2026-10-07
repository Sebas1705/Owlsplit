package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceCalculatorTest {
    @Test
    fun `sums expenses and transfers per member`() {
        val dinner = expense(4_860, mapOf("sebas" to 4_860L), equalAmong(ana, sebas, marta, luis), id = "dinner")
        val taxi = expense(2_000, mapOf("ana" to 2_000L), equalAmong(ana, luis), id = "taxi")
        val payback = TransferEntry("t1", "luis", "sebas", 1_215, EUR, FxRate.IDENTITY)

        val balances = BalanceCalculator.balances(four, listOf(dinner, taxi), listOf(payback), EUR)

        assertEquals(
            mapOf("ana" to -215L, "sebas" to 2_430L, "marta" to -1_215L, "luis" to -1_000L),
            balances,
        )
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun `members without activity have a zero balance`() {
        val e = expense(1_000, mapOf("ana" to 1_000L), equalAmong(ana, sebas))
        assertEquals(0L, BalanceCalculator.balances(four, listOf(e), emptyList(), EUR).getValue("luis"))
    }

    @Test
    fun `empty group`() {
        assertEquals(
            mapOf("ana" to 0L, "sebas" to 0L, "marta" to 0L, "luis" to 0L),
            BalanceCalculator.balances(four, emptyList(), emptyList(), EUR),
        )
    }
}
