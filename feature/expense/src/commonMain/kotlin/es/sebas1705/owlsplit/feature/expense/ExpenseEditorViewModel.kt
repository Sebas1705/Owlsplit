package es.sebas1705.owlsplit.feature.expense

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.KeypadKey
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.domain.usecases.expenses.DeleteExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.DraftCheck
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDraft
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

data class ExpenseEditorState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val currency: String = "EUR",
    val myMemberId: String? = null,
    /** Who can be in the split: active members, plus inactive ones already in it. */
    val members: List<Member> = emptyList(),
    val amount: AmountInput = AmountInput(),
    val keypadOpen: Boolean = true,
    val draft: ExpenseDraft = ExpenseDraft(),
    val check: DraftCheck = DraftCheck(emptyMap(), null),
    /** Text of each member's value for weights, amounts and percentages, as typed. */
    val valueTexts: Map<String, String> = emptyMap(),
    val confirmDelete: Boolean = false,
    /** Saved or deleted: the editor closes. */
    val done: Boolean = false,
) : MVIBaseState

sealed interface ExpenseEditorIntent : MVIBaseIntent {
    data class Key(val key: KeypadKey) : ExpenseEditorIntent
    data class KeypadOpen(val open: Boolean) : ExpenseEditorIntent
    data class TitleChanged(val title: String) : ExpenseEditorIntent
    data class CategoryChanged(val category: ExpenseCategory) : ExpenseEditorIntent
    data class PayerChanged(val memberId: String) : ExpenseEditorIntent
    data class TypeChanged(val type: SplitType) : ExpenseEditorIntent
    data class Toggle(val memberId: String) : ExpenseEditorIntent
    data class ValueChanged(val memberId: String, val text: String) : ExpenseEditorIntent
    data object Save : ExpenseEditorIntent
    data object AskDelete : ExpenseEditorIntent
    data object ConfirmDelete : ExpenseEditorIntent
    data object DismissDelete : ExpenseEditorIntent
}

/**
 * New expense ([expenseId] null) or edit, in the group currency. Every change re-runs the same
 * validation that saving uses, so the parts shown are exactly the ones that will be stored.
 */
class ExpenseEditorViewModel(
    private val groupId: String,
    private val expenseId: String?,
    private val observeDetail: ObserveGroupDetail,
    private val saveExpense: SaveExpense,
    private val deleteExpense: DeleteExpense,
    private val decimalSeparator: Char = ',',
) : MVIBaseViewModel<ExpenseEditorState, ExpenseEditorIntent>() {

    /** Amounts typed per member (EXACT) stop following the total once the user edits one. */
    private var valuesEdited = false

    override fun initState() = ExpenseEditorState(editing = expenseId != null, keypadOpen = expenseId == null)

    init {
        execute { load() }
    }

    private suspend fun load() {
        val detail = observeDetail(groupId).first() ?: return
        val currency = detail.group.baseCurrency
        val decimals = Currency.of(currency).decimals
        val expense = expenseId?.let { id -> detail.expenses.firstOrNull { it.id == id } }
        val draft = expense?.let(ExpenseDrafts::of) ?: ExpenseDrafts.new(detail.members, detail.myMemberId)
        val members = detail.members.filter { it.active || it.id in draft.values || it.id == draft.payerId }
        valuesEdited = expense != null
        updateState {
            it.copy(
                loaded = true,
                currency = currency,
                myMemberId = detail.myMemberId,
                members = members,
                amount = AmountInput.of(draft.amountMinor, decimals),
            ).withDraft(draft)
        }
    }

    override fun intentHandler(intent: ExpenseEditorIntent): Job = execute {
        when (intent) {
            is ExpenseEditorIntent.Key -> typeAmount(intent.key)
            is ExpenseEditorIntent.KeypadOpen -> updateState { it.copy(keypadOpen = intent.open) }
            is ExpenseEditorIntent.TitleChanged ->
                updateDraft { it.copy(title = intent.title.take(ExpenseDrafts.MAX_TITLE)) }
            is ExpenseEditorIntent.CategoryChanged -> updateDraft { it.copy(category = intent.category) }
            is ExpenseEditorIntent.PayerChanged -> updateDraft { it.copy(payerId = intent.memberId) }
            is ExpenseEditorIntent.TypeChanged -> {
                valuesEdited = false
                updateState { it.withDraft(ExpenseDrafts.withType(it.draft, intent.type, it.members)) }
            }
            is ExpenseEditorIntent.Toggle -> toggle(intent.memberId)
            is ExpenseEditorIntent.ValueChanged -> changeValue(intent.memberId, intent.text)
            ExpenseEditorIntent.Save -> save()
            ExpenseEditorIntent.AskDelete -> updateState { it.copy(confirmDelete = true) }
            ExpenseEditorIntent.DismissDelete -> updateState { it.copy(confirmDelete = false) }
            ExpenseEditorIntent.ConfirmDelete -> {
                expenseId?.let { deleteExpense(it) }
                updateState { it.copy(confirmDelete = false, done = true) }
            }
        }
    }

    private fun typeAmount(key: KeypadKey) = updateState { state ->
        val amount = state.amount.apply(key)
        var draft = state.draft.copy(amountMinor = amount.minorUnits)
        if (draft.splitType == SplitType.EXACT && !valuesEdited) {
            draft = draft.copy(values = ExpenseDrafts.defaults(SplitType.EXACT, state.included(), amount.minorUnits))
        }
        state.copy(amount = amount).withDraft(draft)
    }

    private fun toggle(memberId: String) = updateState { state ->
        val member = state.members.firstOrNull { it.id == memberId } ?: return@updateState state
        valuesEdited = false
        state.withDraft(ExpenseDrafts.toggle(state.draft, member, state.members))
    }

    private fun changeValue(memberId: String, text: String) = updateState { state ->
        val units = parseDecimal(text, state.valueDecimals()) ?: return@updateState state
        valuesEdited = true
        state.copy(valueTexts = state.valueTexts + (memberId to text))
            .withDraft(state.draft.copy(values = state.draft.values + (memberId to units)), keepTexts = true)
    }

    private fun updateDraft(change: (ExpenseDraft) -> ExpenseDraft) = updateState { it.withDraft(change(it.draft)) }

    private suspend fun save() {
        val state = currentState
        if (!state.check.canSave) return
        if (saveExpense(groupId, expenseId, state.draft, state.currency, state.members)) {
            updateState { it.copy(done = true) }
        }
    }

    private fun ExpenseEditorState.included() = members.filter { it.id in draft.values }

    /** Weights are whole numbers; amounts follow the currency; percentages have two decimals. */
    private fun ExpenseEditorState.valueDecimals() = when (draft.splitType) {
        SplitType.EXACT -> Currency.of(currency).decimals
        SplitType.PERCENT -> PERCENT_DECIMALS
        SplitType.EQUAL, SplitType.WEIGHTS -> 0
    }

    private fun ExpenseEditorState.withDraft(draft: ExpenseDraft, keepTexts: Boolean = false): ExpenseEditorState {
        val next = copy(draft = draft, check = ExpenseDrafts.check(draft, members))
        if (keepTexts) return next
        val decimals = next.valueDecimals()
        return next.copy(
            valueTexts = draft.values.mapValues { (_, units) -> formatDecimal(units, decimals, decimalSeparator) },
        )
    }

    private companion object {
        const val PERCENT_DECIMALS = 2
    }
}
