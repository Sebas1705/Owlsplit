package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import kotlin.math.roundToLong

private const val FRACTION_SCALE = 0.6f
private const val ROLL_MILLIS = 300

/**
 * An amount where the integer part leads and the fraction shrinks to 60 %. When [animate] is
 * true a change rolls from the previous value; the final frame is always the exact amount.
 * [spokenDescription] replaces the digits for screen readers ("Te deben veintitrés euros con cincuenta").
 */
@Composable
fun AmountText(
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    showPlus: Boolean = false,
    animate: Boolean = true,
    spokenDescription: String? = null,
) {
    val shown = if (animate) rememberRollingAmount(amountMinor) else amountMinor
    val parts = format.parts(shown, currency, showPlus)
    val small = SpanStyle(fontSize = style.fontSize * FRACTION_SCALE)
    val text = buildAnnotatedString {
        append(parts.sign)
        if (parts.symbolBefore) append(parts.symbol)
        append(parts.integer)
        withStyle(small) {
            append(parts.fraction)
            if (!parts.symbolBefore) append(parts.symbol)
        }
    }
    Text(
        text,
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.semantics { if (spokenDescription != null) contentDescription = spokenDescription },
    )
}

@Composable
private fun rememberRollingAmount(target: Long): Long {
    var shown by remember { mutableLongStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        val from = shown
        if (from == target) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ROLL_MILLIS, easing = FastOutSlowInEasing)) {
            shown = from + ((target - from) * value.toDouble()).roundToLong()
        }
        shown = target
    }
    return shown
}
