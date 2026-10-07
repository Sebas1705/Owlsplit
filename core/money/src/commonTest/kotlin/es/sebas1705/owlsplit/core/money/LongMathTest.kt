package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LongMathTest {
    @Test
    fun `small values truncate toward zero with signed remainder`() {
        assertEquals(QuotRem(10, 1), mulDivRem(7, 3, 2))
        assertEquals(QuotRem(-10, -1), mulDivRem(-7, 3, 2))
        assertEquals(QuotRem(-10, -1), mulDivRem(7, -3, 2))
        assertEquals(QuotRem(10, 1), mulDivRem(-7, -3, 2))
        assertEquals(QuotRem(0, 0), mulDivRem(0, 123, 7))
    }

    @Test
    fun `products beyond 64 bits are exact`() {
        // 10^12 * 9*10^17 = 9*10^29, far beyond Long; divided by 10^12.
        assertEquals(
            QuotRem(900_000_000_000_000_000, 0),
            mulDivRem(1_000_000_000_000, 900_000_000_000_000_000, 1_000_000_000_000),
        )
        // (2^62 + 1) * 6 / 4 = 6917529027641081857 remainder 2.
        assertEquals(QuotRem(6_917_529_027_641_081_857, 2), mulDivRem((1L shl 62) + 1, 6, 4))
    }

    @Test
    fun `quotient overflow is reported`() {
        assertFailsWith<ArithmeticException> { mulDivRem(Long.MAX_VALUE, 2, 1) }
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertFailsWith<IllegalArgumentException> { mulDivRem(1, 1, 0) }
        assertFailsWith<IllegalArgumentException> { mulDivRem(Long.MIN_VALUE, 1, 1) }
    }
}
