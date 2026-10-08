package es.sebas1705.owlsplit.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowBackUp
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Pencil
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import compose.icons.tablericons.UserPlus
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.format.dayLabel
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import es.sebas1705.owlsplit.domain.usecases.history.HistoryItem
import es.sebas1705.owlsplit.feature.history.resources.Res
import es.sebas1705.owlsplit.feature.history.resources.activity_title
import es.sebas1705.owlsplit.feature.history.resources.back
import es.sebas1705.owlsplit.feature.history.resources.dismiss
import es.sebas1705.owlsplit.feature.history.resources.empty_body
import es.sebas1705.owlsplit.feature.history.resources.empty_title
import es.sebas1705.owlsplit.feature.history.resources.history_title
import es.sebas1705.owlsplit.feature.history.resources.pending_conflicts
import es.sebas1705.owlsplit.feature.history.resources.reapply
import es.sebas1705.owlsplit.feature.history.resources.reapply_failed
import es.sebas1705.owlsplit.feature.history.resources.restore
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

private const val MESSAGE_MILLIS = 4_000L

/** A group's history ([groupId] with [onBack]) or the Actividad tab ([groupId] null). */
@Composable
fun HistoryRoute(groupId: String?, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<HistoryViewModel>(key = "history-$groupId") { parametersOf(groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state, viewModel::onEvent, onBack, modifier)
}

@Composable
fun HistoryScreen(
    state: HistoryState,
    onEvent: (HistoryIntent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    format: MoneyFormat = LocalMoneyFormat.current,
    today: LocalDate = Today.System(),
) {
    LaunchedEffect(state.reapplyFailed) {
        if (state.reapplyFailed) {
            delay(MESSAGE_MILLIS)
            onEvent(HistoryIntent.DismissMessage)
        }
    }
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(TablerIcons.ArrowLeft, stringResource(Res.string.back), tint = Owlsplit.colors.textPrimary)
                }
            }
            Text(
                stringResource(if (state.acrossGroups) Res.string.activity_title else Res.string.history_title),
                style = if (onBack != null) Owlsplit.type.bodyStrong else Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.m).semantics { heading() },
            )
        }
        if (state.loaded && state.items.isEmpty()) {
            EmptyState(stringResource(Res.string.empty_title), stringResource(Res.string.empty_body))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            if (state.reapplyFailed) {
                item {
                    Text(
                        stringResource(Res.string.reapply_failed),
                        style = Owlsplit.type.body,
                        color = Owlsplit.colors.error
                    )
                }
            }
            if (state.pending.isNotEmpty()) {
                item {
                    OwlsplitContainer(title = stringResource(Res.string.pending_conflicts)) {
                        state.pending.forEach { Entry(it, state.acrossGroups, onEvent, format, today) }
                    }
                }
            }
            state.items.groupBy { it.entry.createdAt.toLocalDate() }.forEach { (day, entries) ->
                item(key = "day-$day") {
                    Text(dayLabel(day, today), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
                }
                item(key = "list-$day") {
                    OwlsplitContainer { entries.forEach { Entry(it, state.acrossGroups, onEvent, format, today) } }
                }
            }
        }
    }
}

@Composable
private fun Entry(
    item: HistoryItem,
    showGroup: Boolean,
    onEvent: (HistoryIntent) -> Unit,
    format: MoneyFormat,
    today: LocalDate
) {
    val colors = Owlsplit.colors
    val conflict = item.entry.action == ChangeAction.CONFLICT
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = Sizes.rowMinHeight
        ).padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            Modifier.size(
                Sizes.iconTile
            ).background(if (conflict) colors.surfaceVariant else colors.accentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconOf(item.entry.action),
                null,
                tint = if (conflict) colors.warning else colors.accentText,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(headline(item), style = Owlsplit.type.body, color = colors.textPrimary)
            changes(item, format, today)?.let { Text(it, style = Owlsplit.type.caption, color = colors.textSecondary) }
            if (showGroup) Text(item.groupName, style = Owlsplit.type.caption, color = colors.textMuted)
            Row {
                if (item.canRestore) {
                    TextActionButton(
                        stringResource(Res.string.restore),
                        { onEvent(HistoryIntent.Restore(item.entry.entityId)) }
                    )
                }
                item.conflictId?.let { id ->
                    TextActionButton(stringResource(Res.string.reapply), { onEvent(HistoryIntent.Reapply(id)) })
                    TextActionButton(
                        stringResource(Res.string.dismiss),
                        { onEvent(HistoryIntent.Dismiss(id)) },
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

private fun iconOf(action: ChangeAction): ImageVector = when (action) {
    ChangeAction.CREATED -> TablerIcons.Plus
    ChangeAction.UPDATED -> TablerIcons.Pencil
    ChangeAction.DELETED -> TablerIcons.Trash
    ChangeAction.RESTORED -> TablerIcons.ArrowBackUp
    ChangeAction.JOINED -> TablerIcons.UserPlus
    ChangeAction.CONFLICT -> TablerIcons.AlertTriangle
}

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
