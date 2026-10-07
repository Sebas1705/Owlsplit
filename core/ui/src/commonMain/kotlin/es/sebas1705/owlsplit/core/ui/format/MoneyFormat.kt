package es.sebas1705.owlsplit.core.ui.format

import es.sebas1705.owlsplit.core.money.Currency

/**
 * Locale-style money formatting without platform APIs. The minus sign is U+2212, as in the
 * design system; amounts are always grouped by thousands.
 */
data class MoneyFormat(
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val symbolBefore: Boolean,
) {
    /** Pieces of a formatted amount, so big displays can shrink the fraction. */
    data class Parts(
        val sign: String,
        val integer: String,
        val fraction: String,
        val symbol: String,
        val symbolBefore: Boolean,
    ) {
        override fun toString(): String =
            if (symbolBefore) "$sign$symbol$integer$fraction" else "$sign$integer$fraction$symbol"
    }

    fun parts(amountMinor: Long, currency: Currency, showPlus: Boolean = false): Parts {
        val sign = when {
            amountMinor < 0 -> MINUS
            amountMinor > 0 && showPlus -> "+"
            else -> ""
        }
        var unit = 1L
        repeat(currency.decimals) { unit *= TEN }
        val abs = if (amountMinor < 0) -amountMinor else amountMinor
        val integer = group((abs / unit).toString())
        val fraction = if (currency.decimals == 0) {
            ""
        } else {
            decimalSeparator + (abs % unit).toString().padStart(currency.decimals, '0')
        }
        val symbol = symbolOf(currency.code)
        return Parts(sign, integer, fraction, if (symbolBefore) symbol else " $symbol", symbolBefore)
    }

    fun format(amountMinor: Long, currency: Currency, showPlus: Boolean = false): String =
        parts(amountMinor, currency, showPlus).toString()

    private fun group(digits: String): String =
        digits.reversed().chunked(GROUP).joinToString(groupingSeparator.toString()).reversed()

    companion object {
        private const val MINUS = "−"
        private const val TEN = 10L
        private const val GROUP = 3

        val Spanish = MoneyFormat(decimalSeparator = ',', groupingSeparator = '.', symbolBefore = false)
        val English = MoneyFormat(decimalSeparator = '.', groupingSeparator = ',', symbolBefore = true)

        private val symbols = mapOf("EUR" to "€", "USD" to "$", "GBP" to "£", "JPY" to "¥")

        fun symbolOf(code: String): String = symbols[code] ?: code
    }
}
