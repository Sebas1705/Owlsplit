package es.sebas1705.owlsplit.domain.usecases.fx

import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.repository.fx.FxQuote
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.usecases.groups.HomeData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertTotalTest {
    private val rates = object : FxRates {
        override suspend fun latest(from: String, to: String) =
            if (from == "USD") FxQuote(FxRate.parse("0.9"), "2026-10-07") else null
    }

    @Test
    fun otherCurrenciesAreConvertedOrKeptApart() = runTest {
        val home = HomeData(emptyList(), "EUR", total = 1_000, otherCurrencies = mapOf("USD" to 2_000L, "GBP" to -500L))
        assertEquals(
            ConvertedTotal(2_800, approximate = true, unconverted = mapOf("GBP" to -500L)),
            ConvertTotal(rates)(home),
        )
    }

    @Test
    fun aSingleCurrencyIsExact() = runTest {
        val home = HomeData(emptyList(), "EUR", total = 1_000, otherCurrencies = emptyMap())
        assertEquals(ConvertedTotal(1_000, approximate = false, unconverted = emptyMap()), ConvertTotal(rates)(home))
    }
}
