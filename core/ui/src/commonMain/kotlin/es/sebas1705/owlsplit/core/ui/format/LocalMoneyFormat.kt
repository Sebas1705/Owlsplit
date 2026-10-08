package es.sebas1705.owlsplit.core.ui.format

import androidx.compose.runtime.staticCompositionLocalOf

/** How amounts are written in the app's current language; the app root provides it. */
val LocalMoneyFormat = staticCompositionLocalOf { MoneyFormat.Spanish }

/** Spanish style for Spanish ("es", "es-MX"…), English style for anything else. */
fun moneyFormatFor(languageTag: String): MoneyFormat =
    if (languageTag.substringBefore('-').substringBefore('_').equals("es", ignoreCase = true)) {
        MoneyFormat.Spanish
    } else {
        MoneyFormat.English
    }
