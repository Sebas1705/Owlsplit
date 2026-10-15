package es.sebas1705.owlsplit.feature.groups.stats

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import es.sebas1705.owlsplit.core.ui.components.Avatar
import es.sebas1705.owlsplit.feature.groups.resources.stats_count
import es.sebas1705.owlsplit.feature.groups.resources.stats_per_day_value
import es.sebas1705.owlsplit.feature.groups.resources.stats_spent
import org.jetbrains.compose.resources.pluralStringResource
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
            item { Hero(stats, currency, format) }
            item { CategoryDonut(stats.categories, currency, format) }
            if (stats.months.size > 1) item { MonthColumns(stats.months, currency, format) }
            item { People(detail, stats, currency, format) }
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
private fun Hero(stats: GroupStats, currency: Currency, format: MoneyFormat) {
    val count = pluralStringResource(Res.plurals.stats_count, stats.expenseCount, stats.expenseCount)
    val perDay = stringResource(Res.string.stats_per_day_value, format.format(stats.perDayMinor, currency))
    SpendHero(stringResource(Res.string.stats_spent), stats.totalMinor, currency, format, "$count · $perDay")
}

/** Each person: what they paid against their share, as two bars, and the balance that leaves. */
@Composable
private fun People(detail: GroupDetail, stats: GroupStats, currency: Currency, format: MoneyFormat) {
    val largest = stats.members.maxOfOrNull { maxOf(it.paidMinor, it.shareMinor) }?.coerceAtLeast(1) ?: return
    val colors = Owlsplit.colors
    OwlsplitContainer(title = stringResource(Res.string.stats_people)) {
        stats.members.forEach { member ->
            val name = detail.members.nameOf(member.memberId)
            val balance = detail.balances?.balances?.get(member.memberId) ?: (member.paidMinor - member.shareMinor)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.rowMinHeight)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Avatar(name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(name, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
                    PersonBar(member.paidMinor.toFloat() / largest, colors.accent)
                    PersonBar(member.shareMinor.toFloat() / largest, colors.accent.copy(alpha = SHARE_ALPHA))
                    Text(
                        stringResource(
                            Res.string.stats_paid_share,
                            format.format(member.paidMinor, currency),
                            format.format(member.shareMinor, currency),
                        ),
                        style = Owlsplit.type.caption,
                        color = colors.textSecondary,
                    )
                }
                Text(
                    format.format(balance, currency, showPlus = true),
                    style = Owlsplit.type.bodyStrong,
                    color = if (balance > 0) colors.positive else colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun PersonBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(Owlsplit.colors.surfaceVariant, RoundedCornerShape(3.dp))) {
        if (fraction > 0f) {
            Box(
                Modifier.fillMaxWidth(
                    fraction.coerceIn(MIN_BAR, 1f)
                ).height(6.dp).background(color, RoundedCornerShape(3.dp)),
            )
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

private const val SHARE_ALPHA = 0.35f
private const val MIN_BAR = 0.02f
