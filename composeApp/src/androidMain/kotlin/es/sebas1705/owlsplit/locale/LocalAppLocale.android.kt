package es.sebas1705.owlsplit.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    actual val current: String
        @Composable get() = LocalConfiguration.current.locales[0].toLanguageTag()

    @Composable
    actual infix fun provides(tag: String?): ProvidedValue<*> {
        // The activity's configuration keeps the device language: the override is a copy of it.
        val device = LocalConfiguration.current
        val locale = tag?.let(Locale::forLanguageTag) ?: device.locales[0]
        // Platform formatters (the date picker) read the default locale.
        Locale.setDefault(locale)
        val configuration = Configuration(device).apply { setLocale(locale) }
        return LocalConfiguration.provides(configuration)
    }
}
