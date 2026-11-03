package es.sebas1705.owlsplit.feature.groups.members

import es.sebas1705.owlsplit.feature.groups.resources.admin_badge
import es.sebas1705.owlsplit.feature.groups.resources.admin_only_hint
import es.sebas1705.owlsplit.feature.groups.resources.take_over_body
import es.sebas1705.owlsplit.feature.groups.resources.take_over_group
import es.sebas1705.owlsplit.feature.groups.resources.permissions_title
import es.sebas1705.owlsplit.feature.groups.resources.perm_add_expenses
import es.sebas1705.owlsplit.feature.groups.resources.perm_edit_expenses
import es.sebas1705.owlsplit.feature.groups.resources.perm_record_payments
import es.sebas1705.owlsplit.feature.groups.resources.make_admin
import es.sebas1705.owlsplit.feature.groups.resources.make_admin_hint
import es.sebas1705.owlsplit.feature.groups.resources.merge_member
import es.sebas1705.owlsplit.feature.groups.resources.merge_title
import es.sebas1705.owlsplit.feature.groups.resources.merge_body
import es.sebas1705.owlsplit.feature.groups.resources.merge_none
import es.sebas1705.owlsplit.feature.groups.resources.merge_confirm_title
import es.sebas1705.owlsplit.feature.groups.resources.merge_confirm_body
import es.sebas1705.owlsplit.feature.groups.resources.merge_action
import es.sebas1705.owlsplit.feature.groups.resources.left_group
import es.sebas1705.owlsplit.feature.groups.resources.with_google
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.components.BottomActionBar
import es.sebas1705.owlsplit.core.ui.components.ProfileAvatar
import es.sebas1705.owlsplit.core.ui.theme.memberAccent
import es.sebas1705.owlsplit.data.models.Permission
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
import es.sebas1705.owlsplit.core.ui.components.SwitchRow
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
        val scroll = rememberScrollState()
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(Spacing.l)) {
            if (state.adminless) {
                Text(
                    stringResource(Res.string.take_over_body),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                )
                TextActionButton(stringResource(Res.string.take_over_group), { onEvent(MembersIntent.TakeOver) })
            } else if (!state.isAdmin) {
                val admin = state.members.firstOrNull { it.id == state.adminId }?.name.orEmpty()
                Text(
                    stringResource(Res.string.admin_only_hint, admin),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }
            OwlsplitContainer {
                state.members.forEach { member ->
                    val isMe = member.id == state.myMemberId
                    MemberRow(
                        member,
                        isMe = isMe,
                        isAdmin = member.id == state.adminId,
                        enabled = state.isAdmin || isMe
                    ) {
                        onEvent(MembersIntent.Edit(member))
                    }
                }
            }
        }
        if (state.isAdmin) {
            BottomActionBar(contentBelow = scroll.canScrollForward) {
                PrimaryPillButton(stringResource(Res.string.add_member), { onEvent(MembersIntent.Add) })
            }
        }
    }
    state.form?.let { MemberDialog(it, onEvent) }
    state.merge?.let { MergeDialogs(it, state.mergeCandidates, onEvent) }
}

@Composable
private fun MemberRow(member: Member, isMe: Boolean, isAdmin: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = Owlsplit.colors
    val details = listOfNotNull(
        stringResource(
            when {
                member.userUid != null && member.linked -> Res.string.with_google
                member.userUid != null -> Res.string.with_account
                member.abandoned -> Res.string.left_group
                else -> Res.string.ghost
            },
        ),
        stringResource(Res.string.weight_value, member.defaultWeight.toInt()),
        if (member.active) null else stringResource(Res.string.inactive),
        if (isAdmin) stringResource(Res.string.admin_badge) else null,
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ProfileAvatar(member.name, memberAccent(member.id, member.avatar))
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
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) {
                OwlsplitTextField(
                    value = form.name,
                    onValueChange = { onEvent(MembersIntent.NameChanged(it)) },
                    label = stringResource(Res.string.member_name),
                    capitalization = KeyboardCapitalization.Words,
                )
                if (!form.nameOnly) WeightStepper(form.weight) { onEvent(MembersIntent.WeightChanged(it)) }
                if (form.memberId != null && !form.nameOnly && !form.isAdmin) PermissionSwitches(form, onEvent)
                if (form.memberId != null && !form.isMe && !form.nameOnly) {
                    AdminActions(form, onEvent)
                    SwitchRow(
                        stringResource(Res.string.active_label),
                        form.active,
                        { onEvent(MembersIntent.ActiveChanged(it)) },
                        shape = OwlsplitShapes.control,
                    )
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

@Composable
private fun PermissionSwitches(form: MemberForm, onEvent: (MembersIntent) -> Unit) {
    val colors = Owlsplit.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(Res.string.permissions_title), style = Owlsplit.type.label, color = colors.textSecondary)
        Permission.entries.forEach { permission ->
            SwitchRow(
                stringResource(permission.label()),
                permission !in form.denied,
                { onEvent(MembersIntent.PermissionChanged(permission, it)) },
                shape = OwlsplitShapes.control,
            )
        }
    }
}

private fun Permission.label() = when (this) {
    Permission.ADD_EXPENSES -> Res.string.perm_add_expenses
    Permission.EDIT_EXPENSES -> Res.string.perm_edit_expenses
    Permission.RECORD_PAYMENTS -> Res.string.perm_record_payments
}

/** Choosing who to merge into, then asking to confirm: it cannot be undone. */
@Composable
private fun MergeDialogs(draft: MergeDraft, candidates: List<Member>, onEvent: (MembersIntent) -> Unit) {
    val colors = Owlsplit.colors
    val target = draft.target
    val name = draft.absorbed.name
    AlertDialog(
        onDismissRequest = { onEvent(MembersIntent.CancelMerge) },
        containerColor = colors.surface,
        title = {
            val title = if (target == null) {
                stringResource(Res.string.merge_title, name)
            } else {
                stringResource(Res.string.merge_confirm_title, name, target.name)
            }
            Text(title, style = Owlsplit.type.title, color = colors.textPrimary)
        },
        text = {
            if (target == null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val body = if (candidates.isEmpty()) Res.string.merge_none else Res.string.merge_body
                    Text(stringResource(body, name), style = Owlsplit.type.caption, color = colors.textSecondary)
                    candidates.forEach { candidate ->
                        TextActionButton(candidate.name, { onEvent(MembersIntent.MergeInto(candidate)) })
                    }
                }
            } else {
                Text(
                    stringResource(Res.string.merge_confirm_body, name, target.name),
                    style = Owlsplit.type.body,
                    color = colors.textSecondary,
                )
            }
        },
        confirmButton = {
            if (target != null) {
                TextActionButton(
                    stringResource(Res.string.merge_action),
                    { onEvent(MembersIntent.ConfirmMerge) },
                    color = colors.error,
                )
            }
        },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), { onEvent(MembersIntent.CancelMerge) }) },
    )
}

@Composable
private fun AdminActions(form: MemberForm, onEvent: (MembersIntent) -> Unit) {
    if (form.hasAccount && !form.isAdmin) {
        TextActionButton(stringResource(Res.string.make_admin), { onEvent(MembersIntent.MakeAdmin) })
        Text(
            stringResource(Res.string.make_admin_hint),
            style = Owlsplit.type.caption,
            color = Owlsplit.colors.textSecondary,
        )
    }
    TextActionButton(stringResource(Res.string.merge_member), { onEvent(MembersIntent.StartMerge) })
}
