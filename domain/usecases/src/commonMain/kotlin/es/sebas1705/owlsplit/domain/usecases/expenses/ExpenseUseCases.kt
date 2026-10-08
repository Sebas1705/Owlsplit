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
import kotlinx.datetime.LocalDate
import es.sebas1705.owlsplit.domain.ledger.SplitType as LedgerSplitType

/**
 * An expense being edited. [values] mean what [splitType] says (see `SplitSpec`); a member takes
 * part when it has an entry, even at 0 while amounts are being typed (0 is left out when saving).
 */
data class ExpenseDraft(
    val amountMinor: Long = 0,
    val title: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val payerId: String? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val values: Map<String, Long> = emptyMap(),
    /** Kept from the expense being edited; new ones are dated today. */
    val date: LocalDate? = null,
    val note: String? = null,
)

/** Why a draft cannot be saved yet, in the order the user is asked to fix them. */
sealed interface DraftProblem {
    data object MissingAmount : DraftProblem
    data object MissingTitle : DraftProblem
    data object NoPayer : DraftProblem
    data class Split(val issue: SplitIssue) : DraftProblem
}

data class DraftCheck(
    /** Each member's part as it will be saved; empty while the split is invalid. */
    val shares: Map<String, Long>,
    val problem: DraftProblem?,
) {
    val canSave: Boolean get() = problem == null
}

/** Defaults and live validation for the expense editor (docs/DOMAIN.md, "Reparto"). */
object ExpenseDrafts {
    const val MAX_TITLE = 80

    /** A new expense: paid by me (or the first active member), shared equally between active members. */
    fun new(members: List<Member>, myMemberId: String?): ExpenseDraft {
        val active = members.filter { it.active }
        val payer = myMemberId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
        return ExpenseDraft(payerId = payer, values = defaults(SplitType.EQUAL, active, 0))
    }

    fun of(expense: Expense): ExpenseDraft = ExpenseDraft(
        amountMinor = expense.amountMinor,
        title = expense.title,
        category = expense.category,
        payerId = expense.payers.keys.singleOrNull() ?: expense.payers.maxByOrNull { it.value }?.key,
        splitType = expense.splitType,
        values = expense.splitValues,
        date = expense.date,
        note = expense.note,
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

    fun check(draft: ExpenseDraft, members: List<Member>): DraftCheck {
        val ledger = members.map { LedgerMember(it.id, it.sortKey) }
        val split = ShareCalculator.shares(
            draft.amountMinor,
            SplitSpec(LedgerSplitType.valueOf(draft.splitType.name), draft.values),
            ledger,
        )
        val shares = (split as? SplitResult.Ok)?.shares.orEmpty()
        val problem = when {
            draft.amountMinor <= 0 -> DraftProblem.MissingAmount
            split is SplitResult.Invalid -> DraftProblem.Split(split.issue)
            draft.title.isBlank() -> DraftProblem.MissingTitle
            draft.payerId == null || members.none { it.id == draft.payerId } -> DraftProblem.NoPayer
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

/** Saves a draft in the group currency; false (and nothing saved) if it is not valid. */
class SaveExpense(private val expenses: ExpenseRepository, private val today: Today = Today.System) {
    suspend operator fun invoke(
        groupId: String,
        expenseId: String?,
        draft: ExpenseDraft,
        currency: String,
        members: List<Member>,
    ): Boolean {
        if (!ExpenseDrafts.check(draft, members).canSave) return false
        val input = ExpenseInput(
            title = draft.title.trim().take(ExpenseDrafts.MAX_TITLE),
            category = draft.category,
            note = draft.note,
            date = draft.date ?: today(),
            currency = currency,
            amountMinor = draft.amountMinor,
            fxRateScaled = FxRate.SCALE,
            payers = mapOf(draft.payerId!! to draft.amountMinor),
            splitType = draft.splitType,
            splitValues = draft.values.filterValues { it > 0 },
        )
        if (expenseId == null) expenses.create(groupId, input) else expenses.update(expenseId, input)
        return true
    }
}

class DeleteExpense(private val expenses: ExpenseRepository) {
    suspend operator fun invoke(expenseId: String) = expenses.delete(expenseId)
}
