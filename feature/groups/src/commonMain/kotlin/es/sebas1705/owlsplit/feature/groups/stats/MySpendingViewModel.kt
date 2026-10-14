package es.sebas1705.owlsplit.feature.groups.stats

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import es.sebas1705.owlsplit.domain.usecases.stats.MySpending
import es.sebas1705.owlsplit.domain.usecases.stats.ObserveMySpending
import es.sebas1705.owlsplit.domain.usecases.stats.SpendingPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MySpendingState(
    val loaded: Boolean = false,
    val period: SpendingPeriod = SpendingPeriod.YEAR,
    val spending: MySpending? = null,
) : MVIBaseState

sealed interface MySpendingIntent : MVIBaseIntent {
    data class SelectPeriod(val period: SpendingPeriod) : MySpendingIntent
}

/** The Statistics tab: what I spent across all my groups, live with every change. */
class MySpendingViewModel(
    observe: ObserveMySpending,
    private val today: Today = Today.System,
) : MVIBaseViewModel<MySpendingState, MySpendingIntent>() {

    private val period = MutableStateFlow(SpendingPeriod.YEAR)

    override fun initState() = MySpendingState()

    init {
        viewModelScope.launch {
            combine(observe(), period) { source, chosen ->
                chosen to MySpending.of(source, chosen, today())
            }.collect { (chosen, spending) ->
                updateState { it.copy(loaded = true, period = chosen, spending = spending) }
            }
        }
    }

    override fun intentHandler(intent: MySpendingIntent): Job = execute {
        when (intent) {
            is MySpendingIntent.SelectPeriod -> period.value = intent.period
        }
    }
}
