package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.money.Money
import kotlin.math.abs

/**
 * Effect of one entry on each member's balance, in the group base currency.
 * Positive = the member is owed money. The values of one entry always sum to exactly 0.
 * See docs/DOMAIN.md#divisas.
 */
object NetCalculator {

    fun expenseNet(expense: ExpenseEntry, members: List<LedgerMember>, base: Currency): Map<String, Long> {
        val shares = when (val result = ShareCalculator.shares(expense.amountMinor, expense.split, members)) {
            is SplitResult.Ok -> result.shares
            is SplitResult.Invalid -> throw InvalidEntryException(expense.id, result.issue)
        }
        validatePayers(expense, members)

        val net = HashMap<String, Long>()
        expense.payers.forEach { (id, paid) -> net[id] = (net[id] ?: 0L) + paid }
        shares.forEach { (id, share) -> net[id] = (net[id] ?: 0L) - share }
        return toBase(net, expense.currency, base, expense.fxRateToBase, members)
    }

    fun transferNet(transfer: TransferEntry, members: List<LedgerMember>, base: Currency): Map<String, Long> {
        val knownIds = members.mapTo(HashSet()) { it.id }
        val issue = when {
            transfer.amountMinor !in 1..Money.MAX_EXPENSE_MINOR -> SplitIssue.AmountOutOfRange
            transfer.fromMemberId == transfer.toMemberId -> SplitIssue.SameMember
            transfer.fromMemberId !in knownIds -> SplitIssue.UnknownMember(transfer.fromMemberId)
            transfer.toMemberId !in knownIds -> SplitIssue.UnknownMember(transfer.toMemberId)
            else -> null
        }
        if (issue != null) throw InvalidEntryException(transfer.id, issue)

        val net = mapOf(transfer.fromMemberId to transfer.amountMinor, transfer.toMemberId to -transfer.amountMinor)
        return toBase(net, transfer.currency, base, transfer.fxRateToBase, members)
    }

    private fun validatePayers(expense: ExpenseEntry, members: List<LedgerMember>) {
        val knownIds = members.mapTo(HashSet()) { it.id }
        val unknown = expense.payers.keys.firstOrNull { it !in knownIds }
        val paid = expense.payers.values.sum()
        val issue = when {
            unknown != null -> SplitIssue.UnknownMember(unknown)
            expense.payers.values.any { it < 0 } -> SplitIssue.NegativeValue
            paid != expense.amountMinor -> SplitIssue.PayersSumMismatch(expense.amountMinor - paid)
            else -> null
        }
        if (issue != null) throw InvalidEntryException(expense.id, issue)
    }

    /**
     * Converts each member's net (which sum to 0) to the base currency, truncating toward zero,
     * then hands out or takes back the leftover units by largest remainder so the result
     * still sums to exactly 0. Ties follow member order.
     */
    private fun toBase(
        net: Map<String, Long>,
        from: Currency,
        base: Currency,
        rate: FxRate,
        members: List<LedgerMember>,
    ): Map<String, Long> {
        if (from == base) return net
        val ordered = members.sortedWith(memberOrder).map { it.id }.filter { it in net }
        val converted = ordered.associateWith { Conversion.convert(net.getValue(it), from, base, rate) }
        val result = converted.mapValuesTo(LinkedHashMap()) { it.value.truncated }

        val leftover = -result.values.sum()
        if (leftover != 0L) {
            val sign = if (leftover > 0) 1L else -1L
            ordered.sortedByDescending { sign * converted.getValue(it).remainder }
                .take(abs(leftover).toInt())
                .forEach { result[it] = result.getValue(it) + sign }
        }
        return result
    }
}
