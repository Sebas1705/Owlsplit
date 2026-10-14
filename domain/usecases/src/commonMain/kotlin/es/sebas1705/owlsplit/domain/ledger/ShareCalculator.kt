package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.Money

/** What each member owes for one expense, in the expense currency. See docs/DOMAIN.md#split. */
object ShareCalculator {
    const val FULL_PERCENT = 10_000L

    fun shares(amountMinor: Long, split: SplitSpec, members: List<LedgerMember>): SplitResult {
        if (amountMinor !in 1..Money.MAX_EXPENSE_MINOR) return SplitResult.Invalid(SplitIssue.AmountOutOfRange)
        val knownIds = members.mapTo(HashSet()) { it.id }
        split.values.keys.firstOrNull { it !in knownIds }?.let {
            return SplitResult.Invalid(SplitIssue.UnknownMember(it))
        }
        if (split.values.values.any { it < 0 }) return SplitResult.Invalid(SplitIssue.NegativeValue)

        val included = members.filter { (split.values[it.id] ?: 0L) > 0L }.sortedWith(memberOrder)
        if (included.isEmpty()) return SplitResult.Invalid(SplitIssue.NoBeneficiary)
        val value = { member: LedgerMember -> split.values.getValue(member.id) }

        return when (split.type) {
            SplitType.EQUAL -> SplitResult.Ok(allocate(amountMinor, included) { 1L })
            SplitType.WEIGHTS -> SplitResult.Ok(allocate(amountMinor, included, value))
            SplitType.PERCENT -> {
                val sum = included.sumOf(value)
                if (sum != FULL_PERCENT) {
                    SplitResult.Invalid(SplitIssue.PercentSumMismatch(FULL_PERCENT - sum))
                } else {
                    SplitResult.Ok(allocate(amountMinor, included, value))
                }
            }
            SplitType.EXACT -> {
                val sum = included.sumOf(value)
                if (sum != amountMinor) {
                    SplitResult.Invalid(SplitIssue.ExactSumMismatch(amountMinor - sum))
                } else {
                    SplitResult.Ok(included.associate { it.id to value(it) })
                }
            }
        }
    }

    private fun allocate(
        total: Long,
        ordered: List<LedgerMember>,
        weight: (LedgerMember) -> Long,
    ): Map<String, Long> =
        ordered.zip(Allocation.byWeights(total, ordered.map(weight)))
            .associate { (member, part) -> member.id to part }
}
