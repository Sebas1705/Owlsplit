package es.sebas1705.owlsplit.feature.groups.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.Minus
import compose.icons.tablericons.Plus
import es.sebas1705.owlsplit.core.ui.components.Avatar
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.feature.groups.common.TopBar
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.active_label
import es.sebas1705.owlsplit.feature.groups.resources.add_member
import es.sebas1705.owlsplit.feature.groups.resources.cancel
import es.sebas1705.owlsplit.feature.groups.resources.decrease
import es.sebas1705.owlsplit.feature.groups.resources.delete_member
import es.sebas1705.owlsplit.feature.groups.resources.edit_member
import es.sebas1705.owlsplit.feature.groups.resources.ghost
import es.sebas1705.owlsplit.feature.groups.resources.inactive
import es.sebas1705.owlsplit.feature.groups.resources.increase
import es.sebas1705.owlsplit.feature.groups.resources.member_in_use
import es.sebas1705.owlsplit.feature.groups.resources.member_name
import es.sebas1705.owlsplit.feature.groups.resources.members
import es.sebas1705.owlsplit.feature.groups.resources.save
import es.sebas1705.owlsplit.feature.groups.resources.weight_hint
import es.sebas1705.owlsplit.feature.groups.resources.weight_label
import es.sebas1705.owlsplit.feature.groups.resources.weight_value
import es.sebas1705.owlsplit.feature.groups.resources.with_account
import es.sebas1705.owlsplit.feature.groups.resources.you_suffix
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MembersRoute(groupId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<MembersViewModel>(key = "members-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MembersScreen(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun MembersScreen(
    state: MembersState,
    onEvent: (MembersIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        TopBar(stringResource(Res.string.members), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.l)) {
            OwlsplitContainer {
                state.members.forEach { member ->
                    MemberRow(member, isMe = member.id == state.myMemberId) { onEvent(MembersIntent.Edit(member)) }
                }
            }
        }
        PrimaryPillButton(
            stringResource(Res.string.add_member),
            { onEvent(MembersIntent.Add) },
            Modifier.padding(Spacing.l)
        )
    }
    state.form?.let { MemberDialog(it, onEvent) }
}

@Composable
private fun MemberRow(member: Member, isMe: Boolean, onClick: () -> Unit) {
    val colors = Owlsplit.colors
    val details = listOfNotNull(
        stringResource(if (member.userUid != null) Res.string.with_account else Res.string.ghost),
        stringResource(Res.string.weight_value, member.defaultWeight.toInt()),
        if (member.active) null else stringResource(Res.string.inactive),
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Avatar(member.name)
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) stringResource(Res.string.you_suffix, member.name) else member.name,
                style = Owlsplit.type.bodyStrong,
                color = if (member.active) colors.textPrimary else colors.textMuted,
            )
            Text(details, style = Owlsplit.type.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun MemberDialog(form: MemberForm, onEvent: (MembersIntent) -> Unit) {
    val colors = Owlsplit.colors
    AlertDialog(
        onDismissRequest = { onEvent(MembersIntent.Dismiss) },
        containerColor = colors.surface,
        title = {
            Text(
                stringResource(if (form.memberId == null) Res.string.add_member else Res.string.edit_member),
                style = Owlsplit.type.title,
                color = colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OwlsplitTextField(
                    value = form.name,
                    onValueChange = { onEvent(MembersIntent.NameChanged(it)) },
                    label = stringResource(Res.string.member_name),
                    capitalization = KeyboardCapitalization.Words,
                )
                WeightStepper(form.weight) { onEvent(MembersIntent.WeightChanged(it)) }
                if (form.memberId != null && !form.isMe) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.active_label),
                            style = Owlsplit.type.body,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = form.active,
                            onCheckedChange = { onEvent(MembersIntent.ActiveChanged(it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
                        )
                    }
                    if (form.inUse) {
                        Text(
                            stringResource(Res.string.member_in_use),
                            style = Owlsplit.type.caption,
                            color = colors.textSecondary
                        )
                    } else {
                        TextActionButton(
                            stringResource(Res.string.delete_member),
                            { onEvent(MembersIntent.Delete) },
                            color = colors.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(stringResource(Res.string.save), { onEvent(MembersIntent.Save) }, enabled = form.canSave)
        },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), { onEvent(MembersIntent.Dismiss) }) },
    )
}

@Composable
private fun WeightStepper(weight: Long, onChange: (Long) -> Unit) {
    val colors = Owlsplit.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(Res.string.weight_label), style = Owlsplit.type.label, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(weight - 1) }, enabled = weight > 1) {
                Icon(TablerIcons.Minus, stringResource(Res.string.decrease), tint = colors.textPrimary)
            }
            Text("$weight", style = Owlsplit.type.title, color = colors.textPrimary)
            IconButton(onClick = { onChange(weight + 1) }) {
                Icon(TablerIcons.Plus, stringResource(Res.string.increase), tint = colors.textPrimary)
            }
        }
        Text(stringResource(Res.string.weight_hint), style = Owlsplit.type.caption, color = colors.textSecondary)
    }
}
