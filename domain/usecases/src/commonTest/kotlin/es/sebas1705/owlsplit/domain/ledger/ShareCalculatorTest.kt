package es.sebas1705.owlsplit.domain.ledger

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareCalculatorTest {
    private fun ok(result: SplitResult) = (result as SplitResult.Ok).shares
    private fun issue(result: SplitResult) = (result as SplitResult.Invalid).issue

    @Test
    fun `equal split follows member order, not map order`() {
        val split = SplitSpec(SplitType.EQUAL, mapOf("marta" to 1L, "ana" to 1L, "sebas" to 1L))
        val shares = ok(ShareCalculator.shares(1000, split, listOf(marta, sebas, ana)))
        assertEquals(mapOf("ana" to 334L, "sebas" to 333L, "marta" to 333L), shares)
    }

    @Test
    fun `members with value zero are left out of an equal split`() {
        val split = SplitSpec(SplitType.EQUAL, mapOf("ana" to 1L, "sebas" to 0L, "marta" to 1L))
        assertEquals(mapOf("ana" to 500L, "marta" to 500L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `weights`() {
        val split = SplitSpec(SplitType.WEIGHTS, mapOf("ana" to 2L, "sebas" to 1L))
        assertEquals(mapOf("ana" to 667L, "sebas" to 333L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `percentages in hundredths`() {
        val split = SplitSpec(SplitType.PERCENT, mapOf("ana" to 7_500L, "sebas" to 2_500L))
        assertEquals(mapOf("ana" to 750L, "sebas" to 250L), ok(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `percentages must add up to 100`() {
        val split = SplitSpec(SplitType.PERCENT, mapOf("ana" to 5_000L, "sebas" to 4_000L))
        assertEquals(SplitIssue.PercentSumMismatch(1_000), issue(ShareCalculator.shares(1000, split, four)))
    }

    @Test
    fun `exact amounts`() {
        val split = SplitSpec(SplitType.EXACT, mapOf("ana" to 1_000L, "luis" to 3_860L))
        assertEquals(mapOf("ana" to 1_000L, "luis" to 3_860L), ok(ShareCalculator.shares(4_860, split, four)))
    }

    @Test
    fun `exact amounts report what is missing`() {
        val split = SplitSpec(SplitType.EXACT, mapOf("ana" to 1_000L, "luis" to 3_540L))
        assertEquals(SplitIssue.ExactSumMismatch(320), issue(ShareCalculator.shares(4_860, split, four)))
    }

    @Test
    fun `invalid splits`() {
        assertEquals(
            SplitIssue.NoBeneficiary,
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.EQUAL, mapOf("ana" to 0L)), four)),
        )
        assertEquals(
            SplitIssue.UnknownMember("zoe"),
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.EQUAL, mapOf("zoe" to 1L)), four)),
        )
        assertEquals(
            SplitIssue.NegativeValue,
            issue(ShareCalculator.shares(1000, SplitSpec(SplitType.WEIGHTS, mapOf("ana" to -1L)), four)),
        )
        assertEquals(SplitIssue.AmountOutOfRange, issue(ShareCalculator.shares(0, equalAmong(ana), four)))
    }
}
