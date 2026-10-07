package es.sebas1705.owlsplit.core.money

/** ISO 4217 currency with its number of minor-unit digits (EUR 2, JPY 0, KWD 3). */
data class Currency(val code: String, val decimals: Int) {
    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Invalid ISO 4217 code: $code" }
        require(decimals in 0..MAX_DECIMALS) { "Unsupported decimals $decimals for $code" }
    }

    companion object {
        const val MAX_DECIMALS = 3

        fun of(code: String): Currency =
            requireNotNull(CurrencyTable.byCode[code]) { "Unknown currency: $code" }

        fun isKnown(code: String): Boolean = code in CurrencyTable.byCode
    }
}
