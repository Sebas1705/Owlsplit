package es.sebas1705.owlsplit.core.ui.components

/** Opens the system share sheet with a file made in memory; false where it cannot be shared. */
fun interface FileSharer {
    fun share(fileName: String, mimeType: String, bytes: ByteArray): Boolean
}
