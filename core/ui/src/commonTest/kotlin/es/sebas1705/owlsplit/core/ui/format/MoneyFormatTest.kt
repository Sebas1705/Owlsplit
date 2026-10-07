package es.sebas1705.owlsplit.core.ui.format

import es.sebas1705.owlsplit.core.money.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    private val eur = Currency.of("EUR")
    private val jpy = Currency.of("JPY")
    private val kwd = Currency.of("KWD")

    @Test
    fun `spanish puts the symbol after and groups with dots`() {
        assertEquals("1.284,60 €", MoneyFormat.Spanish.format(128_460, eur))
        assertEquals("0,05 €", MoneyFormat.Spanish.format(5, eur))
        assertEquals("−12,00 €", MoneyFormat.Spanish.format(-1_200, eur))
        assertEquals("+23,50 €", MoneyFormat.Spanish.format(2_350, eur, showPlus = true))
    }

    @Test
    fun `english puts the symbol before and groups with commas`() {
        assertEquals("€1,284.60", MoneyFormat.English.format(128_460, eur))
        assertEquals("−€12.00", MoneyFormat.English.format(-1_200, eur))
    }

    @Test
    fun `currencies without or with three decimals`() {
        assertEquals("1.635 ¥", MoneyFormat.Spanish.format(1_635, jpy))
        assertEquals("3,300 KWD", MoneyFormat.Spanish.format(3_300, kwd))
    }

    @Test
    fun `parts split integer and fraction for big amounts`() {
        val parts = MoneyFormat.Spanish.parts(4_230, eur, showPlus = true)
        assertEquals(
            MoneyFormat.Parts(sign = "+", integer = "42", fraction = ",30", symbol = " €", symbolBefore = false),
            parts,
        )
    }

    @Test
    fun `zero has no sign`() {
        assertEquals("0,00 €", MoneyFormat.Spanish.format(0, eur, showPlus = true))
    }
}
