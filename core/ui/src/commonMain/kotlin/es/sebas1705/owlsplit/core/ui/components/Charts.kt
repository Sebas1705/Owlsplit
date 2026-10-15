package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitColors
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** One part of a whole: its [value] (any unit, only proportions matter) and its [color]. */
data class ChartSlice(val value: Float, val color: Color)

/**
 * Colours for the parts of a chart, in order, from the group accents (distinct in both themes). Past
 * [MAX_CHART_COLORS] parts a chart should merge the rest into one, drawn in [chartRestColor].
 */
fun chartColors(colors: OwlsplitColors): List<Color> =
    CHART_ACCENTS.map { it.content(colors) }

fun chartRestColor(colors: OwlsplitColors): Color = GroupAccent.GRAPHITE.content(colors).copy(alpha = REST_ALPHA)

const val MAX_CHART_COLORS = 6

private val CHART_ACCENTS = listOf(
    GroupAccent.BLUE,
    GroupAccent.ORANGE,
    GroupAccent.VIOLET,
    GroupAccent.GREEN,
    GroupAccent.PINK,
    GroupAccent.CYAN,
)

/** Grows from nothing the first time it is shown (instant in screenshots, which wait for idle). */
@Composable
private fun rememberGrowth(key: Any?): Float {
    val growth = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { growth.animateTo(1f, tween(GROW_MILLIS, easing = FastOutSlowInEasing)) }
    return growth.value
}

/**
 * A ring split in [slices], with [center] inside (typically the total). Decorative: the legend next to
 * it carries the numbers for screen readers.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 18.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val track = Owlsplit.colors.surfaceVariant
    val growth = rememberGrowth(slices)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val stroke = thickness.toPx()
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            drawArc(track, 0f, FULL_TURN, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.count { it.value > 0 } > 1) GAP_DEGREES else 0f
            var start = START_ANGLE
            slices.filter { it.value > 0 }.forEach { slice ->
                val sweep = slice.value / total * FULL_TURN * growth
                drawArc(
                    slice.color,
                    start + gap / 2,
                    (sweep - gap).coerceAtLeast(MIN_SWEEP),
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        center()
    }
}

/** A thin bar split in [slices], each as wide as its share of the whole. Decorative, like [DonutChart]. */
@Composable
fun StackedBar(slices: List<ChartSlice>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val shown = slices.filter { it.value > 0 }
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(Owlsplit.colors.surfaceVariant))
        }
        shown.forEach { Box(Modifier.weight(it.value).fillMaxHeight().background(it.color)) }
    }
}

/** A column of a [ColumnChart]: its [label] under it and what a screen reader says for it. */
data class ChartColumn(val label: String, val value: Long, val description: String)

/**
 * Vertical columns as tall as their share of the largest, the [selected] one in the accent and the
 * others faded; tapping one selects it. Past [MAX_FITTING_COLUMNS] they scroll, starting at the end.
 */
@Composable
fun ColumnChart(
    columns: List<ChartColumn>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val largest = columns.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: return
    val growth = rememberGrowth(columns)
    val scrolls = columns.size > MAX_FITTING_COLUMNS
    val scroll = rememberScrollState()
    LaunchedEffect(columns.size) { if (scrolls) scroll.scrollTo(scroll.maxValue) }
    Row(
        modifier.fillMaxWidth().then(if (scrolls) Modifier.horizontalScroll(scroll) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Bottom,
    ) {
        columns.forEachIndexed { index, column ->
            val isSelected = index == selected
            Column(
                (if (scrolls) Modifier.width(COLUMN_WIDTH) else Modifier.weight(1f))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(index) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = column.description
                        this.selected = isSelected
                        role = Role.Tab
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                    val fraction = (column.value.toFloat() / largest * growth).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(BAR_WIDTH)
                            .widthIn(max = MAX_BAR_WIDTH)
                            .fillMaxHeight(if (column.value > 0) fraction.coerceAtLeast(MIN_COLUMN) else MIN_COLUMN)
                            .background(
                                if (isSelected) Owlsplit.colors.accent else Owlsplit.colors.accent.copy(alpha = FADED),
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 2.dp,
                                    bottomEnd = 2.dp
                                ),
                            ),
                    )
                }
                Text(
                    column.label,
                    style = Owlsplit.type.caption,
                    color = if (isSelected) Owlsplit.colors.textPrimary else Owlsplit.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 2.5f
private const val MIN_SWEEP = 0.5f
private const val GROW_MILLIS = 700
private const val REST_ALPHA = 0.55f
private const val FADED = 0.28f
private const val BAR_WIDTH = 0.62f
private const val MIN_COLUMN = 0.02f
private const val MAX_FITTING_COLUMNS = 8
private val COLUMN_WIDTH = 34.dp
private val MAX_BAR_WIDTH = 36.dp
