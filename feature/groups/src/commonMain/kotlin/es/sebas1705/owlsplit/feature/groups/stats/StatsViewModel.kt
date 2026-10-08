package es.sebas1705.owlsplit.feature.groups.stats

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StatsState(
    val loaded: Boolean = false,
    val detail: GroupDetail? = null,
    val stats: GroupStats? = null,
) : MVIBaseState

/** Nothing to ask for yet: the screen only reads (exports are built from the state). */
sealed interface StatsIntent : MVIBaseIntent

/** A group's spending summary, live with every change. */
class StatsViewModel(
    private val groupId: String,
    observeDetail: ObserveGroupDetail,
) : MVIBaseViewModel<StatsState, StatsIntent>() {

    override fun initState() = StatsState()

    init {
        viewModelScope.launch {
            observeDetail(groupId).collect { detail ->
                val stats = detail?.let {
                    GroupStats.of(
                        it.group.baseCurrency,
                        it.members,
                        it.expenses,
                        it.group.startDate,
                        it.group.endDate,
                    )
                }
                updateState { state -> state.copy(loaded = true, detail = detail, stats = stats) }
            }
        }
    }

    override fun intentHandler(intent: StatsIntent): Job = execute {}
}
