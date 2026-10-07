package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NetCalculatorTest {
    @Test
    fun `one payer, equal split, base currency`() {
        val e = expense(4_860, payers = mapOf("ana" to 4_860L), split = equalAmong(ana, sebas, marta, luis))
        assertEquals(
            mapOf("ana" to 3_645L, "sebas" to -1_215L, "marta" to -1_215L, "luis" to -1_215L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `several payers`() {
        val e = expense(
            4_860,
            payers = mapOf("ana" to 3_000L, "sebas" to 1_860L),
            split = equalAmong(ana, sebas, marta, luis),
        )
        assertEquals(
            mapOf("ana" to 1_785L, "sebas" to 645L, "marta" to -1_215L, "luis" to -1_215L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `payers must add up to the amount`() {
        val e = expense(4_860, payers = mapOf("ana" to 4_000L), split = equalAmong(ana, sebas))
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.expenseNet(e, four, EUR) }
        assertEquals(SplitIssue.PayersSumMismatch(860), error.issue)
    }

    @Test
    fun `an invalid split is reported with the entry id`() {
        val e = expense(100, payers = mapOf("ana" to 100L), split = equalAmong(), id = "bad")
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.expenseNet(e, four, EUR) }
        assertEquals("bad", error.entryId)
        assertEquals(SplitIssue.NoBeneficiary, error.issue)
    }

    @Test
    fun `foreign currency converts to nets that still sum to zero`() {
        val rate = FxRate.parse("0.923456789")
        val e = expense(1_000, payers = mapOf("ana" to 1_000L), split = equalAmong(ana, sebas, marta), USD, rate)
        val net = NetCalculator.expenseNet(e, four, EUR)
        assertEquals(0L, net.values.sum())
        // Exact values: ana +666 USD cents → 615.02 EUR cents; sebas/marta −333/−333 USD cents → −307.51.
        assertTrue(abs(net.getValue("ana") - 615) <= 1)
        assertTrue(abs(net.getValue("sebas") + 308) <= 1)
        assertTrue(abs(net.getValue("marta") + 308) <= 1)
    }

    @Test
    fun `currency without decimals into euros`() {
        val e = expense(
            3_000,
            payers = mapOf("ana" to 3_000L),
            split = equalAmong(ana, sebas, marta),
            currency = JPY,
            rate = FxRate.parse("0.0061"),
        )
        // 3000 JPY = 18.30 EUR: ana is owed 2/3 = 12.20, the others owe 6.10 each.
        assertEquals(
            mapOf("ana" to 1_220L, "sebas" to -610L, "marta" to -610L),
            NetCalculator.expenseNet(e, four, EUR),
        )
    }

    @Test
    fun `transfer credits the payer and debits the receiver`() {
        val t = TransferEntry("t1", "luis", "ana", 4_650, EUR, FxRate.IDENTITY)
        assertEquals(mapOf("luis" to 4_650L, "ana" to -4_650L), NetCalculator.transferNet(t, four, EUR))
    }

    @Test
    fun `transfer to oneself is invalid`() {
        val t = TransferEntry("t1", "ana", "ana", 100, EUR, FxRate.IDENTITY)
        val error = assertFailsWith<InvalidEntryException> { NetCalculator.transferNet(t, four, EUR) }
        assertEquals(SplitIssue.SameMember, error.issue)
    }
}
