package es.sebas1705.owlsplit.feature.expense

import es.sebas1705.owlsplit.core.ui.format.parseDecimal
import es.sebas1705.owlsplit.core.ui.format.formatDecimal
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.ui.components.KeypadKey
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.domain.usecases.expenses.DeleteExpense
import es.sebas1705.owlsplit.domain.usecases.expenses.DraftCheck
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDraft
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpenseDrafts
import es.sebas1705.owlsplit.domain.usecases.expenses.ExpensePayers
import es.sebas1705.owlsplit.domain.usecases.expenses.RepeatUnit
import es.sebas1705.owlsplit.domain.usecases.expenses.SaveExpense
import es.sebas1705.owlsplit.domain.usecases.fx.GetFxRate
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** The exchange rate row, when the expense is not in the group currency. */
data class RateState(
    /** As typed or as received ("0,92345"). */
    val text: String = "",
    /** ECB day of the rate received; null when typed by hand or missing. */
    val day: String? = null,
    /** Received from an expired cache because the network failed. */
    val stale: Boolean = false,
    val loading: Boolean = false,
)

data class ExpenseEditorState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    /** The group currency, in which balances are kept. */
    val baseCurrency: String = "EUR",
    val myMemberId: String? = null,
    /** Who can be in the split: active members, plus inactive ones already in it. */
    val members: List<Member> = emptyList(),
    val amount: AmountInput = AmountInput(),
    val keypadOpen: Boolean = true,
    val draft: ExpenseDraft = ExpenseDraft(),
    val check: DraftCheck = DraftCheck(emptyMap(), null),
    /** Text of each member's value for weights, amounts and percentages, as typed. */
    val valueTexts: Map<String, String> = emptyMap(),
    /** Text of each payer's amount when there are several. */
    val payerTexts: Map<String, String> = emptyMap(),
    val rate: RateState = RateState(),
    val confirmDelete: Boolean = false,
    /** Saved or deleted: the editor closes. */
    val done: Boolean = false,
) : MVIBaseState {
    val currency: String get() = draft.currency
    val foreign: Boolean get() = draft.currency != baseCurrency

    /** The amount in the group currency, for foreign expenses with a rate. */
    val baseEquivalent: Long?
        get() = draft.fxRateScaled?.takeIf { foreign && draft.amountMinor > 0 }?.let {
            Conversion.convert(draft.amountMinor, Currency.of(draft.currency), Currency.of(baseCurrency), FxRate(it))
                .truncated
        }
}

sealed interface ExpenseEditorIntent : MVIBaseIntent {
    data class Key(val key: KeypadKey) : ExpenseEditorIntent
    data class KeypadOpen(val open: Boolean) : ExpenseEditorIntent
    data class TitleChanged(val title: String) : ExpenseEditorIntent
    data class CategoryChanged(val category: ExpenseCategory) : ExpenseEditorIntent
    data class CurrencyChanged(val code: String) : ExpenseEditorIntent
    data class RateChanged(val text: String) : ExpenseEditorIntent
    data class PayerChanged(val memberId: String) : ExpenseEditorIntent
    data class MultiplePayers(val on: Boolean) : ExpenseEditorIntent
    data class PayerAmountChanged(val memberId: String, val text: String) : ExpenseEditorIntent
    data class TypeChanged(val type: SplitType) : ExpenseEditorIntent
    data class Toggle(val memberId: String) : ExpenseEditorIntent
    data class ValueChanged(val memberId: String, val text: String) : ExpenseEditorIntent
    data class DateChanged(val date: LocalDate) : ExpenseEditorIntent
    data class NoteChanged(val note: String) : ExpenseEditorIntent
    data class RepeatChanged(val repeat: RepeatUnit?) : ExpenseEditorIntent
    data object Save : ExpenseEditorIntent
    data object AskDelete : ExpenseEditorIntent
    data object ConfirmDelete : ExpenseEditorIntent
    data object DismissDelete : ExpenseEditorIntent
}

/**
 * New expense ([expenseId] null) or edit. Every change re-runs the same validation that saving
 * uses, so the parts shown are exactly the ones that will be stored. A foreign currency freezes
 * the latest rate, which the user can overwrite.
 */
@Suppress("TooManyFunctions") // One small handler per kind of edit.
class ExpenseEditorViewModel(
    private val groupId: String,
    private val expenseId: String?,
    private val observeDetail: ObserveGroupDetail,
    private val saveExpense: SaveExpense,
    private val deleteExpense: DeleteExpense,
    private val getFxRate: GetFxRate,
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
        val base = detail.group.baseCurrency
        val expense = expenseId?.let { id -> detail.expenses.firstOrNull { it.id == id } }
        val draft = expense?.let(ExpenseDrafts::of) ?: ExpenseDrafts.new(detail.members, detail.myMemberId, base)
        val members = detail.members.filter { it.active || it.id in draft.values || it.id in draft.payers }
        valuesEdited = expense != null
        updateState {
            it.copy(
                loaded = true,
                baseCurrency = base,
                myMemberId = detail.myMemberId,
                members = members,
                amount = AmountInput.of(draft.amountMinor, Currency.of(draft.currency).decimals),
                rate = RateState(text = draft.fxRateScaled?.let(::rateText).orEmpty()),
                payerTexts = draft.payers.mapValues { (_, minor) -> moneyText(minor, draft.currency) },
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
            is ExpenseEditorIntent.CurrencyChanged -> changeCurrency(intent.code)
            is ExpenseEditorIntent.RateChanged -> changeRate(intent.text)
            is ExpenseEditorIntent.PayerChanged -> updateDraft { ExpensePayers.withPayer(it, intent.memberId) }
            is ExpenseEditorIntent.MultiplePayers -> multiplePayers(intent.on)
            is ExpenseEditorIntent.PayerAmountChanged -> changePayerAmount(intent.memberId, intent.text)
            is ExpenseEditorIntent.TypeChanged -> {
                valuesEdited = false
                updateState { it.withDraft(ExpenseDrafts.withType(it.draft, intent.type, it.members)) }
            }
            is ExpenseEditorIntent.Toggle -> toggle(intent.memberId)
            is ExpenseEditorIntent.ValueChanged -> changeValue(intent.memberId, intent.text)
            is ExpenseEditorIntent.DateChanged -> updateDraft { it.copy(date = intent.date) }
            is ExpenseEditorIntent.RepeatChanged -> updateDraft { it.copy(repeat = intent.repeat) }
            is ExpenseEditorIntent.NoteChanged ->
                updateDraft { it.copy(note = intent.note.take(ExpenseDrafts.MAX_NOTE)) }
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
        var draft = ExpensePayers.withAmount(state.draft, amount.minorUnits)
        if (draft.splitType == SplitType.EXACT && !valuesEdited) {
            draft = draft.copy(values = ExpenseDrafts.defaults(SplitType.EXACT, state.included(), amount.minorUnits))
        }
        state.copy(amount = amount).withDraft(draft)
    }

    private suspend fun changeCurrency(code: String) {
        val state = currentState
        if (code == state.draft.currency) return
        val decimals = Currency.of(code).decimals
        // Keep what was typed, cutting decimals the new currency does not have.
        val fraction = state.amount.fraction?.take(decimals)?.takeIf { decimals > 0 }
        val amount = state.amount.copy(decimals = decimals, fraction = fraction)
        val foreign = code != state.baseCurrency
        var draft = ExpensePayers.withAmount(
            state.draft.copy(currency = code, fxRateScaled = if (foreign) null else FxRate.SCALE),
            amount.minorUnits,
        )
        if (draft.splitType == SplitType.EXACT) draft = ExpenseDrafts.withType(draft, SplitType.EXACT, state.members)
        valuesEdited = false
        updateState { it.copy(amount = amount, rate = RateState(loading = foreign)).withDraft(draft) }
        if (!foreign) return
        val quote = getFxRate(code, state.baseCurrency)
        // The user may have picked another currency meanwhile.
        if (currentState.draft.currency != code) return
        updateState {
            val rate = RateState(
                text = quote?.let { q -> rateText(q.rate.scaled) }.orEmpty(),
                day = quote?.day,
                stale = quote?.stale == true,
            )
            it.copy(rate = rate).withDraft(it.draft.copy(fxRateScaled = quote?.rate?.scaled))
        }
    }

    private fun changeRate(text: String) = updateState { state ->
        val scaled = runCatching { FxRate.parse(text).scaled }.getOrNull()
        state.copy(rate = RateState(text = text)).withDraft(state.draft.copy(fxRateScaled = scaled))
    }

    private fun multiplePayers(on: Boolean) = updateState { state ->
        val draft = ExpensePayers.withMultiplePayers(state.draft, on)
        state.copy(payerTexts = draft.payers.mapValues { (_, minor) -> moneyText(minor, draft.currency) })
            .withDraft(draft)
    }

    private fun changePayerAmount(memberId: String, text: String) = updateState { state ->
        val minor = parseDecimal(text, Currency.of(state.currency).decimals) ?: return@updateState state
        state.copy(payerTexts = state.payerTexts + (memberId to text))
            .withDraft(ExpensePayers.withPayerAmount(state.draft, memberId, minor))
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
        if (saveExpense(groupId, expenseId, state.draft, state.baseCurrency, state.members)) {
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
        val next = copy(draft = draft, check = ExpenseDrafts.check(draft, members, baseCurrency))
        if (keepTexts) return next
        val decimals = next.valueDecimals()
        return next.copy(
            valueTexts = draft.values.mapValues { (_, units) -> formatDecimal(units, decimals, decimalSeparator) },
        )
    }

    private fun moneyText(minor: Long, currency: String) =
        formatDecimal(minor, Currency.of(currency).decimals, decimalSeparator)

    private fun rateText(scaled: Long) = FxRate(scaled).toPlainString().replace('.', decimalSeparator)

    private companion object {
        const val PERCENT_DECIMALS = 2
    }
}
