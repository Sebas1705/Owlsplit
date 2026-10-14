package es.sebas1705.owlsplit.feature.groups.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.stats.MySpending
import es.sebas1705.owlsplit.domain.usecases.stats.SpendingPeriod
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.period_all
import es.sebas1705.owlsplit.feature.groups.resources.period_month
import es.sebas1705.owlsplit.feature.groups.resources.period_year
import es.sebas1705.owlsplit.feature.groups.resources.spending_approximate
import es.sebas1705.owlsplit.feature.groups.resources.spending_empty_body
import es.sebas1705.owlsplit.feature.groups.resources.spending_empty_title
import es.sebas1705.owlsplit.feature.groups.resources.spending_groups
import es.sebas1705.owlsplit.feature.groups.resources.spending_hint
import es.sebas1705.owlsplit.feature.groups.resources.spending_mine
import es.sebas1705.owlsplit.feature.groups.resources.spending_paid
import es.sebas1705.owlsplit.feature.groups.resources.spending_title
import es.sebas1705.owlsplit.feature.groups.resources.spending_unconverted
import es.sebas1705.owlsplit.feature.groups.resources.stats_expenses
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MySpendingRoute(onOpenGroup: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<MySpendingViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MySpendingScreen(state, viewModel::onEvent, onOpenGroup, modifier)
}

/** The Statistics tab: my part of the spending of all my groups, by period, group, category and month. */
@Composable
fun MySpendingScreen(
    state: MySpendingState,
    onEvent: (MySpendingIntent) -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
    format: MoneyFormat = LocalMoneyFormat.current,
) {
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.spending_title),
                style = Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.m).semantics { heading() },
            )
        }
        val spending = state.spending
        if (!state.loaded || spending == null) return@Column
        val currency = Currency.of(spending.currency)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item {
                SegmentedControl(
                    listOf(
                        stringResource(Res.string.period_month),
                        stringResource(Res.string.period_year),
                        stringResource(Res.string.period_all),
                    ),
                    selectedIndex = state.period.ordinal,
                    onSelect = { onEvent(MySpendingIntent.SelectPeriod(SpendingPeriod.entries[it])) },
                )
            }
            if (spending.expenseCount == 0) {
                item {
                    EmptyState(
                        stringResource(Res.string.spending_empty_title),
                        stringResource(Res.string.spending_empty_body),
                    )
                }
                return@LazyColumn
            }
            item { Totals(spending, currency, format) }
            item { Groups(spending, currency, format, onOpenGroup) }
            item { CategoryBars(spending.categories, currency, format) }
            if (spending.months.size > 1) item { MonthBars(spending.months, currency, format) }
        }
    }
}

@Composable
private fun Totals(spending: MySpending, currency: Currency, format: MoneyFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OwlsplitContainer {
            Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Figure(
                    stringResource(Res.string.spending_mine),
                    format.format(spending.shareMinor, currency),
                    Modifier.weight(1f),
                )
                Figure(
                    stringResource(Res.string.spending_paid),
                    format.format(spending.paidMinor, currency),
                    Modifier.weight(1f),
                )
                Figure(
                    stringResource(Res.string.stats_expenses),
                    spending.expenseCount.toString(),
                    Modifier.weight(1f),
                )
            }
        }
        Note(stringResource(Res.string.spending_hint))
        if (spending.approximate) Note(stringResource(Res.string.spending_approximate))
        if (spending.unconverted.isNotEmpty()) {
            val amounts = spending.unconverted.entries.joinToString(", ") { (code, minor) ->
                format.format(minor, Currency.of(code))
            }
            Note(stringResource(Res.string.spending_unconverted, amounts))
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = Owlsplit.type.caption,
        color = Owlsplit.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
private fun Groups(spending: MySpending, currency: Currency, format: MoneyFormat, onOpenGroup: (String) -> Unit) {
    val largest = spending.groups.maxOfOrNull { it.amountMinor } ?: return
    val colors = Owlsplit.colors
    OwlsplitContainer(title = stringResource(Res.string.spending_groups)) {
        spending.groups.forEach { item ->
            val accent = GroupAccent.entries.firstOrNull { it.name == item.group.accent } ?: GroupAccent.BLUE
            BarRow(
                GroupIcons.of(item.group.icon),
                item.group.name,
                format.format(item.amountMinor, currency),
                item.amountMinor.toFloat() / largest,
                iconTint = accent.content(colors),
                onClick = { onOpenGroup(item.group.id) },
            )
        }
    }
}
