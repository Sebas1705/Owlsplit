package es.sebas1705.owlsplit.core.ui.format

private val DECIMAL = Regex("""^(\d*)(?:[.,](\d*))?$""")

private fun pow10(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * BASE }

private const val BASE = 10L

private fun fractionUnits(digits: String, decimals: Int): Long =
    digits.padEnd(decimals, '0').take(decimals).ifEmpty { "0" }.toLong()

/** Parses "12,5" or "12.5" into units with [decimals] digits; empty is 0; null if it is not a number. */
fun parseDecimal(text: String, decimals: Int): Long? {
    val match = DECIMAL.matchEntire(text.trim()) ?: return null
    val whole = match.groupValues[1].ifEmpty { "0" }.toLongOrNull()
    val fraction = match.groupValues[2]
    return if (whole == null || fraction.length > decimals) null else whole * pow10(decimals) + fractionUnits(fraction, decimals)
}

/** The inverse of [parseDecimal], without trailing zeros ("12,5", "33,33", "2"). */
fun formatDecimal(units: Long, decimals: Int, separator: Char): String {
    val factor = pow10(decimals)
    val fraction = (units % factor).toString().padStart(decimals, '0').trimEnd('0')
    return (units / factor).toString() + if (fraction.isEmpty()) "" else "$separator$fraction"
}
