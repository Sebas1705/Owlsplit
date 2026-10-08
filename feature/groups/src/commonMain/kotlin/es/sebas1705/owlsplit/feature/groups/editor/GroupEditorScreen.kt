package es.sebas1705.owlsplit.feature.groups.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import es.sebas1705.owlsplit.core.ui.components.AccentPicker
import es.sebas1705.owlsplit.core.ui.components.CurrencyPicker
import es.sebas1705.owlsplit.core.ui.components.DateField
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.dayLabel
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.accentName
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.cancel
import es.sebas1705.owlsplit.feature.groups.resources.clear_dates
import es.sebas1705.owlsplit.feature.groups.resources.colour_label
import es.sebas1705.owlsplit.feature.groups.resources.create
import es.sebas1705.owlsplit.feature.groups.resources.currency_hint
import es.sebas1705.owlsplit.feature.groups.resources.currency_label
import es.sebas1705.owlsplit.feature.groups.resources.currency_search
import es.sebas1705.owlsplit.feature.groups.resources.date_from
import es.sebas1705.owlsplit.feature.groups.resources.date_to
import es.sebas1705.owlsplit.feature.groups.resources.dates_invalid
import es.sebas1705.owlsplit.feature.groups.resources.edit_group
import es.sebas1705.owlsplit.feature.groups.resources.icon_label
import es.sebas1705.owlsplit.feature.groups.resources.name_label
import es.sebas1705.owlsplit.feature.groups.resources.name_placeholder
import es.sebas1705.owlsplit.feature.groups.resources.new_group
import es.sebas1705.owlsplit.feature.groups.resources.no_date
import es.sebas1705.owlsplit.feature.groups.resources.ok
import es.sebas1705.owlsplit.feature.groups.resources.save
import es.sebas1705.owlsplit.feature.groups.resources.trip_dates
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val ICONS_PER_ROW = 6

/** [onSaved] receives the group id (new or edited). */
@Composable
fun GroupEditorRoute(groupId: String?, onBack: () -> Unit, onSaved: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<GroupEditorViewModel>(key = "group-editor-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedGroupId) { state.savedGroupId?.let(onSaved) }
    GroupEditorScreen(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun GroupEditorScreen(
    state: GroupEditorState,
    onEvent: (GroupEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background).imePadding()) {
        TopBar(stringResource(if (state.editing) Res.string.edit_group else Res.string.new_group), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(72.dp)
                    .background(state.accent.container(Owlsplit.colors), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    GroupIcons.of(state.icon),
                    null,
                    tint = state.accent.content(Owlsplit.colors),
                    modifier = Modifier.size(34.dp)
                )
            }
            OwlsplitTextField(
                value = state.name,
                onValueChange = { onEvent(GroupEditorIntent.NameChanged(it)) },
                label = stringResource(Res.string.name_label),
                placeholder = stringResource(Res.string.name_placeholder),
                capitalization = KeyboardCapitalization.Sentences,
            )
            Label(stringResource(Res.string.icon_label))
            IconGrid(state) { onEvent(GroupEditorIntent.IconChanged(it)) }
            Label(stringResource(Res.string.colour_label))
            AccentPicker(state.accent, { onEvent(GroupEditorIntent.AccentChanged(it)) }, names = { accentName(it) })
            OwlsplitContainer {
                // The currency of an existing group cannot change: its expenses are valued in it.
                CurrencyRow(state.currency, enabled = !state.editing) { onEvent(GroupEditorIntent.CurrencyChanged(it)) }
                Text(
                    stringResource(Res.string.currency_hint),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
                )
            }
            TripDates(state, onEvent)
        }
        PrimaryPillButton(
            stringResource(if (state.editing) Res.string.save else Res.string.create),
            { onEvent(GroupEditorIntent.Save) },
            Modifier.padding(Spacing.l),
            enabled = state.canSave,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
}

@Composable
private fun IconGrid(state: GroupEditorState, onSelect: (String) -> Unit) {
    val colors = Owlsplit.colors
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        GroupIcons.all.chunked(ICONS_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (key, icon) ->
                    val selected = key == state.icon
                    val name = GroupIcons.name(key)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = Sizes.touchTarget)
                            .selectable(selected, role = Role.RadioButton) { onSelect(key) }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(
                                    if (selected) state.accent.container(colors) else colors.surface,
                                    CircleShape
                                )
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            2.dp,
                                            state.accent.content(colors),
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icon,
                                null,
                                tint = if (selected) state.accent.content(colors) else colors.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.rowMinHeight)
                .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.currency_label),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(selected, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
            if (enabled) {
                Icon(
                    TablerIcons.ChevronDown,
                    null,
                    tint = Owlsplit.colors.textMuted,
                    modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
                )
            }
        }
        if (open) {
            CurrencyPicker(
                selected = selected,
                title = stringResource(Res.string.currency_label),
                searchLabel = stringResource(Res.string.currency_search),
                cancelLabel = stringResource(Res.string.cancel),
                onSelect = {
                    open = false
                    onSelect(it)
                },
                onDismiss = { open = false },
            )
        }
    }
}

@Composable
private fun TripDates(state: GroupEditorState, onEvent: (GroupEditorIntent) -> Unit) {
    val today = Today.System()
    val noDate = stringResource(Res.string.no_date)
    OwlsplitContainer(title = stringResource(Res.string.trip_dates)) {
        DateField(
            label = stringResource(Res.string.date_from),
            text = state.startDate?.let { dayLabel(it, today) } ?: noDate,
            date = state.startDate ?: today,
            confirmLabel = stringResource(Res.string.ok),
            cancelLabel = stringResource(Res.string.cancel),
            onChange = { onEvent(GroupEditorIntent.StartChanged(it)) },
        )
        DateField(
            label = stringResource(Res.string.date_to),
            text = state.endDate?.let { dayLabel(it, today) } ?: noDate,
            date = state.endDate ?: state.startDate ?: today,
            confirmLabel = stringResource(Res.string.ok),
            cancelLabel = stringResource(Res.string.cancel),
            onChange = { onEvent(GroupEditorIntent.EndChanged(it)) },
        )
        if (!state.datesValid) {
            Text(
                stringResource(Res.string.dates_invalid),
                style = Owlsplit.type.caption,
                color = Owlsplit.colors.error,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }
        if (state.startDate != null || state.endDate != null) {
            TextActionButton(
                stringResource(Res.string.clear_dates),
                { onEvent(GroupEditorIntent.ClearDates) },
                Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}
