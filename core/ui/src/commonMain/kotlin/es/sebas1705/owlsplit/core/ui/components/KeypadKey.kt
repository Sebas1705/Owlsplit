package es.sebas1705.owlsplit.core.ui.components

/** Keys of [AmountKeypad]. */
sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey
    data object Decimal : KeypadKey
    data object Backspace : KeypadKey
}
