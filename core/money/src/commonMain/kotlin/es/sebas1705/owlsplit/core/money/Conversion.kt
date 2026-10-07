package es.sebas1705.owlsplit.core.money

/**
 * A converted amount: [truncated] minor units (toward zero) plus [remainder] / [denominator]
 * of a unit. Conversions of one expense share the denominator, so remainders are comparable.
 */
data class Converted(val truncated: Long, val remainder: Long, val denominator: Long)

object Conversion {
    /** Converts [amountMinor] of [from] into minor units of [to] at [rate] (to per one unit of from). */
    fun convert(amountMinor: Long, from: Currency, to: Currency, rate: FxRate): Converted {
        // amount/10^dFrom major units × rate.scaled/10^9 × 10^dTo minor units of `to`.
        // rate.scaled ≤ 9·10^14 and 10^dTo ≤ 10^3, so the factor stays below 9.2·10^18.
        val factor = rate.scaled * pow10(to.decimals)
        val denominator = FxRate.SCALE * pow10(from.decimals)
        val qr = mulDivRem(amountMinor, factor, denominator)
        return Converted(qr.quotient, qr.remainder, denominator)
    }
}
