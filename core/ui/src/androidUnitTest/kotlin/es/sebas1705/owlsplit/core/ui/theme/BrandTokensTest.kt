package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Test
import java.io.File
import org.junit.Assert.assertEquals

/**
 * The app's colours are the brand's: brand/tokens.json is the source the web and the app icons
 * are generated from (scripts/brand.mjs), so Tokens.kt must say the same.
 */
class BrandTokensTest {
    private val json = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "brand/tokens.json") }
        .first { it.exists() }
        .readText()

    /** The `"name": "#RRGGBB"` pairs of one theme block of tokens.json. */
    private fun theme(name: String): Map<String, String> {
        val block = json.substringAfter("\"$name\": {").substringBefore("}")
        return Regex("\"(\\w+)\": \"(#[0-9A-Fa-f]{6})\"").findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2].uppercase() }
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

    private fun OwlsplitColors.byName() = mapOf(
        "background" to background, "surface" to surface, "surfaceVariant" to surfaceVariant,
        "segmentSelected" to segmentSelected, "divider" to divider, "textPrimary" to textPrimary,
        "textSecondary" to textSecondary, "textMuted" to textMuted, "accent" to accent, "onAccent" to onAccent,
        "accentText" to accentText, "accentContainer" to accentContainer, "positive" to positive,
        "positiveFill" to positiveFill, "negativeFill" to negativeFill, "warning" to warning, "error" to error,
        "brandBeak" to brandBeak,
    ).mapValues { it.value.hex() }

    @Test
    fun lightColoursMatchTheBrand() = assertEquals(theme("light"), LightOwlsplitColors.byName())

    @Test
    fun darkColoursMatchTheBrand() = assertEquals(theme("dark"), DarkOwlsplitColors.byName())
}
