package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    private val eur = Currency.of("EUR")
    private val usd = Currency.of("USD")

    @Test
    fun `adds and subtracts in the same currency`() {
        assertEquals(Money(1500, eur), Money(1000, eur) + Money(500, eur))
        assertEquals(Money(-250, eur), Money(250, eur) - Money(500, eur))
        assertEquals(Money(-7, eur), -Money(7, eur))
        assertTrue(Money.zero(eur).isZero)
    }

    @Test
    fun `mixing currencies is a programming error`() {
        assertFailsWith<CurrencyMismatchException> { Money(1, eur) + Money(1, usd) }
    }

    @Test
    fun `amounts beyond the safe range are rejected`() {
        assertFailsWith<IllegalArgumentException> { Money(Money.MAX_ABS + 1, eur) }
        assertFailsWith<IllegalArgumentException> { Money(Money.MAX_ABS, eur) + Money(1, eur) }
    }
}
