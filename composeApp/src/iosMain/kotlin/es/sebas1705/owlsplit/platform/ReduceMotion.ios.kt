package es.sebas1705.owlsplit.platform

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun systemReduceMotion(): Boolean = UIAccessibilityIsReduceMotionEnabled()
