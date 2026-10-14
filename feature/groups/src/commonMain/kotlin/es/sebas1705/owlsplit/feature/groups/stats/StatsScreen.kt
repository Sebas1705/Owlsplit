package es.sebas1705.owlsplit.feature.groups.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Share
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.FileSharer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitDropdownMenu
import es.sebas1705.owlsplit.core.ui.components.OwlsplitMenuItem
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.rememberDateTexts
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.categoryName
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.nameOf
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.all_settled_title
import es.sebas1705.owlsplit.feature.groups.resources.export
import es.sebas1705.owlsplit.feature.groups.resources.export_csv
import es.sebas1705.owlsplit.feature.groups.resources.export_pdf
import es.sebas1705.owlsplit.feature.groups.resources.no_expenses_body
import es.sebas1705.owlsplit.feature.groups.resources.no_expenses_title
import es.sebas1705.owlsplit.feature.groups.resources.report_amount
import es.sebas1705.owlsplit.feature.groups.resources.report_category
import es.sebas1705.owlsplit.feature.groups.resources.report_currency
import es.sebas1705.owlsplit.feature.groups.resources.report_date
import es.sebas1705.owlsplit.feature.groups.resources.report_description
import es.sebas1705.owlsplit.feature.groups.resources.report_footer
import es.sebas1705.owlsplit.feature.groups.resources.report_paid_by
import es.sebas1705.owlsplit.feature.groups.resources.report_transfer
import es.sebas1705.owlsplit.feature.groups.resources.report_transfer_title
import es.sebas1705.owlsplit.feature.groups.resources.stats_balance
import es.sebas1705.owlsplit.feature.groups.resources.stats_categories
import es.sebas1705.owlsplit.feature.groups.resources.stats_expenses
import es.sebas1705.owlsplit.feature.groups.resources.stats_months
import es.sebas1705.owlsplit.feature.groups.resources.stats_paid
import es.sebas1705.owlsplit.feature.groups.resources.stats_paid_share
import es.sebas1705.owlsplit.feature.groups.resources.stats_people
import es.sebas1705.owlsplit.feature.groups.resources.stats_per_day
import es.sebas1705.owlsplit.feature.groups.resources.stats_share
import es.sebas1705.owlsplit.feature.groups.resources.stats_title
import es.sebas1705.owlsplit.feature.groups.resources.stats_total
import es.sebas1705.owlsplit.feature.groups.resources.stats_to_settle
import es.sebas1705.owlsplit.feature.groups.resources.suggestion_pays
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StatsRoute(groupId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<StatsViewModel>(key = "stats-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(state, onBack, koinInject(), modifier)
}

@Composable
fun StatsScreen(
    state: StatsState,
    onBack: () -> Unit,
    sharer: FileSharer,
    modifier: Modifier = Modifier,
    format: MoneyFormat = LocalMoneyFormat.current,
) {
    val detail = state.detail
    val stats = state.stats
    val labels = reportLabels()
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        TopBar(stringResource(Res.string.stats_title), onBack) {
            if (detail != null && stats != null && stats.expenseCount > 0) {
                ExportMenu { pdf ->
                    val name = GroupReport.fileName(detail.group.name, if (pdf) "pdf" else "csv")
                    if (pdf) {
                        sharer.share(name, GroupReport.PDF_MIME, GroupReport.pdf(detail, stats, labels, format))
                    } else {
                        sharer.share(name, GroupReport.CSV_MIME, GroupReport.csv(detail, labels, format))
                    }
                }
            }
        }
        if (detail == null || stats == null) return@Column
        if (stats.expenseCount == 0) {
            EmptyState(stringResource(Res.string.no_expenses_title), stringResource(Res.string.no_expenses_body))
            return@Column
        }
        val currency = Currency.of(stats.baseCurrency)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item { Figures(stats, currency, format) }
            item { People(detail, stats, currency, format) }
            item { CategoryBars(stats.categories, currency, format) }
            if (stats.months.size > 1) item { MonthBars(stats.months, currency, format) }
        }
    }
}

@Composable
private fun ExportMenu(onExport: (pdf: Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(TablerIcons.Share, stringResource(Res.string.export), tint = Owlsplit.colors.textPrimary)
        }
        OwlsplitDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OwlsplitMenuItem(
                stringResource(Res.string.export_pdf),
                onClick = {
                    open = false
                    onExport(true)
                },
            )
            OwlsplitMenuItem(
                stringResource(Res.string.export_csv),
                onClick = {
                    open = false
                    onExport(false)
                },
            )
        }
    }
}

@Composable
private fun Figures(stats: GroupStats, currency: Currency, format: MoneyFormat) {
    OwlsplitContainer {
        Row(Modifier.padding(Spacing.m), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Figure(
                stringResource(Res.string.stats_total),
                format.format(stats.totalMinor, currency),
                Modifier.weight(1f)
            )
            Figure(stringResource(Res.string.stats_expenses), stats.expenseCount.toString(), Modifier.weight(1f))
            Figure(
                stringResource(Res.string.stats_per_day),
                format.format(stats.perDayMinor, currency),
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun People(detail: GroupDetail, stats: GroupStats, currency: Currency, format: MoneyFormat) {
    OwlsplitContainer(title = stringResource(Res.string.stats_people)) {
        stats.members.forEach { member ->
            val balance = detail.balances?.balances?.get(member.memberId) ?: (member.paidMinor - member.shareMinor)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.rowMinHeight)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.members.nameOf(member.memberId),
                        style = Owlsplit.type.bodyStrong,
                        color = Owlsplit.colors.textPrimary,
                    )
                    Text(
                        stringResource(
                            Res.string.stats_paid_share,
                            format.format(member.paidMinor, currency),
                            format.format(member.shareMinor, currency),
                        ),
                        style = Owlsplit.type.caption,
                        color = Owlsplit.colors.textSecondary,
                    )
                }
                Text(
                    format.format(balance, currency, showPlus = true),
                    style = Owlsplit.type.bodyStrong,
                    color = if (balance > 0) Owlsplit.colors.positive else Owlsplit.colors.textPrimary,
                )
            }
        }
    }
}

/** Every word the exports need, in the app's language. */
@Composable
private fun reportLabels(): ReportLabels {
    val dates = rememberDateTexts()
    val categories = ExpenseCategory.entries.associateWith { categoryName(it.name) }
    val payment = stringResource(Res.string.suggestion_pays, "\u0001", "\u0002", "\u0003")
    val transferTitle = stringResource(Res.string.report_transfer_title, "\u0001", "\u0002")
    return ReportLabels(
        summary = stringResource(Res.string.stats_title),
        total = stringResource(Res.string.stats_total),
        expenses = stringResource(Res.string.stats_expenses),
        perDay = stringResource(Res.string.stats_per_day),
        people = stringResource(Res.string.stats_people),
        paid = stringResource(Res.string.stats_paid),
        share = stringResource(Res.string.stats_share),
        balance = stringResource(Res.string.stats_balance),
        categories = stringResource(Res.string.stats_categories),
        months = stringResource(Res.string.stats_months),
        toSettle = stringResource(Res.string.stats_to_settle),
        allSettled = stringResource(Res.string.all_settled_title),
        date = stringResource(Res.string.report_date),
        description = stringResource(Res.string.report_description),
        category = stringResource(Res.string.report_category),
        amount = stringResource(Res.string.report_amount),
        currency = stringResource(Res.string.report_currency),
        paidBy = stringResource(Res.string.report_paid_by),
        transfer = stringResource(Res.string.report_transfer),
        footer = stringResource(Res.string.report_footer),
        categoryName = { categories.getValue(it) },
        dateText = dates::date,
        monthText = dates::month,
        // Compose resources substitute arguments as text: markers keep the pattern for later.
        payment = { from, amount, to ->
            payment.replace(
                "\u0001",
                from
            ).replace("\u0002", amount).replace("\u0003", to)
        },
        transferTitle = { from, to -> transferTitle.replace("\u0001", from).replace("\u0002", to) },
    )
}
