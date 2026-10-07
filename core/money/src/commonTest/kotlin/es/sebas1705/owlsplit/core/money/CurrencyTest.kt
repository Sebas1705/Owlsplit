package es.sebas1705.owlsplit.core.money

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrencyTest {
    @Test
    fun `known currencies carry their ISO 4217 decimals`() {
        assertEquals(2, Currency.of("EUR").decimals)
        assertEquals(0, Currency.of("JPY").decimals)
        assertEquals(3, Currency.of("KWD").decimals)
    }

    @Test
    fun `unknown currency is rejected`() {
        assertFailsWith<IllegalArgumentException> { Currency.of("XXX") }
        assertFalse(Currency.isKnown("XXX"))
        assertTrue(Currency.isKnown("USD"))
    }

    @Test
    fun `malformed codes are rejected`() {
        assertFailsWith<IllegalArgumentException> { Currency("eur", 2) }
        assertFailsWith<IllegalArgumentException> { Currency("EURO", 2) }
        assertFailsWith<IllegalArgumentException> { Currency("EUR", 4) }
    }
}
