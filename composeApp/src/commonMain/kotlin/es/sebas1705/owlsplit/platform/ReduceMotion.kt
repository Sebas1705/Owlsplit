package es.sebas1705.owlsplit.platform

import androidx.compose.runtime.Composable

/** The system "reduce motion" setting, read on each composition of the root. */
@Composable
expect fun systemReduceMotion(): Boolean
