package es.sebas1705.owlsplit.core.ui.components

/** Opens the system share sheet with [text]; false where sharing is not available (copy instead). */
fun interface TextSharer {
    fun share(text: String): Boolean
}
