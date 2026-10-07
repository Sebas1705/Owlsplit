package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FxRateTest {
    @Test
    fun `parses decimal strings with dot or comma`() {
        assertEquals(923_456_789L, FxRate.parse("0.923456789").scaled)
        assertEquals(1_500_000_000L, FxRate.parse("1,5").scaled)
        assertEquals(FxRate.IDENTITY, FxRate.parse("1"))
        assertEquals(163_500_000_000L, FxRate.parse("163.5").scaled)
    }

    @Test
    fun `rejects invalid rates`() {
        for (bad in listOf("", "abc", "0", "0.000000000", "-1", "1.1234567891", "1000000")) {
            assertFailsWith<IllegalArgumentException>(bad) { FxRate.parse(bad) }
        }
    }

    @Test
    fun `formats without trailing zeros`() {
        assertEquals("1", FxRate.IDENTITY.toPlainString())
        assertEquals("1.5", FxRate(1_500_000_000).toPlainString())
        assertEquals("0.923456789", FxRate(923_456_789).toPlainString())
        assertEquals("0.0061", FxRate(6_100_000).toPlainString())
    }
}
