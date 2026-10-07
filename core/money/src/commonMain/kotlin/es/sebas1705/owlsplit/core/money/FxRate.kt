package es.sebas1705.owlsplit.core.money

import kotlin.jvm.JvmInline

/**
 * Units of the target currency per one major unit of the source currency,
 * as a fixed-point number with 9 decimals (`scaled / 10⁹`).
 */
@JvmInline
value class FxRate(val scaled: Long) {
    init {
        require(scaled in 1..MAX_SCALED) { "Rate out of range: $scaled" }
    }

    fun toPlainString(): String {
        val integer = scaled / SCALE
        val fraction = (scaled % SCALE).toString().padStart(DECIMALS, '0').trimEnd('0')
        return if (fraction.isEmpty()) integer.toString() else "$integer.$fraction"
    }

    companion object {
        const val DECIMALS = 9
        const val SCALE = 1_000_000_000L

        /** Highest supported rate: 900 000 (keeps conversions inside Long, see Conversion). */
        const val MAX_SCALED = 900_000L * SCALE

        val IDENTITY = FxRate(SCALE)

        private val pattern = Regex("""^(\d{1,6})(?:[.,](\d{1,9}))?$""")

        /** Parses "0.923456789" or "0,92". Rejects zero, negatives and more than 9 decimals. */
        fun parse(decimal: String): FxRate {
            val match = requireNotNull(pattern.matchEntire(decimal.trim())) { "Invalid rate: '$decimal'" }
            val integer = match.groupValues[1].toLong()
            val fraction = match.groupValues[2].padEnd(DECIMALS, '0').toLong()
            val scaled = integer * SCALE + fraction
            require(scaled > 0) { "Rate must be positive: '$decimal'" }
            return FxRate(scaled)
        }
    }
}
