package es.sebas1705.owlsplit.feature.groups.detail

import es.sebas1705.owlsplit.domain.usecases.groups.DeleteTransfer
import es.sebas1705.owlsplit.data.models.Transfer
import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.balances.BalanceExplanation
import es.sebas1705.owlsplit.domain.usecases.balances.balanceExplanationOf
import es.sebas1705.owlsplit.domain.usecases.history.ExpenseFilter
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.expenses.GenerateRecurring
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
    val filter: ExpenseFilter = ExpenseFilter(),
    val confirmSettle: SuggestedPayment? = null,
    /** Second step of settling: recording a payment is the one action people regret. */
    val finalSettle: SuggestedPayment? = null,
    val confirmLeave: Boolean = false,
    /** Second step of leaving: there is no way back without a new invite. */
    val finalLeave: Boolean = false,
    /** A recorded payment opened from the list, and whether its deletion is being confirmed. */
    val openTransfer: Transfer? = null,
    val confirmDeleteTransfer: Boolean = false,
    /** A payment owed to me whose reminder is waiting for the share sheet (the screen sends it once). */
    val reminder: SuggestedPayment? = null,
    /** "Where does this come from?" while open; it follows the data like the rest of the screen. */
    val explanation: BalanceExplanation? = null,
    /** The group is no longer here (left, or gone): the screen closes. */
    val closed: Boolean = false,
) : MVIBaseState {
    /** The expenses list after search and filters. */
    val visibleExpenses: List<Expense> get() = detail?.expenses?.let(filter::apply).orEmpty()
}

sealed interface GroupIntent : MVIBaseIntent {
    data class SelectTab(val tab: GroupTab) : GroupIntent
    data class Search(val query: String) : GroupIntent
    data class FilterMember(val memberId: String?) : GroupIntent
    data class FilterCategory(val category: ExpenseCategory?) : GroupIntent
    data object ClearFilters : GroupIntent
    data class AskSettle(val payment: SuggestedPayment) : GroupIntent
    data object ConfirmSettle : GroupIntent
    data object FinalSettle : GroupIntent

    /** Share a friendly reminder with whoever owes me [payment]; ignored when it is not owed to me. */
    data class Remind(val payment: SuggestedPayment) : GroupIntent

    /** The share sheet opened (or the message was copied): the reminder is done. */
    data object ReminderSent : GroupIntent
    data class OpenTransfer(val transfer: Transfer) : GroupIntent
    data object AskDeleteTransfer : GroupIntent
    data object ConfirmDeleteTransfer : GroupIntent
    data object ToggleArchive : GroupIntent
    data object AskLeave : GroupIntent
    data object ConfirmLeave : GroupIntent
    data object FinalLeave : GroupIntent

    /** Open "Where does this come from?" (DismissDialog closes it). */
    data object ShowExplanation : GroupIntent
    data object DismissDialog : GroupIntent
}

class GroupViewModel(
    private val groupId: String,
    private val initialTab: GroupTab,
    observeDetail: ObserveGroupDetail,
    private val settleUp: SettleUp,
    private val actions: GroupActions,
    private val deleteTransfer: DeleteTransfer? = null,
    /** Adds the repeating expenses that are due (null in tests that do not need it). */
    generateRecurring: GenerateRecurring? = null,
) : MVIBaseViewModel<GroupState, GroupIntent>() {

    override fun initState() = GroupState(tab = initialTab)

    init {
        generateRecurring?.let { generate -> execute { generate(groupId) } }
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
                        explanation = it.explanation?.let { detail?.let(::explain) },
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
            is GroupIntent.Search -> updateState {
                it.copy(
                    filter = it.filter.copy(query = intent.query.take(MAX_QUERY))
                )
            }
            is GroupIntent.FilterMember -> updateState { it.copy(filter = it.filter.copy(memberId = intent.memberId)) }
            is GroupIntent.FilterCategory -> updateState {
                it.copy(
                    filter = it.filter.copy(category = intent.category)
                )
            }
            GroupIntent.ClearFilters -> updateState { it.copy(filter = ExpenseFilter()) }
            is GroupIntent.AskSettle -> updateState { it.copy(confirmSettle = intent.payment) }
            GroupIntent.ConfirmSettle -> updateState { it.copy(finalSettle = it.confirmSettle, confirmSettle = null) }
            GroupIntent.FinalSettle -> settle()
            is GroupIntent.Remind -> updateState {
                val owedToMe = it.detail?.myMemberId?.let { me -> me == intent.payment.toMemberId } == true
                if (owedToMe) it.copy(reminder = intent.payment) else it
            }
            GroupIntent.ReminderSent -> updateState { it.copy(reminder = null) }
            is GroupIntent.OpenTransfer -> updateState { it.copy(openTransfer = intent.transfer) }
            GroupIntent.AskDeleteTransfer -> updateState { it.copy(confirmDeleteTransfer = true) }
            GroupIntent.ConfirmDeleteTransfer -> {
                val transfer = currentState.openTransfer
                updateState { it.copy(openTransfer = null, confirmDeleteTransfer = false) }
                transfer?.let { deleteTransfer?.invoke(it.id) }
            }
            GroupIntent.ShowExplanation -> updateState { it.copy(explanation = it.detail?.let(::explain)) }
            GroupIntent.ToggleArchive -> currentState.detail?.let { actions.setArchived(groupId, !it.archivedByMe) }
            GroupIntent.AskLeave -> updateState { it.copy(confirmLeave = true) }
            GroupIntent.ConfirmLeave -> updateState { it.copy(confirmLeave = false, finalLeave = true) }
            GroupIntent.FinalLeave -> {
                updateState { it.copy(finalLeave = false) }
                actions.leave(groupId)
            }
            GroupIntent.DismissDialog -> updateState {
                it.copy(
                    confirmSettle = null,
                    finalSettle = null,
                    confirmLeave = false,
                    finalLeave = false,
                    openTransfer = null,
                    confirmDeleteTransfer = false,
                    explanation = null,
                )
            }
        }
    }

    private suspend fun settle() {
        val payment = currentState.finalSettle ?: return
        val currency = currentState.detail?.group?.baseCurrency ?: return
        updateState { it.copy(finalSettle = null) }
        settleUp(groupId, payment, currency)
    }
}

private const val MAX_QUERY = 80

private fun explain(detail: GroupDetail): BalanceExplanation =
    balanceExplanationOf(detail.group.baseCurrency, detail.members, detail.expenses, detail.transfers)
