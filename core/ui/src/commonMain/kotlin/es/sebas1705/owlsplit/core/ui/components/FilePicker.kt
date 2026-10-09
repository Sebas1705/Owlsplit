package es.sebas1705.owlsplit.core.ui.components

/** A text file chosen by the person: its name (without folders) and its contents. */
data class PickedFile(val name: String, val text: String)

/** Opens the system file picker; null when cancelled or the file cannot be read as text. */
fun interface FilePicker {
    suspend fun pickText(): PickedFile?
}
