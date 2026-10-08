package es.sebas1705.owlsplit.feature.groups.detail

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.SettleUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class GroupTab { EXPENSES, BALANCES, SETTLE }

data class GroupState(
    val loaded: Boolean = false,
    val detail: GroupDetail? = null,
    val tab: GroupTab = GroupTab.EXPENSES,
    /** My part of each expense (expense id → minor units), for the list. */
    val myShares: Map<String, Long> = emptyMap(),
    val confirmSettle: SuggestedPayment? = null,
    val confirmLeave: Boolean = false,
    /** The group is no longer here (left, or gone): the screen closes. */
    val closed: Boolean = false,
) : MVIBaseState

sealed interface GroupIntent : MVIBaseIntent {
    data class SelectTab(val tab: GroupTab) : GroupIntent
    data class AskSettle(val payment: SuggestedPayment) : GroupIntent
    data object ConfirmSettle : GroupIntent
    data object ToggleArchive : GroupIntent
    data object AskLeave : GroupIntent
    data object ConfirmLeave : GroupIntent
    data object DismissDialog : GroupIntent
}

class GroupViewModel(
    private val groupId: String,
    private val initialTab: GroupTab,
    observeDetail: ObserveGroupDetail,
    private val settleUp: SettleUp,
    private val actions: GroupActions,
) : MVIBaseViewModel<GroupState, GroupIntent>() {

    override fun initState() = GroupState(tab = initialTab)

    init {
        viewModelScope.launch {
            observeDetail(groupId).collect { detail ->
                val myId = detail?.myMemberId
                val shares = if (detail == null || myId == null) {
                    emptyMap()
                } else {
                    detail.expenses.associate { expense ->
                        expense.id to (ExpenseDrafts.check(ExpenseDrafts.of(expense), detail.members).shares[myId] ?: 0L)
                    }
                }
                updateState {
                    it.copy(
                        loaded = true,
                        detail = detail,
                        myShares = shares,
                        // Leaving removes my uid: the group is no longer mine even if it is still stored.
                        closed = detail == null || (detail.myUid != null && detail.myUid !in detail.group.memberUids),
                    )
                }
            }
        }
    }

    override fun intentHandler(intent: GroupIntent): Job = execute {
        when (intent) {
            is GroupIntent.SelectTab -> updateState { it.copy(tab = intent.tab) }
            is GroupIntent.AskSettle -> updateState { it.copy(confirmSettle = intent.payment) }
            GroupIntent.ConfirmSettle -> settle()
            GroupIntent.ToggleArchive -> currentState.detail?.let { actions.setArchived(groupId, !it.archivedByMe) }
            GroupIntent.AskLeave -> updateState { it.copy(confirmLeave = true) }
            GroupIntent.ConfirmLeave -> {
                updateState { it.copy(confirmLeave = false) }
                actions.leave(groupId)
            }
            GroupIntent.DismissDialog -> updateState { it.copy(confirmSettle = null, confirmLeave = false) }
        }
    }

    private suspend fun settle() {
        val payment = currentState.confirmSettle ?: return
        val currency = currentState.detail?.group?.baseCurrency ?: return
        updateState { it.copy(confirmSettle = null) }
        settleUp(groupId, payment, currency)
    }
}
