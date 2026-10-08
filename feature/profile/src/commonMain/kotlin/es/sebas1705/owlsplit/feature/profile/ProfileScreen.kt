package es.sebas1705.owlsplit.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Logout
import compose.icons.tablericons.Palette
import compose.icons.tablericons.ShieldCheck
import compose.icons.tablericons.Trash
import es.sebas1705.owlsplit.core.datastore.AppLanguage
import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.ui.components.AccentPicker
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.ProfileAvatar
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.accentName
import es.sebas1705.owlsplit.feature.profile.resources.Res
import es.sebas1705.owlsplit.feature.profile.resources.anonymous_account
import es.sebas1705.owlsplit.feature.profile.resources.cancel
import es.sebas1705.owlsplit.feature.profile.resources.colour_label
import es.sebas1705.owlsplit.feature.profile.resources.currency
import es.sebas1705.owlsplit.feature.profile.resources.delete_account
import es.sebas1705.owlsplit.feature.profile.resources.delete_body
import es.sebas1705.owlsplit.feature.profile.resources.delete_confirm
import es.sebas1705.owlsplit.feature.profile.resources.delete_title
import es.sebas1705.owlsplit.feature.profile.resources.design_catalog
import es.sebas1705.owlsplit.feature.profile.resources.developer
import es.sebas1705.owlsplit.feature.profile.resources.link_google
import es.sebas1705.owlsplit.feature.profile.resources.link_unavailable
import es.sebas1705.owlsplit.feature.profile.resources.linked_google
import es.sebas1705.owlsplit.feature.profile.resources.msg_credential_in_use
import es.sebas1705.owlsplit.feature.profile.resources.msg_failed
import es.sebas1705.owlsplit.feature.profile.resources.msg_linked
import es.sebas1705.owlsplit.feature.profile.resources.msg_offline
import es.sebas1705.owlsplit.feature.profile.resources.msg_saved
import es.sebas1705.owlsplit.feature.profile.resources.name_label
import es.sebas1705.owlsplit.feature.profile.resources.preferences
import es.sebas1705.owlsplit.feature.profile.resources.protect_body
import es.sebas1705.owlsplit.feature.profile.resources.protect_title
import es.sebas1705.owlsplit.feature.profile.resources.reauth_body
import es.sebas1705.owlsplit.feature.profile.resources.reauth_confirm
import es.sebas1705.owlsplit.feature.profile.resources.reauth_title
import es.sebas1705.owlsplit.feature.profile.resources.save
import es.sebas1705.owlsplit.feature.profile.resources.session
import es.sebas1705.owlsplit.feature.profile.resources.sign_out
import es.sebas1705.owlsplit.feature.profile.resources.language
import es.sebas1705.owlsplit.feature.profile.resources.language_system
import es.sebas1705.owlsplit.feature.profile.resources.theme
import es.sebas1705.owlsplit.feature.profile.resources.theme_dark
import es.sebas1705.owlsplit.feature.profile.resources.theme_light
import es.sebas1705.owlsplit.feature.profile.resources.theme_system
import es.sebas1705.owlsplit.feature.profile.resources.title
import es.sebas1705.owlsplit.feature.profile.resources.your_profile
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Currencies offered for new groups until P9 brings the full picker. */
private val PREFERRED_CURRENCIES = listOf("EUR", "USD", "GBP", "CHF", "MXN", "ARS", "COP", "CLP", "BRL", "JPY")

private const val MESSAGE_MILLIS = 4_000L

/** Profile tab. [onOpenCatalog] is null outside the development environment. */
@Composable
fun ProfileRoute(onOpenCatalog: (() -> Unit)?, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<ProfileViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(state, viewModel::onEvent, onOpenCatalog, modifier)
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    onEvent: (ProfileIntent) -> Unit,
    onOpenCatalog: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MILLIS)
            onEvent(ProfileIntent.DismissMessage)
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(Owlsplit.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            stringResource(Res.string.title),
            style = Owlsplit.type.title,
            color = Owlsplit.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Header(state)
        Message(state.message)
        EditProfile(state, onEvent)
        if (state.isAnonymous) Protect(state, onEvent)
        Preferences(state, onEvent)
        Session(state, onEvent)
        if (onOpenCatalog != null) {
            OwlsplitContainer(title = stringResource(Res.string.developer)) {
                ActionRow(TablerIcons.Palette, stringResource(Res.string.design_catalog), onClick = onOpenCatalog)
            }
        }
        Spacer(Modifier.heightIn(min = Spacing.xl))
    }
    Dialogs(state, onEvent)
}

@Composable
private fun Header(state: ProfileState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        ProfileAvatar(state.saved?.name.orEmpty(), state.accent, size = 64.dp)
        Column {
            Text(state.saved?.name.orEmpty(), style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary)
            val status = if (state.linkedGoogle) Res.string.linked_google else Res.string.anonymous_account
            Text(stringResource(status), style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
        }
        if (state.busy) {
            Spacer(Modifier.weight(1f))
            CircularProgressIndicator(Modifier.size(20.dp), color = Owlsplit.colors.accent, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun Message(message: ProfileMessage?) {
    val text = when (message) {
        ProfileMessage.LINKED -> stringResource(Res.string.msg_linked)
        ProfileMessage.SAVED -> stringResource(Res.string.msg_saved)
        ProfileMessage.CREDENTIAL_IN_USE -> stringResource(Res.string.msg_credential_in_use)
        ProfileMessage.OFFLINE -> stringResource(Res.string.msg_offline)
        ProfileMessage.FAILED -> stringResource(Res.string.msg_failed)
        null -> return
    }
    val positive = message == ProfileMessage.LINKED || message == ProfileMessage.SAVED
    Text(
        text,
        style = Owlsplit.type.body,
        color = if (positive) Owlsplit.colors.positive else Owlsplit.colors.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun EditProfile(state: ProfileState, onEvent: (ProfileIntent) -> Unit) {
    OwlsplitContainer(title = stringResource(Res.string.your_profile)) {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            OwlsplitTextField(
                value = state.name,
                onValueChange = { onEvent(ProfileIntent.NameChanged(it)) },
                label = stringResource(Res.string.name_label),
                capitalization = KeyboardCapitalization.Words,
            )
            Text(
                stringResource(Res.string.colour_label),
                style = Owlsplit.type.label,
                color = Owlsplit.colors.textSecondary
            )
            AccentPicker(state.accent, { onEvent(ProfileIntent.AccentChanged(it)) }, names = { accentName(it) })
            if (state.dirty) {
                PrimaryPillButton(
                    stringResource(Res.string.save),
                    { onEvent(ProfileIntent.Save) },
                    enabled = state.nameValid
                )
            }
        }
    }
}

@Composable
private fun Protect(state: ProfileState, onEvent: (ProfileIntent) -> Unit) {
    OwlsplitContainer {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s)
            ) {
                Icon(TablerIcons.ShieldCheck, null, tint = Owlsplit.colors.accentText, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(Res.string.protect_title),
                    style = Owlsplit.type.bodyStrong,
                    color = Owlsplit.colors.textPrimary
                )
            }
            Text(
                stringResource(Res.string.protect_body),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textSecondary
            )
            if (state.canLink) {
                PrimaryPillButton(
                    stringResource(Res.string.link_google),
                    { onEvent(ProfileIntent.Link) },
                    enabled = !state.busy
                )
            } else {
                Text(
                    stringResource(Res.string.link_unavailable),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun Preferences(state: ProfileState, onEvent: (ProfileIntent) -> Unit) {
    OwlsplitContainer(title = stringResource(Res.string.preferences)) {
        CurrencyRow(state.currency) { onEvent(ProfileIntent.CurrencyChanged(it)) }
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Text(stringResource(Res.string.theme), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
            val modes = ThemeMode.entries
            SegmentedControl(
                options = listOf(
                    stringResource(Res.string.theme_system),
                    stringResource(Res.string.theme_light),
                    stringResource(Res.string.theme_dark),
                ),
                selectedIndex = modes.indexOf(state.theme),
                onSelect = { onEvent(ProfileIntent.ThemeChanged(modes[it])) },
            )
            Text(stringResource(Res.string.language), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
            val languages = AppLanguage.entries
            SegmentedControl(
                // Each language in its own name, so it can be found whatever the app is in.
                options = listOf(stringResource(Res.string.language_system), "Español", "English"),
                selectedIndex = languages.indexOf(state.language),
                onSelect = { onEvent(ProfileIntent.LanguageChanged(languages[it])) },
            )
        }
    }
}

@Composable
private fun CurrencyRow(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.rowMinHeight)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.currency),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(selected, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
            Icon(
                TablerIcons.ChevronDown,
                null,
                tint = Owlsplit.colors.textMuted,
                modifier = Modifier.padding(start = Spacing.xs).size(18.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PREFERRED_CURRENCIES.forEach { code ->
                DropdownMenuItem(text = { Text(code, style = Owlsplit.type.body) }, onClick = {
                    open = false
                    onSelect(code)
                })
            }
        }
    }
}

@Composable
private fun Session(state: ProfileState, onEvent: (ProfileIntent) -> Unit) {
    OwlsplitContainer(title = stringResource(Res.string.session)) {
        // An anonymous account could never come back after signing out: only linked ones can.
        if (!state.isAnonymous) {
            ActionRow(TablerIcons.Logout, stringResource(Res.string.sign_out), enabled = !state.busy) {
                onEvent(ProfileIntent.SignOut)
            }
        }
        ActionRow(TablerIcons.Trash, stringResource(Res.string.delete_account), Owlsplit.colors.error, !state.busy) {
            onEvent(ProfileIntent.AskDelete)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    color: Color = Owlsplit.colors.textPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = Owlsplit.type.body, color = color, modifier = Modifier.weight(1f))
        Icon(TablerIcons.ChevronRight, null, tint = Owlsplit.colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Dialogs(state: ProfileState, onEvent: (ProfileIntent) -> Unit) {
    when (state.dialog) {
        ProfileDialog.CONFIRM_DELETE -> ConfirmDialog(
            stringResource(Res.string.delete_title),
            stringResource(Res.string.delete_body),
            stringResource(Res.string.delete_confirm),
            destructive = true,
            onConfirm = { onEvent(ProfileIntent.ConfirmDelete) },
            onDismiss = { onEvent(ProfileIntent.DismissDialog) },
        )
        ProfileDialog.REAUTHENTICATE -> ConfirmDialog(
            stringResource(Res.string.reauth_title),
            stringResource(Res.string.reauth_body),
            stringResource(Res.string.reauth_confirm),
            destructive = false,
            onConfirm = { onEvent(ProfileIntent.Reauthenticate) },
            onDismiss = { onEvent(ProfileIntent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Owlsplit.colors.surface,
        title = { Text(title, style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary) },
        text = { Text(body, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary) },
        confirmButton = {
            val color = if (destructive) Owlsplit.colors.error else Owlsplit.colors.accentText
            TextActionButton(confirm, onConfirm, color = color)
        },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), onDismiss) },
    )
}
