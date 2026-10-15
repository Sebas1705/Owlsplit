package es.sebas1705.owlsplit.feature.groups.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.AmountText
import es.sebas1705.owlsplit.core.ui.components.ChartColumn
import es.sebas1705.owlsplit.core.ui.components.ChartSlice
import es.sebas1705.owlsplit.core.ui.components.ColumnChart
import es.sebas1705.owlsplit.core.ui.components.DonutChart
import es.sebas1705.owlsplit.core.ui.components.MAX_CHART_COLORS
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.chartColors
import es.sebas1705.owlsplit.core.ui.components.chartRestColor
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.rememberDateTexts
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.categoryIcon
import es.sebas1705.owlsplit.core.ui.theme.categoryName
import es.sebas1705.owlsplit.domain.usecases.stats.CategoryStats
import es.sebas1705.owlsplit.domain.usecases.stats.MonthStats
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.stats_categories
import es.sebas1705.owlsplit.feature.groups.resources.stats_months
import es.sebas1705.owlsplit.feature.groups.resources.stats_percent
import es.sebas1705.owlsplit.feature.groups.resources.stats_rest
import es.sebas1705.owlsplit.feature.groups.resources.stats_total
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** A caption over a figure, read as one. */
@Composable
internal fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        Text(value, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
    }
}

/** The headline figure of a summary, big and centred, with a line of context under it. */
@Composable
internal fun SpendHero(label: String, amountMinor: Long, currency: Currency, format: MoneyFormat, footer: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        AmountText(amountMinor, currency, format, Owlsplit.type.amountDisplay, Owlsplit.colors.textPrimary)
        Text(footer, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** A part of a whole as a legend line: what it is, its colour, its share and its amount. */
internal class LegendEntry(
    val label: String,
    val icon: ImageVector,
    val amountMinor: Long,
    val color: Color,
    val iconTint: Color? = null,
    val onClick: (() -> Unit)? = null,
)

/** Rows naming the parts of a chart; [total] gives each one's share. */
@Composable
internal fun Legend(entries: List<LegendEntry>, total: Long, currency: Currency, format: MoneyFormat) {
    entries.forEach { entry ->
        val amount = format.format(entry.amountMinor, currency)
        val share = stringResource(Res.string.stats_percent, percent(entry.amountMinor, total))
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (entry.onClick != null) Modifier.clickable(onClick = entry.onClick) else Modifier)
                .heightIn(min = Sizes.rowMinHeight)
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
                .clearAndSetSemantics {
                    contentDescription = "${entry.label}, $amount, $share"
                    if (entry.onClick != null) role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier.size(32.dp).background(entry.color.copy(alpha = ICON_BACKDROP), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, null, tint = entry.iconTint ?: entry.color, modifier = Modifier.size(18.dp))
            }
            Text(
                entry.label,
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
                Text(share, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
            }
        }
    }
}

/** Spending by category: a ring with the total inside, over its legend. Past six, the rest merge. */
@Composable
internal fun CategoryDonut(categories: List<CategoryStats>, currency: Currency, format: MoneyFormat) {
    if (categories.isEmpty()) return
    val palette = chartColors(Owlsplit.colors)
    val rest = chartRestColor(Owlsplit.colors)
    val restLabel = stringResource(Res.string.stats_rest)
    val shown = categories.take(MAX_CHART_COLORS)
    val merged = categories.drop(MAX_CHART_COLORS).sumOf { it.amountMinor }
    val entries = shown.mapIndexed { i, category ->
        LegendEntry(categoryName(category.category.name), categoryIcon(category.category.name), category.amountMinor, palette[i])
    } + listOfNotNull(
        LegendEntry(restLabel, categoryIcon("OTHER"), merged, rest).takeIf { merged > 0 },
    )
    val total = categories.sumOf { it.amountMinor }
    OwlsplitContainer(title = stringResource(Res.string.stats_categories)) {
        DonutChart(
            entries.map { ChartSlice(it.amountMinor.toFloat(), it.color) },
            Modifier.padding(Spacing.m).size(DONUT_SIZE).align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.stats_total),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary
                )
                Text(
                    format.format(total, currency),
                    style = Owlsplit.type.bodyStrong,
                    color = Owlsplit.colors.textPrimary
                )
            }
        }
        Legend(entries, total, currency, format)
    }
}

/** Spending by month as columns; tapping one shows its amount above them. The latest is selected first. */
@Composable
internal fun MonthColumns(months: List<MonthStats>, currency: Currency, format: MoneyFormat) {
    if (months.isEmpty()) return
    val dates = rememberDateTexts()
    var selected by remember(months) { mutableIntStateOf(months.indexOfLast { it.amountMinor > 0 }.coerceAtLeast(0)) }
    val month = months[selected.coerceIn(months.indices)]
    OwlsplitContainer(title = stringResource(Res.string.stats_months)) {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    format.format(month.amountMinor, currency),
                    style = Owlsplit.type.amountHeadline,
                    color = Owlsplit.colors.textPrimary,
                )
                Text(
                    dates.month(month.year, month.month),
                    style = Owlsplit.type.body,
                    color = Owlsplit.colors.textSecondary,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }
            ColumnChart(
                months.map {
                    ChartColumn(
                        dates.monthName(it.month),
                        it.amountMinor,
                        "${dates.month(it.year, it.month)}, ${format.format(it.amountMinor, currency)}",
                    )
                },
                selected = selected,
                onSelect = { selected = it },
            )
        }
    }
}

/** Whole percent of [part] in [total], at least 1 for anything that is not nothing. */
internal fun percent(part: Long, total: Long): Int = when {
    total <= 0 || part <= 0 -> 0
    else -> (part * PERCENT.toDouble() / total).roundToInt().coerceAtLeast(1)
}

private const val PERCENT = 100
private const val ICON_BACKDROP = 0.14f
private val DONUT_SIZE = 168.dp
