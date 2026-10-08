package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

private val LocalOwlsplitColors = staticCompositionLocalOf { LightOwlsplitColors }
private val LocalOwlsplitTypography = staticCompositionLocalOf<OwlsplitTypography> {
    error("Owlsplit typography requested outside OwlsplitTheme")
}

/** Access to the brand tokens: `Owlsplit.colors.accent`, `Owlsplit.type.amountDisplay`. */
object Owlsplit {
    val colors: OwlsplitColors
        @Composable @ReadOnlyComposable
        get() = LocalOwlsplitColors.current

    val type: OwlsplitTypography
        @Composable @ReadOnlyComposable
        get() = LocalOwlsplitTypography.current
}

/**
 * Brand theme. [fontFamily] defaults to the bundled Inter; tests may pass [FontFamily.Default]
 * where resource fonts cannot be loaded.
 */
@Composable
fun OwlsplitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkOwlsplitColors else LightOwlsplitColors
    val family = fontFamily ?: interFontFamily()
    CompositionLocalProvider(
        LocalOwlsplitColors provides colors,
        LocalOwlsplitTypography provides owlsplitTypography(family),
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = materialTypography(family),
            // Menus (extraSmall), fields, cards and dialogs (extraLarge) with the corners of the design system.
            shapes = Shapes(
                extraSmall = OwlsplitShapes.control,
                small = OwlsplitShapes.control,
                medium = OwlsplitShapes.container,
                large = OwlsplitShapes.container,
                extraLarge = OwlsplitShapes.dialog,
            ),
            content = content,
        )
    }
}

private fun OwlsplitColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = accentText,
        secondary = accent,
        onSecondary = onAccent,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        // Every container is the surface colour: depth comes from background vs surface (no shadows),
        // and no Material tonal tint on top of it.
        surfaceTint = surface,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceVariant,
        secondaryContainer = accentContainer,
        onSecondaryContainer = accentText,
        tertiary = accent,
        onTertiary = onAccent,
        // Snackbars: the opposite of the theme, as the system does.
        inverseSurface = textPrimary,
        inverseOnSurface = surface,
        inversePrimary = accentText,
        outline = textMuted,
        outlineVariant = divider,
        error = error,
        onError = onAccent,
    )
}
