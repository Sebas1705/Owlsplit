package es.sebas1705.owlsplit.feature.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.invite.InviteCodes
import es.sebas1705.owlsplit.feature.invite.resources.Res
import es.sebas1705.owlsplit.feature.invite.resources.back
import es.sebas1705.owlsplit.feature.invite.resources.cancel
import es.sebas1705.owlsplit.feature.invite.resources.code_label
import es.sebas1705.owlsplit.feature.invite.resources.copied
import es.sebas1705.owlsplit.feature.invite.resources.copy_code
import es.sebas1705.owlsplit.feature.invite.resources.failed
import es.sebas1705.owlsplit.feature.invite.resources.invite_body
import es.sebas1705.owlsplit.feature.invite.resources.invite_title
import es.sebas1705.owlsplit.feature.invite.resources.offline
import es.sebas1705.owlsplit.feature.invite.resources.preparing
import es.sebas1705.owlsplit.feature.invite.resources.regenerate
import es.sebas1705.owlsplit.feature.invite.resources.regenerate_body
import es.sebas1705.owlsplit.feature.invite.resources.regenerate_confirm
import es.sebas1705.owlsplit.feature.invite.resources.regenerate_title
import es.sebas1705.owlsplit.feature.invite.resources.retry
import es.sebas1705.owlsplit.feature.invite.resources.share_link
import es.sebas1705.owlsplit.feature.invite.resources.share_text
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val COPIED_MILLIS = 2_000L

@Composable
fun InviteRoute(groupId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<InviteViewModel>(key = "invite-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InviteScreen(state, viewModel::onEvent, onBack, koinInject(), modifier)
}

@Composable
fun InviteScreen(
    state: InviteState,
    onEvent: (InviteIntent) -> Unit,
    onBack: () -> Unit,
    sharer: TextSharer,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MILLIS)
            copied = false
        }
    }
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                stringResource(Res.string.invite_title),
                style = Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(Res.string.invite_body), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
            when (state.status) {
                InviteStatus.PREPARING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Owlsplit.colors.accent, strokeWidth = 2.dp)
                    Text(stringResource(Res.string.preparing), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
                }
                InviteStatus.OFFLINE, InviteStatus.FAILED -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    val message = if (state.status == InviteStatus.OFFLINE) Res.string.offline else Res.string.failed
                    Text(stringResource(message), style = Owlsplit.type.body, color = Owlsplit.colors.error)
                    TextActionButton(stringResource(Res.string.retry), { onEvent(InviteIntent.Retry) })
                }
                InviteStatus.READY -> state.link?.let { link ->
                    OwlsplitContainer(title = stringResource(Res.string.code_label)) {
                        Text(
                            InviteCodes.display(link.code),
                            style = Owlsplit.type.amountHeadline,
                            color = Owlsplit.colors.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m),
                        )
                        Text(
                            link.url,
                            style = Owlsplit.type.caption,
                            color = Owlsplit.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
                        )
                    }
                    val message = stringResource(Res.string.share_text, state.groupName, link.url, InviteCodes.display(link.code))
                    PrimaryPillButton(stringResource(Res.string.share_link), {
                        if (!sharer.share(message)) {
                            clipboard.setText(AnnotatedString(message))
                            copied = true
                        }
                    })
                    TextActionButton(
                        stringResource(if (copied) Res.string.copied else Res.string.copy_code),
                        {
                            clipboard.setText(AnnotatedString(link.code))
                            copied = true
                        },
                        Modifier.align(Alignment.CenterHorizontally),
                    )
                    TextActionButton(
                        stringResource(Res.string.regenerate),
                        { onEvent(InviteIntent.AskRegenerate) },
                        Modifier.align(Alignment.CenterHorizontally),
                        color = Owlsplit.colors.textSecondary,
                    )
                }
            }
        }
    }
    if (state.confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { onEvent(InviteIntent.DismissDialog) },
            containerColor = Owlsplit.colors.surface,
            title = { Text(stringResource(Res.string.regenerate_title), style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary) },
            text = { Text(stringResource(Res.string.regenerate_body), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary) },
            confirmButton = {
                TextActionButton(stringResource(Res.string.regenerate_confirm), { onEvent(InviteIntent.ConfirmRegenerate) }, color = Owlsplit.colors.error)
            },
            dismissButton = { TextActionButton(stringResource(Res.string.cancel), { onEvent(InviteIntent.DismissDialog) }) },
        )
    }
}
