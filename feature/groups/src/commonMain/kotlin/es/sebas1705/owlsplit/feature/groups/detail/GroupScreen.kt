package es.sebas1705.owlsplit.feature.groups.detail

import androidx.compose.foundation.layout.offset
import es.sebas1705.owlsplit.core.ui.theme.memberAccent
import es.sebas1705.owlsplit.core.ui.components.BottomActionBar
import androidx.compose.foundation.lazy.rememberLazyListState
import es.sebas1705.owlsplit.core.ui.components.OwlsplitMenuItem
import es.sebas1705.owlsplit.core.ui.components.OwlsplitDropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
import compose.icons.tablericons.DotsVertical
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Users
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.AmountText
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.ExpenseRow
import es.sebas1705.owlsplit.core.ui.components.MemberBalanceRow
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.components.SettleSuggestionRow
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.dayLabel
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.categoryIcon
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import es.sebas1705.owlsplit.domain.usecases.groups.nameOf
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.add_expense
import es.sebas1705.owlsplit.feature.groups.resources.all_settled_body
import es.sebas1705.owlsplit.feature.groups.resources.all_settled_title
import es.sebas1705.owlsplit.feature.groups.resources.archive
import es.sebas1705.owlsplit.feature.groups.resources.balance_even
import es.sebas1705.owlsplit.feature.groups.resources.balance_owed
import es.sebas1705.owlsplit.feature.groups.resources.balance_owes
import es.sebas1705.owlsplit.feature.groups.resources.cancel
import es.sebas1705.owlsplit.feature.groups.resources.edit_group
import es.sebas1705.owlsplit.feature.groups.resources.leave
import es.sebas1705.owlsplit.feature.groups.resources.leave_body
import es.sebas1705.owlsplit.feature.groups.resources.leave_confirm
import es.sebas1705.owlsplit.feature.groups.resources.leave_title
import es.sebas1705.owlsplit.feature.groups.resources.history
import es.sebas1705.owlsplit.feature.groups.resources.invite
import es.sebas1705.owlsplit.feature.groups.resources.members
import es.sebas1705.owlsplit.feature.groups.resources.more
import es.sebas1705.owlsplit.feature.groups.resources.no_expenses_body
import es.sebas1705.owlsplit.feature.groups.resources.no_expenses_title
import es.sebas1705.owlsplit.feature.groups.resources.no_match
import es.sebas1705.owlsplit.feature.groups.resources.paid_by
import es.sebas1705.owlsplit.feature.groups.resources.record_payment
import es.sebas1705.owlsplit.feature.groups.resources.settle
import es.sebas1705.owlsplit.feature.groups.resources.suggestion_pays
import es.sebas1705.owlsplit.feature.groups.resources.suggestion_pays_you
import es.sebas1705.owlsplit.feature.groups.resources.suggestion_step
import es.sebas1705.owlsplit.feature.groups.resources.suggestion_you_pay
import es.sebas1705.owlsplit.feature.groups.resources.tab_balances
import es.sebas1705.owlsplit.feature.groups.resources.tab_expenses
import es.sebas1705.owlsplit.feature.groups.resources.tab_settle
import es.sebas1705.owlsplit.feature.groups.resources.transfer_title
import es.sebas1705.owlsplit.feature.groups.resources.transfers
import es.sebas1705.owlsplit.feature.groups.resources.unarchive
import es.sebas1705.owlsplit.feature.groups.resources.you
import es.sebas1705.owlsplit.feature.groups.resources.your_balance
import es.sebas1705.owlsplit.feature.groups.resources.your_share
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

@Composable
fun GroupRoute(groupId: String, initialTab: GroupTab, navigation: GroupNavigation, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GroupViewModel>(key = "group-$groupId") { parametersOf(groupId, initialTab) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed && state.loaded) navigation.back() }
    GroupScreen(state, viewModel::onEvent, navigation, koinInject(), modifier)
}

@Composable
fun GroupScreen(
    state: GroupState,
    onEvent: (GroupIntent) -> Unit,
    navigation: GroupNavigation,
    sharer: TextSharer,
    modifier: Modifier = Modifier,
    format: MoneyFormat = LocalMoneyFormat.current,
    today: LocalDate = Today.System(),
) {
    val detail = state.detail ?: return
    val currency = Currency.of(detail.group.baseCurrency)
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        TopBar("", navigation.back) { Actions(detail, onEvent, navigation) }
        val list = rememberLazyListState()
        LazyColumn(
            Modifier.weight(1f),
            state = list,
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item { Header(detail, currency, format) }
            item {
                SegmentedControl(
                    listOf(
                        stringResource(Res.string.tab_expenses),
                        stringResource(Res.string.tab_balances),
                        stringResource(Res.string.tab_settle),
                    ),
                    selectedIndex = state.tab.ordinal,
                    onSelect = { onEvent(GroupIntent.SelectTab(GroupTab.entries[it])) },
                )
            }
            when (state.tab) {
                GroupTab.EXPENSES -> expenses(state, detail, format, today, onEvent, navigation.openExpense)
                GroupTab.BALANCES -> item { Balances(detail, currency, format) }
                GroupTab.SETTLE -> settle(detail, currency, format, today, onEvent, navigation.recordPayment)
            }
        }
        if (state.tab == GroupTab.EXPENSES) {
            BottomActionBar(contentBelow = list.canScrollForward) {
                PrimaryPillButton(stringResource(Res.string.add_expense), navigation.addExpense)
            }
        }
    }
    Dialogs(state, detail, currency, format, sharer, onEvent)
}

@Composable
private fun Actions(detail: GroupDetail, onEvent: (GroupIntent) -> Unit, navigation: GroupNavigation) {
    var menu by remember { mutableStateOf(false) }
    IconButton(onClick = navigation.members) {
        Icon(TablerIcons.Users, stringResource(Res.string.members), tint = Owlsplit.colors.textPrimary)
    }
    IconButton(onClick = navigation.edit) {
        Icon(TablerIcons.Pencil, stringResource(Res.string.edit_group), tint = Owlsplit.colors.textPrimary)
    }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(TablerIcons.DotsVertical, stringResource(Res.string.more), tint = Owlsplit.colors.textPrimary)
        }
        OwlsplitDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            OwlsplitMenuItem(
                stringResource(Res.string.invite),
                onClick = {
                    menu = false
                    navigation.invite()
                },
            )
            OwlsplitMenuItem(
                stringResource(Res.string.history),
                onClick = {
                    menu = false
                    navigation.history()
                },
            )
            OwlsplitMenuItem(
                stringResource(if (detail.archivedByMe) Res.string.unarchive else Res.string.archive),
                onClick = {
                    menu = false
                    onEvent(GroupIntent.ToggleArchive)
                },
            )
            OwlsplitMenuItem(
                stringResource(Res.string.leave),
                onClick = {
                    menu = false
                    onEvent(GroupIntent.AskLeave)
                },
                color = Owlsplit.colors.error,
            )
        }
    }
}

@Composable
private fun Header(detail: GroupDetail, currency: Currency, format: MoneyFormat) {
    val accent = GroupAccent.entries.firstOrNull { it.name == detail.group.accent } ?: GroupAccent.BLUE
    val colors = Owlsplit.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(
            Modifier.size(52.dp).background(accent.container(colors), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(GroupIcons.of(detail.group.icon), null, tint = accent.content(colors), modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(detail.group.name, style = Owlsplit.type.title, color = colors.textPrimary)
            val mine = detail.myMemberId?.let { detail.balances?.balances?.get(it) }
            if (mine != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    Text(
                        stringResource(Res.string.your_balance),
                        style = Owlsplit.type.caption,
                        color = colors.textSecondary
                    )
                    AmountText(
                        mine,
                        currency,
                        format,
                        Owlsplit.type.bodyStrong,
                        if (mine > 0) colors.positive else colors.textPrimary,
                        showPlus = true,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.expenses(
    state: GroupState,
    detail: GroupDetail,
    format: MoneyFormat,
    today: LocalDate,
    onEvent: (GroupIntent) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (detail.expenses.isEmpty()) {
        item { EmptyState(stringResource(Res.string.no_expenses_title), stringResource(Res.string.no_expenses_body)) }
        return
    }
    item(key = "filters") { ExpenseFilters(state, detail, onEvent) }
    val visible = state.visibleExpenses
    if (visible.isEmpty()) {
        item(key = "no-match") {
            Text(stringResource(Res.string.no_match), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
        }
    }
    visible.groupBy { it.date }.forEach { (date, dayExpenses) ->
        item(key = "day-$date") {
            Text(dayLabel(date, today), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
        }
        item(key = "list-$date") {
            OwlsplitContainer {
                dayExpenses.forEach { expense ->
                    // Shares are in the expense's own currency.
                    val currency = Currency.of(expense.currency)
                    val payers = expense.payers.keys.joinToString(", ") { detail.members.nameOf(it) }
                    val share = state.myShares[expense.id] ?: 0
                    val shareText = stringResource(Res.string.your_share, format.format(share, currency))
                    ExpenseRow(
                        title = expense.title,
                        subtitle = stringResource(Res.string.paid_by, payers),
                        icon = categoryIcon(expense.category.name),
                        amountMinor = expense.amountMinor,
                        currency = currency,
                        format = format,
                        detail = shareText.takeIf { share > 0 },
                        onClick = { onOpen(expense.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Balances(detail: GroupDetail, currency: Currency, format: MoneyFormat) {
    val balances = detail.balances?.balances.orEmpty()
    val largest = balances.values.maxOfOrNull { abs(it) } ?: 0
    OwlsplitContainer {
        detail.members.forEach { member ->
            val balance = balances[member.id] ?: 0
            val amount = format.format(abs(balance), currency)
            val spoken = when {
                balance > 0 -> stringResource(Res.string.balance_owed, member.name, amount)
                balance < 0 -> stringResource(Res.string.balance_owes, member.name, amount)
                else -> stringResource(Res.string.balance_even, member.name)
            }
            MemberBalanceRow(
                member.name,
                balance,
                largest,
                currency,
                format,
                spokenDescription = spoken,
                accent = memberAccent(member.id, member.avatar),
            )
        }
    }
}

private fun LazyListScope.settle(
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    today: LocalDate,
    onEvent: (GroupIntent) -> Unit,
    onRecordPayment: () -> Unit,
) {
    val payments = detail.balances?.suggestedPayments.orEmpty()
    if (payments.isEmpty()) {
        item { EmptyState(stringResource(Res.string.all_settled_title), stringResource(Res.string.all_settled_body)) }
    } else {
        item {
            OwlsplitContainer {
                payments.forEachIndexed { index, payment ->
                    SettleSuggestionRow(
                        text = paymentText(payment, detail, currency, format),
                        subtitle = stringResource(Res.string.suggestion_step, index + 1, payments.size),
                        actionLabel = stringResource(Res.string.settle),
                        icon = TablerIcons.ArrowsLeftRight,
                        onAction = { onEvent(GroupIntent.AskSettle(payment)) },
                    )
                }
            }
        }
    }
    // Pulled back by the button padding: the label lines up with the cards.
    item {
        TextActionButton(
            stringResource(Res.string.record_payment),
            onRecordPayment,
            Modifier.offset(x = -Spacing.m)
        )
    }
    if (detail.transfers.isNotEmpty()) {
        item {
            Text(
                stringResource(Res.string.transfers),
                style = Owlsplit.type.label,
                color = Owlsplit.colors.textSecondary
            )
        }
        item {
            OwlsplitContainer {
                detail.transfers.sortedByDescending { it.createdAt }.forEach { transfer ->
                    ExpenseRow(
                        title = stringResource(
                            Res.string.transfer_title,
                            memberLabel(transfer.fromMemberId, detail),
                            memberLabel(transfer.toMemberId, detail),
                        ),
                        subtitle = dayLabel(transfer.date, today),
                        icon = TablerIcons.ArrowsLeftRight,
                        amountMinor = transfer.amountMinor,
                        currency = Currency.of(transfer.currency),
                        format = format,
                        detail = null,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun memberLabel(id: String, detail: GroupDetail): String =
    if (id == detail.myMemberId) stringResource(Res.string.you) else detail.members.nameOf(id)

@Composable
private fun paymentText(
    payment: SuggestedPayment,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat
): String {
    val amount = format.format(payment.amountMinor, currency)
    val from = detail.members.nameOf(payment.fromMemberId)
    val to = detail.members.nameOf(payment.toMemberId)
    return when (detail.myMemberId) {
        payment.fromMemberId -> stringResource(Res.string.suggestion_you_pay, amount, to)
        payment.toMemberId -> stringResource(Res.string.suggestion_pays_you, from, amount)
        else -> stringResource(Res.string.suggestion_pays, from, amount, to)
    }
}

@Composable
private fun Dialogs(
    state: GroupState,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    sharer: TextSharer,
    onEvent: (GroupIntent) -> Unit,
) {
    val dismiss = { onEvent(GroupIntent.DismissDialog) }
    state.confirmSettle?.let { payment ->
        SettleDialog(
            payment,
            paymentText(payment, detail, currency, format),
            detail,
            currency,
            format,
            sharer,
            onConfirm = { onEvent(GroupIntent.ConfirmSettle) },
            onDismiss = dismiss,
        )
    }
    if (state.confirmLeave) {
        ConfirmDialog(
            stringResource(Res.string.leave_title, detail.group.name),
            stringResource(Res.string.leave_body),
            stringResource(Res.string.leave_confirm),
            onConfirm = { onEvent(GroupIntent.ConfirmLeave) },
            onDismiss = dismiss,
            destructive = true,
        )
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Owlsplit.colors.surface,
        title = { Text(title, style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary) },
        text = { Text(body, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary) },
        confirmButton = {
            TextActionButton(
                confirm,
                onConfirm,
                color = if (destructive) Owlsplit.colors.error else Owlsplit.colors.accentText
            )
        },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), onDismiss) },
    )
}
