package es.sebas1705.owlsplit.feature.groups.transfer

import es.sebas1705.owlsplit.core.ui.components.OwlsplitMenuItem
import es.sebas1705.owlsplit.core.ui.components.OwlsplitDropdownMenu
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.payment_amount
import es.sebas1705.owlsplit.feature.groups.resources.payment_from
import es.sebas1705.owlsplit.feature.groups.resources.payment_note
import es.sebas1705.owlsplit.feature.groups.resources.payment_note_placeholder
import es.sebas1705.owlsplit.feature.groups.resources.payment_same_member
import es.sebas1705.owlsplit.feature.groups.resources.payment_to
import es.sebas1705.owlsplit.feature.groups.resources.record_payment
import es.sebas1705.owlsplit.feature.groups.resources.settle_confirm
import es.sebas1705.owlsplit.feature.groups.resources.you_suffix
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransferRoute(groupId: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<TransferViewModel>(key = "transfer-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    TransferScreen(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun TransferScreen(
    state: TransferState,
    onEvent: (TransferIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background).imePadding()) {
        TopBar(stringResource(Res.string.record_payment), onClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            OwlsplitContainer {
                MemberRow(
                    stringResource(Res.string.payment_from),
                    state.fromId,
                    state
                ) { onEvent(TransferIntent.FromChanged(it)) }
                MemberRow(
                    stringResource(Res.string.payment_to),
                    state.toId,
                    state
                ) { onEvent(TransferIntent.ToChanged(it)) }
            }
            if (state.sameMember) {
                Text(
                    stringResource(Res.string.payment_same_member),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.error
                )
            }
            OwlsplitTextField(
                value = state.amountText,
                onValueChange = { onEvent(TransferIntent.AmountChanged(it)) },
                label = stringResource(Res.string.payment_amount, state.currency),
                placeholder = "0",
                keyboardType = KeyboardType.Decimal,
            )
            OwlsplitTextField(
                value = state.note,
                onValueChange = { onEvent(TransferIntent.NoteChanged(it)) },
                label = stringResource(Res.string.payment_note),
                placeholder = stringResource(Res.string.payment_note_placeholder),
            )
        }
        PrimaryPillButton(
            stringResource(Res.string.settle_confirm),
            { onEvent(TransferIntent.Save) },
            Modifier.padding(Spacing.l),
            enabled = state.canSave,
        )
    }
}

@Composable
private fun MemberRow(label: String, selectedId: String?, state: TransferState, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = { id: String? -> state.members.firstOrNull { it.id == id }?.name }
    val you = stringResource(Res.string.you_suffix, name(state.myMemberId).orEmpty())
    val display = { id: String? -> if (id != null && id == state.myMemberId) you else name(id).orEmpty() }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.rowMinHeight)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Owlsplit.type.body, color = Owlsplit.colors.textPrimary, modifier = Modifier.weight(1f))
            Text(display(selectedId), style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
            Icon(
                TablerIcons.ChevronDown,
                null,
                tint = Owlsplit.colors.textMuted,
                modifier = Modifier.padding(start = Spacing.xs).size(18.dp)
            )
        }
        OwlsplitDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.members.forEach { member ->
                OwlsplitMenuItem(
                    display(member.id),
                    onClick = {
                        open = false
                        onSelect(member.id)
                    },
                    selected = member.id == selectedId,
                )
            }
        }
    }
}
