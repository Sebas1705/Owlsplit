package es.sebas1705.owlsplit

import androidx.compose.runtime.Composable

/** Light or dark system bar icons to match the app theme, not the system one. */
@Composable
expect fun SystemBarsAppearance(darkTheme: Boolean)
