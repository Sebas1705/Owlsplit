package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate

/** A group member as the ledger sees it. [sortKey] then [id] give the deterministic order. */
data class LedgerMember(val id: String, val sortKey: Long)

internal val memberOrder: Comparator<LedgerMember> = compareBy({ it.sortKey }, { it.id })

enum class SplitType { EQUAL, WEIGHTS, EXACT, PERCENT }

/**
 * How an expense is shared. Members with a value > 0 take part.
 * EQUAL: the value only marks participation. WEIGHTS: integer weights.
 * EXACT: minor units. PERCENT: hundredths of a percent (10 000 = 100 %).
 */
data class SplitSpec(val type: SplitType, val values: Map<String, Long>)

data class ExpenseEntry(
    val id: String,
    val amountMinor: Long,
    val currency: Currency,
    /** Base-currency units per one unit of [currency]; ignored when [currency] is the base. */
    val fxRateToBase: FxRate,
    val payers: Map<String, Long>,
    val split: SplitSpec,
)

data class TransferEntry(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: Currency,
    val fxRateToBase: FxRate,
)

data class SuggestedPayment(val fromMemberId: String, val toMemberId: String, val amountMinor: Long)

sealed interface SplitIssue {
    data object AmountOutOfRange : SplitIssue
    data object NoBeneficiary : SplitIssue
    data object NegativeValue : SplitIssue
    data object SameMember : SplitIssue
    data class UnknownMember(val memberId: String) : SplitIssue

    /** Positive: still missing; negative: too much. */
    data class ExactSumMismatch(val missingMinor: Long) : SplitIssue
    data class PercentSumMismatch(val missingHundredths: Long) : SplitIssue
    data class PayersSumMismatch(val missingMinor: Long) : SplitIssue
}

sealed interface SplitResult {
    data class Ok(val shares: Map<String, Long>) : SplitResult
    data class Invalid(val issue: SplitIssue) : SplitResult
}

class InvalidEntryException(val entryId: String, val issue: SplitIssue) :
    IllegalArgumentException("Entry $entryId is invalid: $issue")
