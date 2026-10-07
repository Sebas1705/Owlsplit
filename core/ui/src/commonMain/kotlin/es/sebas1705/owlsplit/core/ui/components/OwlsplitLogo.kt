package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit

/** Geometric owl: accent rounded square, two white rings for eyes and an amber beak. */
@Composable
fun OwlsplitLogo(modifier: Modifier = Modifier, size: Dp = 48.dp, contentDescription: String? = null) {
    val accent = Owlsplit.colors.accent
    val beak = Owlsplit.colors.brandBeak
    Canvas(
        modifier
            .size(size)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        val unit = this.size.width / LOGO_GRID
        drawRoundRect(accent, cornerRadius = CornerRadius(CORNER * unit))
        val ring = Stroke(width = RING_STROKE * unit)
        drawCircle(
            Color.White,
            radius = EYE_RADIUS * unit,
            center = Offset(LEFT_EYE_X * unit, EYE_Y * unit),
            style = ring
        )
        drawCircle(
            Color.White,
            radius = EYE_RADIUS * unit,
            center = Offset(RIGHT_EYE_X * unit, EYE_Y * unit),
            style = ring
        )
        val beakPath = Path().apply {
            moveTo(BEAK_LEFT * unit, BEAK_TOP * unit)
            lineTo(BEAK_RIGHT * unit, BEAK_TOP * unit)
            lineTo(LOGO_GRID / 2 * unit, BEAK_TIP * unit)
            close()
        }
        drawPath(beakPath, beak)
    }
}

// Proportions on a 24-unit grid, as in the mockup.
private const val LOGO_GRID = 24f
private const val CORNER = 7f
private const val RING_STROKE = 1.8f
private const val EYE_RADIUS = 3.2f
private const val LEFT_EYE_X = 8.5f
private const val RIGHT_EYE_X = 15.5f
private const val EYE_Y = 11f
private const val BEAK_LEFT = 10.8f
private const val BEAK_RIGHT = 13.2f
private const val BEAK_TOP = 15.6f
private const val BEAK_TIP = 18f
