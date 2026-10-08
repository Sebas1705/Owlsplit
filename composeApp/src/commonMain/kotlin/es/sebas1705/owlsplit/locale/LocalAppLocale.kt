package es.sebas1705.owlsplit.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * The language the app's resources resolve in. Compose Multiplatform reads the platform
 * locale, so overriding it means changing that locale (the pattern of its localisation docs).
 */
expect object LocalAppLocale {
    /** The language tag in use ("es", "en-GB"…). */
    val current: String
        @Composable get

    /** [tag] null goes back to the device language. */
    @Composable
    infix fun provides(tag: String?): ProvidedValue<*>
}
