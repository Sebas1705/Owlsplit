package es.sebas1705.owlsplit.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Qrcode
import compose.icons.tablericons.UserPlus
import es.sebas1705.owlsplit.core.ui.components.AccentPicker
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitLogo
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.ProfileAvatar
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.accentName
import es.sebas1705.owlsplit.feature.onboarding.resources.Res
import es.sebas1705.owlsplit.feature.onboarding.resources.back
import es.sebas1705.owlsplit.feature.onboarding.resources.colour_label
import es.sebas1705.owlsplit.feature.onboarding.resources.continue_
import es.sebas1705.owlsplit.feature.onboarding.resources.create_group
import es.sebas1705.owlsplit.feature.onboarding.resources.create_group_hint
import es.sebas1705.owlsplit.feature.onboarding.resources.default_name
import es.sebas1705.owlsplit.feature.onboarding.resources.existing_account
import es.sebas1705.owlsplit.feature.onboarding.resources.google_failed
import es.sebas1705.owlsplit.feature.onboarding.resources.join_group
import es.sebas1705.owlsplit.feature.onboarding.resources.join_group_hint
import es.sebas1705.owlsplit.feature.onboarding.resources.later
import es.sebas1705.owlsplit.feature.onboarding.resources.name_label
import es.sebas1705.owlsplit.feature.onboarding.resources.name_placeholder
import es.sebas1705.owlsplit.feature.onboarding.resources.offline
import es.sebas1705.owlsplit.feature.onboarding.resources.profile_body
import es.sebas1705.owlsplit.feature.onboarding.resources.profile_title
import es.sebas1705.owlsplit.feature.onboarding.resources.skip
import es.sebas1705.owlsplit.feature.onboarding.resources.start
import es.sebas1705.owlsplit.feature.onboarding.resources.start_body
import es.sebas1705.owlsplit.feature.onboarding.resources.start_title
import es.sebas1705.owlsplit.feature.onboarding.resources.step_of
import es.sebas1705.owlsplit.feature.onboarding.resources.waiting_network
import es.sebas1705.owlsplit.feature.onboarding.resources.welcome_body
import es.sebas1705.owlsplit.feature.onboarding.resources.welcome_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Onboarding wired to its ViewModel; [onFinished] fires once the profile is saved. A new
 * [runKey] each time onboarding starts again (sign out, account deleted): the ViewModel outlives
 * the screen, and an old one would reopen on its last step.
 */
@Composable
fun OnboardingRoute(onFinished: (StartChoice) -> Unit, modifier: Modifier = Modifier, runKey: String = "onboarding") {
    val viewModel = koinViewModel<OnboardingViewModel>(key = runKey)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { state.finished?.let(onFinished) }
    OnboardingScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun OnboardingScreen(state: OnboardingState, onEvent: (OnboardingIntent) -> Unit, modifier: Modifier = Modifier) {
    val fallbackName = stringResource(Res.string.default_name)
    val finish = { choice: StartChoice -> onEvent(OnboardingIntent.Finish(choice, fallbackName)) }
    Column(
        modifier.fillMaxSize().background(Owlsplit.colors.background).imePadding(),
    ) {
        TopBar(state, onBack = { onEvent(OnboardingIntent.Back) }, onSkip = { finish(StartChoice.LATER) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            when (state.step) {
                OnboardingStep.WELCOME -> Welcome(state, onEvent)
                OnboardingStep.PROFILE -> ProfileStep(state, onEvent)
                OnboardingStep.START -> StartStep(state, finish)
            }
            Status(state)
        }
    }
}

@Composable
private fun TopBar(state: OnboardingState, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step != OnboardingStep.WELCOME) {
            IconButton(onClick = onBack, enabled = !state.busy) {
                Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
            }
        }
        Spacer(Modifier.weight(1f))
        StepDots(state.step)
        Spacer(Modifier.weight(1f))
        TextActionButton(stringResource(Res.string.skip), onSkip, enabled = !state.busy)
    }
}

@Composable
private fun StepDots(step: OnboardingStep) {
    val total = OnboardingStep.entries.size
    val description = stringResource(Res.string.step_of, step.ordinal + 1, total)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnboardingStep.entries.forEach {
            val color = if (it == step) Owlsplit.colors.accent else Owlsplit.colors.divider
            Box(Modifier.size(width = if (it == step) 18.dp else 6.dp, height = 6.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun ColumnScope.Welcome(state: OnboardingState, onEvent: (OnboardingIntent) -> Unit) {
    Spacer(Modifier.heightIn(min = Spacing.xxl))
    OwlsplitLogo(Modifier.align(Alignment.CenterHorizontally), size = 96.dp)
    Title(stringResource(Res.string.welcome_title), centered = true)
    Body(stringResource(Res.string.welcome_body), centered = true)
    Spacer(Modifier.heightIn(min = Spacing.xl))
    PrimaryPillButton(stringResource(Res.string.start), { onEvent(OnboardingIntent.Next) }, enabled = !state.busy)
    if (state.googleAvailable) {
        TextActionButton(
            stringResource(Res.string.existing_account),
            { onEvent(OnboardingIntent.UseExistingAccount) },
            Modifier.align(Alignment.CenterHorizontally),
            enabled = !state.busy,
        )
    }
}

@Composable
private fun ColumnScope.ProfileStep(state: OnboardingState, onEvent: (OnboardingIntent) -> Unit) {
    Title(stringResource(Res.string.profile_title))
    Body(stringResource(Res.string.profile_body))
    val fallback = stringResource(Res.string.default_name)
    ProfileAvatar(
        state.name.ifBlank { fallback },
        state.accent,
        Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.s),
        size = 88.dp,
    )
    OwlsplitTextField(
        value = state.name,
        onValueChange = { onEvent(OnboardingIntent.NameChanged(it)) },
        label = stringResource(Res.string.name_label),
        placeholder = stringResource(Res.string.name_placeholder),
        capitalization = KeyboardCapitalization.Words,
    )
    Text(stringResource(Res.string.colour_label), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
    AccentPicker(state.accent, { onEvent(OnboardingIntent.AccentChanged(it)) }, names = { accentName(it) })
    Spacer(Modifier.heightIn(min = Spacing.l))
    PrimaryPillButton(stringResource(Res.string.continue_), { onEvent(OnboardingIntent.Next) }, enabled = !state.busy)
}

@Composable
private fun ColumnScope.StartStep(state: OnboardingState, finish: (StartChoice) -> Unit) {
    Title(stringResource(Res.string.start_title))
    Body(stringResource(Res.string.start_body))
    OwlsplitContainer {
        ChoiceRow(
            TablerIcons.UserPlus,
            stringResource(Res.string.create_group),
            stringResource(Res.string.create_group_hint),
            !state.busy
        ) {
            finish(StartChoice.CREATE_GROUP)
        }
        ChoiceRow(
            TablerIcons.Qrcode,
            stringResource(Res.string.join_group),
            stringResource(Res.string.join_group_hint),
            !state.busy
        ) {
            finish(StartChoice.JOIN_GROUP)
        }
    }
    TextActionButton(
        stringResource(Res.string.later),
        { finish(StartChoice.LATER) },
        Modifier.align(Alignment.CenterHorizontally),
        enabled = !state.busy,
    )
}

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, hint: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(Sizes.iconTile).background(Owlsplit.colors.accentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Owlsplit.colors.accentText, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
            Text(hint, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        }
        Icon(TablerIcons.ChevronRight, null, tint = Owlsplit.colors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColumnScope.Status(state: OnboardingState) {
    if (state.busy) {
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Owlsplit.colors.accent, strokeWidth = 2.dp)
            if (state.waitingForNetwork) {
                Text(
                    stringResource(Res.string.waiting_network),
                    style = Owlsplit.type.body,
                    color = Owlsplit.colors.textSecondary
                )
            }
        }
    }
    val message = when (state.message) {
        OnboardingMessage.GOOGLE_FAILED -> stringResource(Res.string.google_failed)
        OnboardingMessage.OFFLINE -> stringResource(Res.string.offline)
        null -> null
    }
    if (message != null) Text(message, style = Owlsplit.type.body, color = Owlsplit.colors.error)
    Spacer(Modifier.heightIn(min = Spacing.xl))
}

@Composable
private fun Title(text: String, centered: Boolean = false) {
    Text(
        text,
        style = Owlsplit.type.title,
        color = Owlsplit.colors.textPrimary,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun Body(text: String, centered: Boolean = false) {
    Text(
        text,
        style = Owlsplit.type.body,
        color = Owlsplit.colors.textSecondary,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}
