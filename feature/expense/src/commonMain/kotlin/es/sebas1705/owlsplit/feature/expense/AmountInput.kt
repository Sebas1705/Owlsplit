package es.sebas1705.owlsplit.feature.expense

import es.sebas1705.owlsplit.core.ui.components.KeypadKey

/**
 * What the user typed on the amount keypad, kept as digits so "12," and "12,5" show as typed.
 * [decimals] comes from the currency (EUR 2, JPY 0).
 */
data class AmountInput(val integer: String = "", val fraction: String? = null, val decimals: Int = 2) {

    fun apply(key: KeypadKey): AmountInput = when (key) {
        is KeypadKey.Digit -> digit(key.value)
        KeypadKey.Decimal -> decimalPoint()
        KeypadKey.Backspace -> backspace()
    }

    private fun digit(value: Int): AmountInput = when {
        fraction != null -> if (fraction.length < decimals) copy(fraction = fraction + value) else this
        integer.length >= MAX_INTEGER_DIGITS -> this
        integer == "0" || integer.isEmpty() -> copy(integer = "$value")
        else -> copy(integer = integer + value)
    }

    private fun decimalPoint(): AmountInput =
        if (decimals == 0 || fraction != null) this else copy(integer = integer.ifEmpty { "0" }, fraction = "")

    private fun backspace(): AmountInput = when {
        fraction == null -> copy(integer = integer.dropLast(1))
        fraction.isEmpty() -> copy(fraction = null)
        else -> copy(fraction = fraction.dropLast(1))
    }

    val minorUnits: Long
        get() = (integer.toLongOrNull() ?: 0L) * pow10(decimals) + fractionUnits(fraction.orEmpty(), decimals)

    /** The amount as typed, with the locale separator; "0" when empty. */
    fun text(decimalSeparator: Char): String =
        integer.ifEmpty { "0" } + (fraction?.let { "$decimalSeparator$it" } ?: "")

    companion object {
        /** 10¹² minor units fit in 13 digits with two decimals: keep the integer part below that. */
        const val MAX_INTEGER_DIGITS = 10

        fun of(minor: Long, decimals: Int): AmountInput {
            if (minor <= 0) return AmountInput(decimals = decimals)
            val factor = pow10(decimals)
            val cents = minor % factor
            val fraction = if (cents == 0L) null else cents.toString().padStart(decimals, '0').trimEnd('0')
            return AmountInput((minor / factor).toString(), fraction, decimals)
        }
    }
}

private const val BASE = 10L
private val DECIMAL = Regex("""^(\d*)(?:[.,](\d*))?$""")

internal fun pow10(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * BASE }

private fun fractionUnits(digits: String, decimals: Int): Long =
    digits.padEnd(decimals, '0').take(decimals).ifEmpty { "0" }.toLong()

/** Parses "12,5" or "12.5" into units with [decimals] digits; empty is 0; null if it is not a number. */
internal fun parseDecimal(text: String, decimals: Int): Long? {
    val match = DECIMAL.matchEntire(text.trim()) ?: return null
    val whole = match.groupValues[1].ifEmpty { "0" }.toLongOrNull()
    val fraction = match.groupValues[2]
    return if (whole == null || fraction.length > decimals) null else whole * pow10(decimals) + fractionUnits(fraction, decimals)
}

/** The inverse of [parseDecimal], without trailing zeros ("12,5", "33,33", "2"). */
internal fun formatDecimal(units: Long, decimals: Int, separator: Char): String {
    val factor = pow10(decimals)
    val fraction = (units % factor).toString().padStart(decimals, '0').trimEnd('0')
    return (units / factor).toString() + if (fraction.isEmpty()) "" else "$separator$fraction"
}
