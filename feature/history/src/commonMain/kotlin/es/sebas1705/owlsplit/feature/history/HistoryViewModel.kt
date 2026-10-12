package es.sebas1705.owlsplit.feature.history

import es.sebas1705.owlsplit.data.models.EntityType
import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.domain.usecases.history.Activity
import es.sebas1705.owlsplit.domain.usecases.history.HistoryItem
import es.sebas1705.owlsplit.domain.usecases.history.ObserveActivity
import es.sebas1705.owlsplit.domain.usecases.history.ObserveHistory
import es.sebas1705.owlsplit.domain.usecases.history.ResolveConflict
import es.sebas1705.owlsplit.domain.usecases.history.RestoreExpense
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class HistoryState(
    val loaded: Boolean = false,
    /** Actividad: entries of every group, with their group's name. */
    val acrossGroups: Boolean = false,
    /** Conflicts still to resolve (Actividad shows them first). */
    val pending: List<HistoryItem> = emptyList(),
    val items: List<HistoryItem> = emptyList(),
    val reapplyFailed: Boolean = false,
) : MVIBaseState

sealed interface HistoryIntent : MVIBaseIntent {
    data class Restore(val type: EntityType, val id: String) : HistoryIntent
    data class Reapply(val conflictId: Long) : HistoryIntent
    data class Dismiss(val conflictId: Long) : HistoryIntent
    data object DismissMessage : HistoryIntent
}

/** A group's history ([groupId] set) or Actividad across groups ([groupId] null). */
class HistoryViewModel(
    private val groupId: String?,
    observeHistory: ObserveHistory,
    observeActivity: ObserveActivity,
    private val restoreExpense: RestoreExpense,
    private val resolveConflict: ResolveConflict,
) : MVIBaseViewModel<HistoryState, HistoryIntent>() {

    override fun initState() = HistoryState(acrossGroups = groupId == null)

    init {
        val source = groupId?.let { id -> observeHistory(id).map { Activity(emptyList(), it) } } ?: observeActivity()
        viewModelScope.launch {
            source.collect { activity ->
                updateState { it.copy(loaded = true, pending = activity.pending, items = activity.items) }
            }
        }
    }

    override fun intentHandler(intent: HistoryIntent): Job = execute {
        when (intent) {
            is HistoryIntent.Restore -> restoreExpense.restore(intent.type, intent.id)
            is HistoryIntent.Reapply ->
                if (!resolveConflict.reapply(intent.conflictId)) updateState { it.copy(reapplyFailed = true) }
            is HistoryIntent.Dismiss -> resolveConflict.dismiss(intent.conflictId)
            HistoryIntent.DismissMessage -> updateState { it.copy(reapplyFailed = false) }
        }
    }
}
