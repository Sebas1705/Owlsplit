package es.sebas1705.owlsplit.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"
    private val device: String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val local = staticCompositionLocalOf { device }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(tag: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (tag == null) defaults.removeObjectForKey(LANGUAGES_KEY) else defaults.setObject(listOf(tag), LANGUAGES_KEY)
        return local.provides(tag ?: device)
    }
}
