package es.sebas1705.owlsplit.feature.home

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.datastore.LinkNudgeSettings
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ObservePendingChanges
import es.sebas1705.owlsplit.domain.usecases.account.ShouldSuggestLinking
import es.sebas1705.owlsplit.domain.usecases.groups.HomeGroup
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveHome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Shortcuts that need a group; with several groups the user picks one first. */
enum class QuickAction { EXPENSE, SETTLE }

data class HomeState(
    val loaded: Boolean = false,
    val groups: List<HomeGroup> = emptyList(),
    val currency: String = "EUR",
    val total: Long = 0,
    val otherCurrencies: Map<String, Long> = emptyMap(),
    val pending: Long = 0,
    val suggestLinking: Boolean = false,
    /** Asking which group a quick action is for. */
    val picking: QuickAction? = null,
) : MVIBaseState

sealed interface HomeIntent : MVIBaseIntent {
    data class Pick(val action: QuickAction) : HomeIntent
    data object DismissPicker : HomeIntent
    data object DismissNudge : HomeIntent
}

class HomeViewModel(
    observeHome: ObserveHome,
    observeAccount: ObserveAccount,
    observePending: ObservePendingChanges,
    private val nudge: LinkNudgeSettings,
) : MVIBaseViewModel<HomeState, HomeIntent>() {

    override fun initState() = HomeState()

    init {
        viewModelScope.launch {
            combine(
                observeHome(),
                observeAccount(),
                observePending(),
                nudge.linkNudgeDismissed,
            ) { home, account, pending, dismissed ->
                HomeState(
                    loaded = true,
                    groups = home.groups,
                    currency = home.preferredCurrency,
                    total = home.total,
                    otherCurrencies = home.otherCurrencies,
                    pending = pending,
                    suggestLinking = ShouldSuggestLinking(account.session, home.groups.size, dismissed),
                )
            }.collect { fresh -> updateState { fresh.copy(picking = it.picking) } }
        }
    }

    override fun intentHandler(intent: HomeIntent): Job = execute {
        when (intent) {
            is HomeIntent.Pick -> updateState { it.copy(picking = intent.action) }
            HomeIntent.DismissPicker -> updateState { it.copy(picking = null) }
            HomeIntent.DismissNudge -> nudge.dismissLinkNudge()
        }
    }
}
