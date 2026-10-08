package es.sebas1705.owlsplit.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
import compose.icons.tablericons.Link
import compose.icons.tablericons.Plus
import compose.icons.tablericons.UserPlus
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.BalanceHero
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.GroupRow
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitLogo
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.QuickActionButton
import es.sebas1705.owlsplit.core.ui.components.SyncStatus
import es.sebas1705.owlsplit.core.ui.components.SyncStatusChip
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.groups.HomeGroup
import es.sebas1705.owlsplit.feature.home.resources.Res
import es.sebas1705.owlsplit.feature.home.resources.action_expense
import es.sebas1705.owlsplit.feature.home.resources.action_group
import es.sebas1705.owlsplit.feature.home.resources.action_join
import es.sebas1705.owlsplit.feature.home.resources.action_settle
import es.sebas1705.owlsplit.feature.home.resources.approximate
import es.sebas1705.owlsplit.feature.home.resources.balance_spoken_even
import es.sebas1705.owlsplit.feature.home.resources.balance_spoken_owe
import es.sebas1705.owlsplit.feature.home.resources.balance_spoken_owed
import es.sebas1705.owlsplit.feature.home.resources.cancel
import es.sebas1705.owlsplit.feature.home.resources.create_group
import es.sebas1705.owlsplit.feature.home.resources.join_with_code
import es.sebas1705.owlsplit.feature.home.resources.empty_body
import es.sebas1705.owlsplit.feature.home.resources.empty_title
import es.sebas1705.owlsplit.feature.home.resources.groups
import es.sebas1705.owlsplit.feature.home.resources.nudge_action
import es.sebas1705.owlsplit.feature.home.resources.nudge_body
import es.sebas1705.owlsplit.feature.home.resources.nudge_dismiss
import es.sebas1705.owlsplit.feature.home.resources.nudge_title
import es.sebas1705.owlsplit.feature.home.resources.other_currencies
import es.sebas1705.owlsplit.feature.home.resources.pending
import es.sebas1705.owlsplit.feature.home.resources.people
import es.sebas1705.owlsplit.feature.home.resources.pick_group
import es.sebas1705.owlsplit.feature.home.resources.synced
import es.sebas1705.owlsplit.feature.home.resources.total_balance
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(navigation: HomeNavigation, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, viewModel::onEvent, navigation, modifier)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onEvent: (HomeIntent) -> Unit,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
    format: MoneyFormat = MoneyFormat.Spanish,
) {
    val quick = { action: QuickAction ->
        val single = state.groups.singleOrNull()?.group?.id
        if (single != null) navigation.run(action, single) else onEvent(HomeIntent.Pick(action))
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Owlsplit.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            OwlsplitLogo(size = 28.dp)
            Text("owlsplit", style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary)
        }
        if (!state.loaded) return@Column
        if (state.groups.isEmpty()) {
            EmptyState(
                stringResource(Res.string.empty_title),
                stringResource(Res.string.empty_body),
                actionLabel = stringResource(Res.string.create_group),
                onAction = navigation.createGroup,
            )
            TextActionButton(
                stringResource(Res.string.join_with_code),
                navigation.joinGroup,
                Modifier.align(Alignment.CenterHorizontally),
            )
            return@Column
        }
        Balance(state, format)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickActionButton(
                TablerIcons.Plus,
                stringResource(Res.string.action_expense),
                { quick(QuickAction.EXPENSE) }
            )
            QuickActionButton(
                TablerIcons.ArrowsLeftRight,
                stringResource(Res.string.action_settle),
                { quick(QuickAction.SETTLE) }
            )
            QuickActionButton(TablerIcons.UserPlus, stringResource(Res.string.action_group), navigation.createGroup)
            QuickActionButton(TablerIcons.Link, stringResource(Res.string.action_join), navigation.joinGroup)
        }
        if (state.suggestLinking) Nudge(onEvent, navigation.link)
        OwlsplitContainer(title = stringResource(Res.string.groups)) {
            state.groups.forEach { item -> Group(item, format) { navigation.openGroup(item.group.id) } }
        }
    }
    state.picking?.let { action ->
        GroupPicker(state.groups, onDismiss = { onEvent(HomeIntent.DismissPicker) }) { groupId ->
            onEvent(HomeIntent.DismissPicker)
            navigation.run(action, groupId)
        }
    }
}

private fun HomeNavigation.run(action: QuickAction, groupId: String) = when (action) {
    QuickAction.EXPENSE -> addExpense(groupId)
    QuickAction.SETTLE -> settle(groupId)
}

@Composable
private fun Balance(state: HomeState, format: MoneyFormat) {
    val currency = Currency.of(state.currency)
    val amount = format.format(kotlin.math.abs(state.total), currency)
    val spoken = when {
        state.total > 0 -> stringResource(Res.string.balance_spoken_owed, amount)
        state.total < 0 -> stringResource(Res.string.balance_spoken_owe, amount)
        else -> stringResource(Res.string.balance_spoken_even)
    }
    BalanceHero(
        label = stringResource(Res.string.total_balance, state.currency),
        amountMinor = state.total,
        currency = currency,
        format = format,
        modifier = Modifier.fillMaxWidth(),
        spokenDescription = spoken,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            if (state.pending > 0) {
                SyncStatusChip(
                    SyncStatus.PENDING,
                    pluralStringResource(Res.plurals.pending, state.pending.toInt(), state.pending)
                )
            } else {
                SyncStatusChip(SyncStatus.SYNCED, stringResource(Res.string.synced))
            }
            if (state.approximate) {
                Text(
                    stringResource(Res.string.approximate),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary
                )
            }
            if (state.otherCurrencies.isNotEmpty()) {
                val others = state.otherCurrencies.entries.joinToString(" · ") { (code, value) ->
                    format.format(value, Currency.of(code), showPlus = true)
                }
                Text(
                    stringResource(Res.string.other_currencies, others),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Group(item: HomeGroup, format: MoneyFormat, onClick: () -> Unit) {
    val group = item.group
    GroupRow(
        name = group.name,
        subtitle = pluralStringResource(Res.plurals.people, item.activeMembers, item.activeMembers),
        icon = GroupIcons.of(group.icon),
        accent = GroupAccent.entries.firstOrNull { it.name == group.accent } ?: GroupAccent.BLUE,
        balanceMinor = item.myBalance ?: 0,
        currency = Currency.of(group.baseCurrency),
        format = format,
        onClick = onClick,
    )
}

@Composable
private fun Nudge(onEvent: (HomeIntent) -> Unit, onLink: () -> Unit) {
    OwlsplitContainer(title = stringResource(Res.string.nudge_title)) {
        Column(Modifier.padding(horizontal = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(
                stringResource(Res.string.nudge_body),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textSecondary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextActionButton(stringResource(Res.string.nudge_dismiss), { onEvent(HomeIntent.DismissNudge) })
                Spacer(Modifier.weight(1f))
                PrimaryPillButton(stringResource(Res.string.nudge_action), onLink, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupPicker(groups: List<HomeGroup>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Owlsplit.colors.surface,
        title = {
            Text(
                stringResource(Res.string.pick_group),
                style = Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary
            )
        },
        text = {
            Column {
                groups.forEach { item ->
                    Text(
                        item.group.name,
                        style = Owlsplit.type.body,
                        color = Owlsplit.colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.touchTarget)
                            .clickable(role = Role.Button) { onPick(item.group.id) }
                            .padding(vertical = Spacing.m),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), onDismiss) },
    )
}
