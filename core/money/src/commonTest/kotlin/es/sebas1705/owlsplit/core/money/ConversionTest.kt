package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversionTest {
    private val eur = Currency.of("EUR")
    private val usd = Currency.of("USD")
    private val jpy = Currency.of("JPY")
    private val kwd = Currency.of("KWD")

    @Test
    fun `same number of decimals`() {
        // 10.00 USD at 0.92 → 9.20 EUR
        assertEquals(920L, Conversion.convert(1000, usd, eur, FxRate.parse("0.92")).truncated)
    }

    @Test
    fun `from a currency without decimals`() {
        // 1000 JPY at 0.0061 → 6.10 EUR
        assertEquals(610L, Conversion.convert(1000, jpy, eur, FxRate.parse("0.0061")).truncated)
    }

    @Test
    fun `to a currency without decimals`() {
        // 10.00 EUR at 163.5 → 1635 JPY
        assertEquals(1635L, Conversion.convert(1000, eur, jpy, FxRate.parse("163.5")).truncated)
    }

    @Test
    fun `to a currency with three decimals`() {
        // 10.00 EUR at 0.33 → 3.300 KWD
        assertEquals(3300L, Conversion.convert(1000, eur, kwd, FxRate.parse("0.33")).truncated)
    }

    @Test
    fun `truncates toward zero and exposes the remainder`() {
        // 1 cent at 0.5 → 0.5 cents: truncated 0, remainder is half the denominator.
        val half = Conversion.convert(1, usd, eur, FxRate.parse("0.5"))
        assertEquals(0L, half.truncated)
        assertEquals(half.denominator / 2, half.remainder)

        val negative = Conversion.convert(-1, usd, eur, FxRate.parse("0.5"))
        assertEquals(0L, negative.truncated)
        assertEquals(-negative.denominator / 2, negative.remainder)
    }
}
