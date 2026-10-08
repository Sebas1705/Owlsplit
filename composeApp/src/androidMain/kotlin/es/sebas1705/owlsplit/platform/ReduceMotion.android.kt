package es.sebas1705.owlsplit.platform

import androidx.compose.runtime.Composable

/** Android scales Compose animations with the system animator duration scale by itself. */
@Composable
actual fun systemReduceMotion(): Boolean = false
