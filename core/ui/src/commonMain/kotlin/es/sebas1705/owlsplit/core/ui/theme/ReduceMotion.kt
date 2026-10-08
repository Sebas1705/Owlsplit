package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The system's "reduce motion" setting, provided by the app root. Android already scales Compose
 * animations with its animator duration scale; iOS does not, so animations check this.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
