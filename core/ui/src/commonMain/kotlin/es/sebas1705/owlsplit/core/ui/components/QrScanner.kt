package es.sebas1705.owlsplit.core.ui.components

/** Reads a QR code with the camera; the platform provides it (Android: Google code scanner). */
interface QrScanner {
    val isAvailable: Boolean

    /** The text read, or null when cancelled or it failed. */
    suspend fun scan(): String?
}
