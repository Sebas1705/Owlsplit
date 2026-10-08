package es.sebas1705.owlsplit.feature.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Qrcode
import compose.icons.tablericons.UserPlus
import es.sebas1705.owlsplit.core.ui.theme.memberAccent
import es.sebas1705.owlsplit.core.ui.components.ProfileAvatar
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.QrScanner
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.feature.invite.resources.Res
import es.sebas1705.owlsplit.feature.invite.resources.back
import es.sebas1705.owlsplit.feature.invite.resources.code_label
import es.sebas1705.owlsplit.feature.invite.resources.code_placeholder
import es.sebas1705.owlsplit.feature.invite.resources.join
import es.sebas1705.owlsplit.feature.invite.resources.join_body
import es.sebas1705.owlsplit.feature.invite.resources.join_title
import es.sebas1705.owlsplit.feature.invite.resources.joining
import es.sebas1705.owlsplit.feature.invite.resources.me_default
import es.sebas1705.owlsplit.feature.invite.resources.new_member
import es.sebas1705.owlsplit.feature.invite.resources.not_found
import es.sebas1705.owlsplit.feature.invite.resources.join_offline
import es.sebas1705.owlsplit.feature.invite.resources.people
import es.sebas1705.owlsplit.feature.invite.resources.rejected
import es.sebas1705.owlsplit.feature.invite.resources.scan_qr
import es.sebas1705.owlsplit.feature.invite.resources.search
import es.sebas1705.owlsplit.feature.invite.resources.searching
import es.sebas1705.owlsplit.feature.invite.resources.waiting_members
import es.sebas1705.owlsplit.feature.invite.resources.who_are_you
import es.sebas1705.owlsplit.feature.invite.resources.who_are_you_body
import org.jetbrains.compose.resources.pluralStringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val CODE_FIELD_LENGTH = 64

@Composable
fun JoinRoute(initialCode: String?, onBack: () -> Unit, onJoined: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<JoinViewModel>(key = "join-$initialCode") { parametersOf(initialCode) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.joinedGroupId) { state.joinedGroupId?.let(onJoined) }
    val scanner = koinInject<QrScanner>()
    val scope = rememberCoroutineScope()
    val onScan: () -> Unit = { scope.launch { scanner.scan()?.let { viewModel.onEvent(JoinIntent.Scanned(it)) } } }
    JoinScreen(state, viewModel::onEvent, onBack, modifier, onScan.takeIf { scanner.isAvailable })
}

/** [onScan] null hides the QR option (no camera scanner on this platform). */
@Composable
fun JoinScreen(
    state: JoinState,
    onEvent: (JoinIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onScan: (() -> Unit)? = null,
) {
    val backFromPreview = state.step == JoinStep.PREVIEW
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (backFromPreview) onEvent(JoinIntent.Back) else onBack() }) {
                Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (state.step) {
                JoinStep.ENTER, JoinStep.SEARCHING -> EnterCode(state, onEvent, onScan)
                JoinStep.PREVIEW, JoinStep.JOINING -> Preview(state, onEvent)
                JoinStep.PICK, JoinStep.DONE -> PickMember(state, onEvent)
            }
        }
    }
}

@Composable
private fun ColumnScope.EnterCode(state: JoinState, onEvent: (JoinIntent) -> Unit, onScan: (() -> Unit)?) {
    Heading(stringResource(Res.string.join_title))
    Text(stringResource(Res.string.join_body), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
    if (onScan != null) {
        OwlsplitContainer {
            ChoiceRow(stringResource(Res.string.scan_qr), state.step == JoinStep.ENTER, {
                Icon(
                    TablerIcons.Qrcode,
                    null,
                    Modifier.size(36.dp).padding(Spacing.s),
                    tint = Owlsplit.colors.accentText
                )
            }, onScan)
        }
    }
    OwlsplitTextField(
        value = state.code,
        onValueChange = { onEvent(JoinIntent.CodeChanged(it)) },
        label = stringResource(Res.string.code_label),
        placeholder = stringResource(Res.string.code_placeholder),
        error = state.error?.let { stringResource(it.message()) },
        maxLength = CODE_FIELD_LENGTH,
        capitalization = KeyboardCapitalization.Characters,
    )
    if (state.step == JoinStep.SEARCHING) {
        Busy(stringResource(Res.string.searching))
    } else {
        PrimaryPillButton(stringResource(Res.string.search), { onEvent(JoinIntent.Search) }, enabled = state.canSearch)
    }
}

@Composable
private fun ColumnScope.Preview(state: JoinState, onEvent: (JoinIntent) -> Unit) {
    val preview = state.preview ?: return
    OwlsplitContainer {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Icon(GroupIcons.of(preview.groupIcon), null, Modifier.size(32.dp), tint = Owlsplit.colors.accentText)
            Column {
                Heading(preview.groupName)
                Text(
                    pluralStringResource(Res.plurals.people, preview.memberCount, preview.memberCount),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                )
            }
        }
    }
    state.error?.let { Text(stringResource(it.message()), style = Owlsplit.type.body, color = Owlsplit.colors.error) }
    if (state.step == JoinStep.JOINING) {
        Busy(stringResource(Res.string.joining))
    } else if (state.error != JoinError.REJECTED) {
        PrimaryPillButton(stringResource(Res.string.join), { onEvent(JoinIntent.Join) })
    }
}

@Composable
private fun ColumnScope.PickMember(state: JoinState, onEvent: (JoinIntent) -> Unit) {
    Heading(stringResource(Res.string.who_are_you))
    val ghosts = state.ghosts
    if (ghosts == null) {
        Busy(stringResource(Res.string.waiting_members))
        return
    }
    Text(stringResource(Res.string.who_are_you_body), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
    val enabled = state.step == JoinStep.PICK
    OwlsplitContainer {
        ghosts.forEach { ghost ->
            ChoiceRow(ghost.name, enabled, {
                ProfileAvatar(ghost.name, memberAccent(ghost.id, null))
            }) { onEvent(JoinIntent.Pick(ghost.id)) }
        }
        val me = state.myName.ifBlank { stringResource(Res.string.me_default) }
        ChoiceRow(stringResource(Res.string.new_member, me), enabled, {
            Icon(TablerIcons.UserPlus, null, Modifier.size(36.dp).padding(Spacing.s), tint = Owlsplit.colors.accentText)
        }) { onEvent(JoinIntent.Pick(null)) }
    }
}

@Composable
private fun ChoiceRow(text: String, enabled: Boolean, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        leading()
        Text(text, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = Owlsplit.type.title,
        color = Owlsplit.colors.textPrimary,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Owlsplit.colors.accent, strokeWidth = 2.dp)
        Text(text, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
    }
}

private fun JoinError.message() = when (this) {
    JoinError.NOT_FOUND -> Res.string.not_found
    JoinError.OFFLINE -> Res.string.join_offline
    JoinError.REJECTED -> Res.string.rejected
}
