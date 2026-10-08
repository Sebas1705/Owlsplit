package es.sebas1705.owlsplit.domain.usecases.fx

import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import es.sebas1705.owlsplit.domain.usecases.groups.HomeData

/** A rate as the screens see it: [rate] of the target per unit of the source, ECB [day], and [stale] when offline. */
data class RateQuote(val rate: FxRate, val day: String, val stale: Boolean)

/** The rate to freeze in a new expense; null when the user has to type it. */
class GetFxRate(private val rates: FxRates) {
    suspend operator fun invoke(from: String, to: String): RateQuote? =
        rates.latest(from, to)?.let { RateQuote(it.rate, it.day, it.stale) }
}

data class ConvertedTotal(
    /** In the preferred currency. */
    val total: Long,
    /** Some groups were converted at today's rate: the figure moves with the market. */
    val approximate: Boolean,
    /** Balances that could not be converted (no rate), per currency. */
    val unconverted: Map<String, Long>,
)

/** Inicio's total: groups in other currencies converted at the latest available rate (decision 8 of P9b). */
class ConvertTotal(private val rates: FxRates) {
    suspend operator fun invoke(home: HomeData): ConvertedTotal {
        val target = Currency.of(home.preferredCurrency)
        var total = home.total
        val unconverted = mutableMapOf<String, Long>()
        home.otherCurrencies.forEach { (code, amount) ->
            val quote = rates.latest(code, home.preferredCurrency)
            if (quote == null) {
                unconverted[code] = amount
            } else {
                total += Conversion.convert(amount, Currency.of(code), target, quote.rate).truncated
            }
        }
        return ConvertedTotal(total, approximate = home.otherCurrencies.size > unconverted.size, unconverted)
    }
}
