package es.sebas1705.owlsplit.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Refresh
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.support.ReportProblem
import es.sebas1705.owlsplit.domain.usecases.support.SyncNow
import es.sebas1705.owlsplit.feature.profile.resources.Res
import es.sebas1705.owlsplit.feature.profile.resources.cancel
import es.sebas1705.owlsplit.feature.profile.resources.report_body
import es.sebas1705.owlsplit.feature.profile.resources.report_hint
import es.sebas1705.owlsplit.feature.profile.resources.report_problem
import es.sebas1705.owlsplit.feature.profile.resources.report_send
import es.sebas1705.owlsplit.feature.profile.resources.report_sent
import es.sebas1705.owlsplit.feature.profile.resources.support
import es.sebas1705.owlsplit.feature.profile.resources.sync_now
import es.sebas1705.owlsplit.feature.profile.resources.sync_offline
import es.sebas1705.owlsplit.feature.profile.resources.sync_pending
import es.sebas1705.owlsplit.feature.profile.resources.sync_running
import es.sebas1705.owlsplit.feature.profile.resources.sync_synced
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How the last "Sync now" ended. */
enum class SyncOutcome { SYNCED, OFFLINE }

data class SupportState(
    val pending: Long = 0,
    val syncing: Boolean = false,
    val outcome: SyncOutcome? = null,
    val reporting: Boolean = false,
    val description: String = "",
    val reported: Boolean = false,
) : MVIBaseState

sealed interface SupportIntent : MVIBaseIntent {
    data object SyncNow : SupportIntent
    data object OpenReport : SupportIntent
    data class DescriptionChanged(val text: String) : SupportIntent
    data object SendReport : SupportIntent
    data object DismissReport : SupportIntent
}

/** "Sync now" and "Report a problem", on the profile screen. */
class SupportViewModel(
    observePending: ObservePendingChanges,
    private val syncNow: SyncNow,
    private val reportProblem: ReportProblem,
) : MVIBaseViewModel<SupportState, SupportIntent>() {

    override fun initState() = SupportState()

    init {
        viewModelScope.launch {
            observePending().collectLatest { pending ->
                updateState { it.copy(pending = pending, outcome = if (pending == 0L) null else it.outcome) }
            }
        }
    }

    override fun intentHandler(intent: SupportIntent): Job = execute {
        when (intent) {
            SupportIntent.SyncNow -> sync()
            SupportIntent.OpenReport -> updateState { it.copy(reporting = true, reported = false) }
            is SupportIntent.DescriptionChanged ->
                updateState { it.copy(description = intent.text.take(ReportProblem.MAX_DESCRIPTION)) }
            SupportIntent.SendReport -> send()
            SupportIntent.DismissReport -> updateState { it.copy(reporting = false, description = "") }
        }
    }

    private suspend fun sync() {
        if (currentState.syncing) return
        updateState { it.copy(syncing = true, outcome = null) }
        val done = syncNow()
        updateState { it.copy(syncing = false, outcome = if (done) SyncOutcome.SYNCED else SyncOutcome.OFFLINE) }
    }

    private suspend fun send() {
        reportProblem(currentState.description)
        updateState { it.copy(reporting = false, description = "", reported = true) }
    }
}

@Composable
fun SupportSection(state: SupportState, onEvent: (SupportIntent) -> Unit) {
    OwlsplitContainer(title = stringResource(Res.string.support)) {
        ActionRow(TablerIcons.Refresh, stringResource(Res.string.sync_now), enabled = !state.syncing) {
            onEvent(SupportIntent.SyncNow)
        }
        val status = when {
            state.syncing -> stringResource(Res.string.sync_running)
            state.outcome == SyncOutcome.OFFLINE -> stringResource(Res.string.sync_offline)
            state.pending > 0 -> pluralStringResource(Res.plurals.sync_pending, state.pending.toInt(), state.pending)
            else -> stringResource(Res.string.sync_synced)
        }
        Text(
            status,
            style = Owlsplit.type.caption,
            color = Owlsplit.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
        )
        ActionRow(TablerIcons.AlertTriangle, stringResource(Res.string.report_problem)) {
            onEvent(SupportIntent.OpenReport)
        }
        if (state.reported) {
            Text(
                stringResource(Res.string.report_sent),
                style = Owlsplit.type.caption,
                color = Owlsplit.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }
    if (state.reporting) ReportDialog(state, onEvent)
}

@Composable
private fun ReportDialog(state: SupportState, onEvent: (SupportIntent) -> Unit) {
    val colors = Owlsplit.colors
    AlertDialog(
        onDismissRequest = { onEvent(SupportIntent.DismissReport) },
        containerColor = colors.surface,
        title = {
            Text(
                stringResource(Res.string.report_problem),
                style = Owlsplit.type.title,
                color = colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    stringResource(Res.string.report_body),
                    style = Owlsplit.type.caption,
                    color = colors.textSecondary
                )
                OwlsplitTextField(
                    value = state.description,
                    onValueChange = { onEvent(SupportIntent.DescriptionChanged(it)) },
                    label = stringResource(Res.string.report_hint),
                )
            }
        },
        confirmButton = {
            TextActionButton(
                stringResource(Res.string.report_send),
                { onEvent(SupportIntent.SendReport) }
            )
        },
        dismissButton = {
            TextActionButton(
                stringResource(Res.string.cancel),
                { onEvent(SupportIntent.DismissReport) }
            )
        },
    )
}
