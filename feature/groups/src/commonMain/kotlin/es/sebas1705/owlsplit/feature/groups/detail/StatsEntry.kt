package es.sebas1705.owlsplit.feature.groups.detail

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChartBar
import compose.icons.tablericons.ChevronRight
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.stats_count
import es.sebas1705.owlsplit.feature.groups.resources.stats_open
import es.sebas1705.owlsplit.feature.groups.resources.stats_per_day_value
import es.sebas1705.owlsplit.feature.groups.resources.stats_spent
import es.sebas1705.owlsplit.feature.groups.resources.stats_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The group's total at a glance, opening its summary (statistics and exports). */
@Composable
internal fun StatsEntry(detail: GroupDetail, currency: Currency, format: MoneyFormat, onOpen: () -> Unit) {
    val stats = remember(detail) {
        GroupStats.of(
            detail.group.baseCurrency,
            detail.members,
            detail.expenses,
            detail.group.startDate,
            detail.group.endDate,
        )
    }
    val total = format.format(stats.totalMinor, currency)
    val details = pluralStringResource(Res.plurals.stats_count, stats.expenseCount, stats.expenseCount) + " · " +
        stringResource(Res.string.stats_per_day_value, format.format(stats.perDayMinor, currency))
    val open = stringResource(Res.string.stats_open)
    val spent = stringResource(Res.string.stats_spent)
    OwlsplitContainer {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .heightIn(min = Sizes.rowMinHeight)
                .padding(Spacing.m)
                .clearAndSetSemantics {
                    contentDescription = "$spent $total, $details. $open"
                    role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(
                Modifier.size(40.dp).background(Owlsplit.colors.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TablerIcons.ChartBar, null, tint = Owlsplit.colors.accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(spent, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
                Text(total, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
                Text(details, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
            }
            Text(stringResource(Res.string.stats_title), style = Owlsplit.type.label, color = Owlsplit.colors.accent)
            Icon(TablerIcons.ChevronRight, null, tint = Owlsplit.colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}
