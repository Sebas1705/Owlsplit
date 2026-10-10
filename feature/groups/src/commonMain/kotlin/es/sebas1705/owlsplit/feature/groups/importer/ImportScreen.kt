package es.sebas1705.owlsplit.feature.groups.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import es.sebas1705.owlsplit.core.ui.components.FilePicker
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.import_another
import es.sebas1705.owlsplit.feature.groups.resources.import_choose
import es.sebas1705.owlsplit.feature.groups.resources.import_how
import es.sebas1705.owlsplit.feature.groups.resources.import_intro
import es.sebas1705.owlsplit.feature.groups.resources.import_missing_rates
import es.sebas1705.owlsplit.feature.groups.resources.import_none
import es.sebas1705.owlsplit.feature.groups.resources.import_not_splitwise
import es.sebas1705.owlsplit.feature.groups.resources.import_nothing
import es.sebas1705.owlsplit.feature.groups.resources.import_run
import es.sebas1705.owlsplit.feature.groups.resources.import_skipped
import es.sebas1705.owlsplit.feature.groups.resources.import_summary
import es.sebas1705.owlsplit.feature.groups.resources.import_title
import es.sebas1705.owlsplit.feature.groups.resources.import_who
import es.sebas1705.owlsplit.feature.groups.resources.name_label
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ImportRoute(onBack: () -> Unit, onImported: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ImportViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.importedGroupId) { state.importedGroupId?.let(onImported) }
    ImportScreen(state, viewModel::onEvent, onBack, koinInject(), modifier)
}

@Composable
fun ImportScreen(
    state: ImportState,
    onEvent: (ImportIntent) -> Unit,
    onBack: () -> Unit,
    picker: FilePicker,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pick: () -> Unit = { scope.launch { onEvent(ImportIntent.Picked(picker.pickText())) } }
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background).imePadding()) {
        TopBar(stringResource(Res.string.import_title), onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (state.parsed == null) Intro(state) else Preview(state, onEvent, pick)
        }
        if (state.parsed == null) {
            PrimaryPillButton(stringResource(Res.string.import_choose), pick, Modifier.padding(Spacing.l))
        } else {
            PrimaryPillButton(
                stringResource(Res.string.import_run),
                { onEvent(ImportIntent.Import) },
                Modifier.padding(Spacing.l),
                enabled = state.canImport,
            )
        }
    }
}

@Composable
private fun Intro(state: ImportState) {
    Text(stringResource(Res.string.import_intro), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
    OwlsplitContainer {
        Text(
            stringResource(Res.string.import_how),
            style = Owlsplit.type.body,
            color = Owlsplit.colors.textPrimary,
            modifier = Modifier.padding(Spacing.m),
        )
    }
    val error = when (state.error) {
        ImportError.NOT_SPLITWISE -> stringResource(Res.string.import_not_splitwise)
        ImportError.NOTHING_TO_IMPORT -> stringResource(Res.string.import_nothing)
        null -> null
    }
    if (error != null) {
        Text(
            error,
            style = Owlsplit.type.body,
            color = Owlsplit.colors.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun Preview(state: ImportState, onEvent: (ImportIntent) -> Unit, pick: () -> Unit) {
    val parsed = state.parsed ?: return
    Text(
        stringResource(Res.string.import_summary, parsed.expenseCount, parsed.paymentCount, parsed.mainCurrency),
        style = Owlsplit.type.bodyStrong,
        color = Owlsplit.colors.textPrimary,
    )
    if (parsed.skipped > 0) Warning(stringResource(Res.string.import_skipped, parsed.skipped))
    if (state.missingRates.isNotEmpty()) {
        Warning(stringResource(Res.string.import_missing_rates, state.missingRates.sorted().joinToString(", ")))
    }
    OwlsplitTextField(
        value = state.name,
        onValueChange = { onEvent(ImportIntent.NameChanged(it)) },
        label = stringResource(Res.string.name_label),
        capitalization = KeyboardCapitalization.Sentences,
    )
    OwlsplitContainer(title = stringResource(Res.string.import_who)) {
        Column(Modifier.selectableGroup()) {
            parsed.members.forEachIndexed { index, name ->
                ChoiceRow(name, state.meIndex == index) { onEvent(ImportIntent.MeChanged(index)) }
            }
            ChoiceRow(
                stringResource(Res.string.import_none),
                state.meIndex == null
            ) { onEvent(ImportIntent.MeChanged(null)) }
        }
    }
    // Pulled back by the button padding: the label lines up with the cards.
    TextActionButton(
        stringResource(Res.string.import_another),
        {
            onEvent(ImportIntent.Reset)
            pick()
        },
        Modifier.offset(x = -Spacing.m),
    )
}

@Composable
private fun Warning(text: String) {
    Text(text, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Owlsplit.type.body, color = Owlsplit.colors.textPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(TablerIcons.Check, null, tint = Owlsplit.colors.accentText, modifier = Modifier.size(20.dp))
    }
}
