package es.sebas1705.owlsplit.feature.groups.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.CalendarEvent
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
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
import org.jetbrains.compose.resources.stringResource

/** A caption over a figure, read as one. */
@Composable
internal fun Figure(label: String, value: String, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        Text(value, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
    }
}

@Composable
internal fun CategoryBars(categories: List<CategoryStats>, currency: Currency, format: MoneyFormat) {
    val largest = categories.maxOfOrNull { it.amountMinor } ?: return
    OwlsplitContainer(title = stringResource(Res.string.stats_categories)) {
        categories.forEach {
            BarRow(
                categoryIcon(it.category.name),
                categoryName(it.category.name),
                format.format(it.amountMinor, currency),
                it.amountMinor.toFloat() / largest,
            )
        }
    }
}

@Composable
internal fun MonthBars(months: List<MonthStats>, currency: Currency, format: MoneyFormat) {
    val dates = rememberDateTexts()
    val largest = months.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: return
    OwlsplitContainer(title = stringResource(Res.string.stats_months)) {
        months.forEach {
            BarRow(
                TablerIcons.CalendarEvent,
                dates.month(it.year, it.month),
                format.format(it.amountMinor, currency),
                it.amountMinor.toFloat() / largest,
            )
        }
    }
}

/** An icon, a label over a bar as long as [fraction] of the largest, and the amount; tappable with [onClick]. */
@Composable
internal fun BarRow(
    icon: ImageVector,
    label: String,
    amount: String,
    fraction: Float,
    iconTint: Color = Owlsplit.colors.textSecondary,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Sizes.rowMinHeight)
            .padding(horizontal = Spacing.m, vertical = Spacing.s)
            .clearAndSetSemantics {
                contentDescription = "$label, $amount"
                if (onClick != null) role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = Owlsplit.type.body, color = Owlsplit.colors.textPrimary)
            Box(Modifier.fillMaxWidth().height(BAR_HEIGHT).background(Owlsplit.colors.surfaceVariant, BAR_SHAPE)) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(MIN_BAR, 1f))
                        .height(BAR_HEIGHT)
                        .background(Owlsplit.colors.accent, BAR_SHAPE),
                )
            }
        }
        Text(amount, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
    }
}

private val BAR_HEIGHT = 6.dp
private val BAR_SHAPE = RoundedCornerShape(3.dp)
private const val MIN_BAR = 0.02f
