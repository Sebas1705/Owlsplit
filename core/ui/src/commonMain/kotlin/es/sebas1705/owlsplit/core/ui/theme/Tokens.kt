package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** Colour tokens from docs/DESIGN_SYSTEM.md. Components read these, never literals. */
@Immutable
data class OwlsplitColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    /** Raised option of a segmented control: lighter than its track in both themes. */
    val segmentSelected: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val accentContainer: Color,
    val positive: Color,
    val positiveFill: Color,
    val negativeFill: Color,
    val warning: Color,
    val error: Color,
    val brandBeak: Color,
    val isDark: Boolean,
)

val LightOwlsplitColors = OwlsplitColors(
    background = Color(0xFFF2F3F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4E6EE),
    segmentSelected = Color(0xFFFFFFFF),
    divider = Color(0xFFECEEF3),
    textPrimary = Color(0xFF0B0C10),
    textSecondary = Color(0xFF5B5F6B),
    textMuted = Color(0xFF696D78), // AA (4.5:1) on background and surface: placeholders and captions are text
    accent = Color(0xFF3B4BF5),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF3B4BF5),
    accentContainer = Color(0xFFE8EBFF),
    positive = Color(0xFF087A54),
    positiveFill = Color(0xFF0E9F6E),
    negativeFill = Color(0xFF0B0C10),
    warning = Color(0xFF9A5B00),
    error = Color(0xFFC4372B),
    brandBeak = Color(0xFFE8B04B),
    isDark = false,
)

val DarkOwlsplitColors = OwlsplitColors(
    background = Color(0xFF0B0C10),
    surface = Color(0xFF16181F),
    surfaceVariant = Color(0xFF1C1E26),
    segmentSelected = Color(0xFF2C303B),
    divider = Color(0xFF22252E),
    textPrimary = Color(0xFFF5F6FA),
    textSecondary = Color(0xFF8A8E99),
    textMuted = Color(0xFF80848F), // AA (4.5:1) on background and surface
    accent = Color(0xFF3B4BF5),
    onAccent = Color(0xFFFFFFFF),
    accentText = Color(0xFF8C96FF),
    accentContainer = Color(0xFF232750),
    positive = Color(0xFF34C38F),
    positiveFill = Color(0xFF34C38F),
    negativeFill = Color(0xFFF5F6FA),
    warning = Color(0xFFF2B84B),
    error = Color(0xFFFF7A6E),
    brandBeak = Color(0xFFE8B04B),
    isDark = true,
)

/** The eight group accents. Only the group icon is tinted, never a whole screen. */
enum class GroupAccent(private val lightContainer: Color, private val content: Color) {
    BLUE(Color(0xFFE8EBFF), Color(0xFF3B4BF5)),
    ORANGE(Color(0xFFFFF1E5), Color(0xFFD9661A)),
    VIOLET(Color(0xFFF3E8FF), Color(0xFF8B3FD9)),
    GREEN(Color(0xFFE3F5EE), Color(0xFF087A54)),
    PINK(Color(0xFFFDE8F0), Color(0xFFC2306A)),
    AMBER(Color(0xFFFFF4D6), Color(0xFF9A6400)),
    CYAN(Color(0xFFE0F4FA), Color(0xFF0A7893)),
    GRAPHITE(Color(0xFFECEEF3), Color(0xFF3A3E4A)),
    ;

    fun container(colors: OwlsplitColors): Color =
        if (colors.isDark) content.copy(alpha = DARK_CONTAINER_ALPHA).compositeOver(colors.surface) else lightContainer

    fun content(colors: OwlsplitColors): Color =
        if (colors.isDark) lerp(content, Color.White, DARK_CONTENT_LIGHTEN) else content

    private companion object {
        const val DARK_CONTAINER_ALPHA = 0.18f
        const val DARK_CONTENT_LIGHTEN = 0.35f
    }
}
