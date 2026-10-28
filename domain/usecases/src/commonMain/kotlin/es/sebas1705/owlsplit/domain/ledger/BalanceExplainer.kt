package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency

/**
 * Where one member's balance comes from, in base-currency minor units:
 * [paidMinor] − [shareMinor] + [sentMinor] − [receivedMinor] = the balance, exactly.
 */
data class LedgerBreakdown(val paidMinor: Long, val shareMinor: Long, val sentMinor: Long, val receivedMinor: Long) {
    val balanceMinor: Long get() = paidMinor - shareMinor + sentMinor - receivedMinor

    operator fun plus(other: LedgerBreakdown) = LedgerBreakdown(
        paidMinor + other.paidMinor,
        shareMinor + other.shareMinor,
        sentMinor + other.sentMinor,
        receivedMinor + other.receivedMinor,
    )

    companion object {
        val Zero = LedgerBreakdown(0, 0, 0, 0)
    }
}

data class LedgerExplanation(
    /** Member id → breakdown, in member order; every balance equals [BalanceCalculator]'s. */
    val breakdowns: Map<String, LedgerBreakdown>,
    /** Pairs of members with a debt left between them when every entry is settled directly (no simplifying). */
    val pairwiseDebts: Int,
)

/**
 * Explains [BalanceCalculator]'s balances. It works from the same per-entry nets ([NetCalculator]), so
 * rounding and currency conversion match cent by cent: only the split of each net into "paid" and
 * "share" is added. See docs/DOMAIN.md#balance-explanation.
 */
object BalanceExplainer {
    fun explain(
        members: List<LedgerMember>,
        expenses: List<ExpenseEntry>,
        transfers: List<TransferEntry>,
        base: Currency,
    ): LedgerExplanation {
        val totals = members.sortedWith(memberOrder).associateTo(LinkedHashMap()) { it.id to LedgerBreakdown.Zero }
        val pairs = HashMap<Pair<String, String>, Long>()
        expenses.forEach { expense ->
            val net = NetCalculator.expenseNet(expense, members, base)
            expenseBreakdown(
                expense,
                net,
                members,
                base
            ).forEach { (id, row) -> totals[id] = totals.getValue(id) + row }
            addDirectDebts(net, members, pairs)
        }
        transfers.forEach { transfer ->
            val net = NetCalculator.transferNet(transfer, members, base)
            net.forEach { (id, value) ->
                val row = if (value > 0) LedgerBreakdown(0, 0, value, 0) else LedgerBreakdown(0, 0, 0, -value)
                totals[id] = totals.getValue(id) + row
            }
            addDirectDebts(net, members, pairs)
        }
        return LedgerExplanation(totals, pairs.values.count { it != 0L })
    }

    /**
     * Splits one expense's base-currency net into paid and share. In the base currency both are the
     * stored amounts. Converted, the total is rounded once and shared among the payers (largest remainder);
     * each share is then paid − net, so the rows add up to the ledger's own net exactly.
     */
    private fun expenseBreakdown(
        expense: ExpenseEntry,
        net: Map<String, Long>,
        members: List<LedgerMember>,
        base: Currency,
    ): Map<String, LedgerBreakdown> {
        val payers = members.sortedWith(memberOrder).map { it.id }.filter { (expense.payers[it] ?: 0L) > 0L }
        val paid: Map<String, Long> = if (expense.currency == base) {
            payers.associateWith { expense.payers.getValue(it) }
        } else {
            val total = rounded(expense.amountMinor, expense.currency, base, expense)
            payers.zip(Allocation.byWeights(total, payers.map { expense.payers.getValue(it) })).toMap()
        }
        return (net.keys + paid.keys).associateWith { id ->
            val memberPaid = paid[id] ?: 0L
            val memberNet = net[id] ?: 0L
            val share = memberPaid - memberNet
            // A tiny converted share can round below zero: then the member simply paid their net.
            if (share < 0) LedgerBreakdown(memberNet, 0, 0, 0) else LedgerBreakdown(memberPaid, share, 0, 0)
        }
    }

    private fun rounded(amountMinor: Long, from: Currency, to: Currency, expense: ExpenseEntry): Long {
        val converted = Conversion.convert(amountMinor, from, to, expense.fxRateToBase)
        return converted.truncated + if (converted.remainder * 2 >= converted.denominator) 1 else 0
    }

    /**
     * Who would owe whom if this entry were settled on its own: each member who is short pays every member who
     * is ahead, in proportion to how far ahead they are. Kept as one signed amount per pair, so debts in
     * both directions cancel out.
     */
    private fun addDirectDebts(
        net: Map<String, Long>,
        members: List<LedgerMember>,
        pairs: MutableMap<Pair<String, String>, Long>
    ) {
        val ordered = members.sortedWith(memberOrder).map { it.id }.filter { (net[it] ?: 0L) != 0L }
        val creditors = ordered.filter { net.getValue(it) > 0 }
        if (creditors.isEmpty()) return
        ordered.filter { net.getValue(it) < 0 }.forEach { debtor ->
            val parts = Allocation.byWeights(-net.getValue(debtor), creditors.map { net.getValue(it) })
            creditors.zip(parts).forEach { (creditor, part) ->
                // Key in a fixed order; positive = the first member owes the second.
                val (key, signed) = if (debtor < creditor) (debtor to creditor) to part else (creditor to debtor) to -part
                pairs[key] = (pairs[key] ?: 0L) + signed
            }
        }
    }
}
