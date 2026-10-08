package es.sebas1705.owlsplit.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    /** The device language, captured before the app overrides it. */
    private var device: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(tag: String?): ProvidedValue<*> {
        val base = device ?: Locale.getDefault().also { device = it }
        val locale = tag?.let(Locale::forLanguageTag) ?: base
        Locale.setDefault(locale)
        // A copy: the activity's own configuration stays untouched.
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
        return LocalConfiguration.provides(configuration)
    }
}
