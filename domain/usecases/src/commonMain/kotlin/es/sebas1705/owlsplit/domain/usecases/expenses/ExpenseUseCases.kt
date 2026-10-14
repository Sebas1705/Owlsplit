package es.sebas1705.owlsplit.domain.usecases.expenses

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.ledger.ShareCalculator
import es.sebas1705.owlsplit.domain.ledger.SplitIssue
import es.sebas1705.owlsplit.domain.ledger.SplitResult
import es.sebas1705.owlsplit.domain.ledger.SplitSpec
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import es.sebas1705.owlsplit.domain.ledger.SplitType as LedgerSplitType

/**
 * An expense being edited. [values] mean what [splitType] says (see `SplitSpec`); a member takes
 * part when it has an entry, even at 0 while amounts are being typed (0 is left out when saving).
 * With one payer, [payers] holds them with the whole amount; with [multiplePayers] each amount is typed.
 */
data class ExpenseDraft(
    val amountMinor: Long = 0,
    val title: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val payers: Map<String, Long> = emptyMap(),
    val multiplePayers: Boolean = false,
    val splitType: SplitType = SplitType.EQUAL,
    val values: Map<String, Long> = emptyMap(),
    /** Kept from the expense being edited; new ones are dated today unless changed. */
    val date: LocalDate? = null,
    val note: String? = null,
    val currency: String = "EUR",
    /** Group-currency units per one unit of [currency] (fixed point, 10⁹); null while missing. */
    val fxRateScaled: Long? = FxRate.SCALE,
    /** How often it repeats; null: once. */
    val repeat: RepeatUnit? = null,
) {
    /** The single payer, when there is one. */
    val payerId: String? get() = if (multiplePayers) null else payers.keys.singleOrNull()
}

/** Why a draft cannot be saved yet, in the order the user is asked to fix them. */
sealed interface DraftProblem {
    data object MissingAmount : DraftProblem
    data object MissingRate : DraftProblem
    data object MissingTitle : DraftProblem
    data object NoPayer : DraftProblem

    /** Positive: payers still have to add up [missingMinor]; negative: they pay too much. */
    data class PayersMismatch(val missingMinor: Long) : DraftProblem
    data class Split(val issue: SplitIssue) : DraftProblem
}

data class DraftCheck(
    /** Each member's part as it will be saved; empty while the split is invalid. */
    val shares: Map<String, Long>,
    val problem: DraftProblem?,
) {
    val canSave: Boolean get() = problem == null
}

/** Defaults and live validation for the expense editor (docs/DOMAIN.md, "Split"). */
object ExpenseDrafts {
    const val MAX_TITLE = 80
    const val MAX_NOTE = 500

    /** A new expense in the group currency: paid by me (or the first active member), shared equally. */
    fun new(members: List<Member>, myMemberId: String?, currency: String = "EUR"): ExpenseDraft {
        val active = members.filter { it.active }
        val payer = myMemberId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
        return ExpenseDraft(
            payers = payer?.let { mapOf(it to 0L) }.orEmpty(),
            values = defaults(SplitType.EQUAL, active, 0),
            currency = currency,
        )
    }

    fun of(expense: Expense): ExpenseDraft = ExpenseDraft(
        amountMinor = expense.amountMinor,
        title = expense.title,
        category = expense.category,
        payers = expense.payers,
        multiplePayers = expense.payers.size > 1,
        splitType = expense.splitType,
        values = expense.splitValues,
        date = expense.date,
        note = expense.note,
        currency = expense.currency,
        fxRateScaled = expense.fxRateScaled,
        repeat = Recurrence.parse(expense.recurrenceId)?.takeUnless { it.stopped }?.unit,
    )

    /** Switching type keeps who takes part and fills sensible values for the new type. */
    fun withType(draft: ExpenseDraft, type: SplitType, members: List<Member>): ExpenseDraft {
        val included = members.filter { it.id in draft.values }.ifEmpty { members.filter { it.active } }
        return draft.copy(splitType = type, values = defaults(type, included, draft.amountMinor))
    }

    /** Includes or excludes a member, recomputing the defaults of the current type. */
    fun toggle(draft: ExpenseDraft, member: Member, members: List<Member>): ExpenseDraft {
        val included = members.filter { it.id in draft.values }.map { it.id }.toMutableSet()
        if (!included.add(member.id)) included.remove(member.id)
        return draft.copy(values = defaults(draft.splitType, members.filter { it.id in included }, draft.amountMinor))
    }

    fun defaults(type: SplitType, included: List<Member>, amountMinor: Long): Map<String, Long> = when (type) {
        SplitType.EQUAL -> included.associate { it.id to 1L }
        SplitType.WEIGHTS -> included.associate { it.id to it.defaultWeight }
        SplitType.EXACT -> evenly(amountMinor, included)
        SplitType.PERCENT -> evenly(ShareCalculator.FULL_PERCENT, included)
    }

    /** [baseCurrency] is the group's: other currencies need a rate. */
    fun check(draft: ExpenseDraft, members: List<Member>, baseCurrency: String = draft.currency): DraftCheck {
        val ledger = members.map { LedgerMember(it.id, it.sortKey) }
        val split = ShareCalculator.shares(
            draft.amountMinor,
            SplitSpec(LedgerSplitType.valueOf(draft.splitType.name), draft.values),
            ledger,
        )
        val shares = (split as? SplitResult.Ok)?.shares.orEmpty()
        val paid = draft.payers.values.sum()
        val problem = when {
            draft.amountMinor <= 0 -> DraftProblem.MissingAmount
            draft.currency != baseCurrency && draft.fxRateScaled == null -> DraftProblem.MissingRate
            split is SplitResult.Invalid -> DraftProblem.Split(split.issue)
            draft.title.isBlank() -> DraftProblem.MissingTitle
            draft.payers.isEmpty() || draft.payers.keys.any { id -> members.none { it.id == id } } -> DraftProblem.NoPayer
            draft.multiplePayers && paid != draft.amountMinor -> DraftProblem.PayersMismatch(draft.amountMinor - paid)
            else -> null
        }
        return DraftCheck(shares, problem)
    }

    private fun evenly(total: Long, included: List<Member>): Map<String, Long> {
        if (included.isEmpty()) return emptyMap()
        val ordered = included.sortedWith(compareBy({ it.sortKey }, { it.id }))
        // Zero values would drop members from the split: keep at least a marker until an amount exists.
        if (total <= 0) return ordered.associate { it.id to 0L }
        return ordered.zip(Allocation.byWeights(total, ordered.map { 1L })).associate { (m, part) -> m.id to part }
    }
}

/** Who paid an expense and how much, kept consistent with its total. */
object ExpensePayers {
    /** A new total: a single payer follows it. */
    fun withAmount(draft: ExpenseDraft, amountMinor: Long): ExpenseDraft {
        val payers = draft.payerId?.let { mapOf(it to amountMinor) } ?: draft.payers
        return draft.copy(amountMinor = amountMinor, payers = payers)
    }

    fun withPayer(draft: ExpenseDraft, memberId: String): ExpenseDraft =
        draft.copy(payers = mapOf(memberId to draft.amountMinor), multiplePayers = false)

    /** Turning several payers on keeps the current one with the whole amount; off keeps the largest. */
    fun withMultiplePayers(draft: ExpenseDraft, on: Boolean): ExpenseDraft {
        val main = draft.payers.maxByOrNull { it.value }?.key ?: return draft.copy(multiplePayers = on)
        return draft.copy(multiplePayers = on, payers = mapOf(main to draft.amountMinor))
    }

    /** One payer's amount when there are several; 0 removes them. */
    fun withPayerAmount(draft: ExpenseDraft, memberId: String, amountMinor: Long): ExpenseDraft =
        draft.copy(payers = if (amountMinor > 0) draft.payers + (memberId to amountMinor) else draft.payers - memberId)
}

/** Saves a draft; false (and nothing saved) if it is not valid. */
class SaveExpense(
    private val expenses: ExpenseRepository,
    private val today: Today = Today.System,
    private val newSeriesId: () -> String = ::randomSeriesId,
) {
    suspend operator fun invoke(
        groupId: String,
        expenseId: String?,
        draft: ExpenseDraft,
        baseCurrency: String,
        members: List<Member>,
    ): Boolean {
        if (!ExpenseDrafts.check(draft, members, baseCurrency).canSave) return false
        val single = draft.payerId
        val input = ExpenseInput(
            title = draft.title.trim().take(ExpenseDrafts.MAX_TITLE),
            category = draft.category,
            note = draft.note?.trim()?.take(ExpenseDrafts.MAX_NOTE)?.ifEmpty { null },
            date = draft.date ?: today(),
            currency = draft.currency,
            amountMinor = draft.amountMinor,
            // The group currency is always 1; others were frozen when the currency was chosen.
            fxRateScaled = if (draft.currency == baseCurrency) FxRate.SCALE else draft.fxRateScaled!!,
            payers = if (single != null) mapOf(single to draft.amountMinor) else draft.payers.filterValues { it > 0 },
            splitType = draft.splitType,
            splitValues = draft.values.filterValues { it > 0 },
            recurrenceId = recurrenceOf(groupId, expenseId, draft.repeat),
        )
        if (expenseId == null) expenses.create(groupId, input) else expenses.update(expenseId, input)
        return true
    }

    /**
     * The series the saved expense belongs to. Turning repetition off marks this expense as
     * stopped, which stops its whole series. Choosing another period starts a new series from this
     * expense and stops the old one on its latest other expense.
     */
    private suspend fun recurrenceOf(groupId: String, expenseId: String?, repeat: RepeatUnit?): String? {
        val old = expenseId?.let { expenses.get(it) }?.recurrenceId?.let(Recurrence::parse)
        return when {
            old == null -> repeat?.let { Recurrence(newSeriesId(), it).encode() }
            repeat == null -> old.copy(stopped = true).encode()
            !old.stopped && old.unit == repeat -> old.encode()
            else -> {
                if (!old.stopped) stopSeries(groupId, old, except = expenseId)
                Recurrence(newSeriesId(), repeat).encode()
            }
        }
    }

    private suspend fun stopSeries(groupId: String, series: Recurrence, except: String?) {
        val other = expenses.observeExpenses(groupId, includeDeleted = true).first()
            .filter { it.id != except && Recurrence.parse(it.recurrenceId)?.seriesId == series.seriesId }
            .maxByOrNull { it.date } ?: return
        expenses.update(other.id, other.toInput(recurrenceId = series.copy(stopped = true).encode()))
    }
}

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private fun randomSeriesId(): String = kotlin.uuid.Uuid.random().toString()

/** An expense as repository input, to rewrite it (or a copy) with a few changes. */
internal fun Expense.toInput(date: LocalDate = this.date, recurrenceId: String? = this.recurrenceId) = ExpenseInput(
    title = title, category = category, note = note, date = date, currency = currency, amountMinor = amountMinor,
    fxRateScaled = fxRateScaled, payers = payers, splitType = splitType, splitValues = splitValues,
    recurrenceId = recurrenceId,
)

class DeleteExpense(private val expenses: ExpenseRepository) {
    suspend operator fun invoke(expenseId: String) = expenses.delete(expenseId)
}
