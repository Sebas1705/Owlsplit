package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.domain.ledger.BalanceExplainer
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment

/** One member's balance as arithmetic, in base-currency minor units. */
data class MemberBreakdown(
    val memberId: String,
    /** Sum of the expenses they paid. */
    val paidMinor: Long,
    /** Sum of their shares of the expenses. */
    val shareMinor: Long,
    /** Recorded payments they made to others. */
    val sentMinor: Long,
    /** Recorded payments others made to them. */
    val receivedMinor: Long,
    /** Positive = is owed. The same value the balances screen shows. */
    val balanceMinor: Long,
) {
    val addsUp: Boolean get() = paidMinor - shareMinor + sentMinor - receivedMinor == balanceMinor
    val isEmpty: Boolean get() = paidMinor == 0L && shareMinor == 0L && sentMinor == 0L && receivedMinor == 0L
}

/** "Where does this come from?": each balance broken down, and why the suggested payments are so few. */
data class BalanceExplanation(
    val baseCurrency: String,
    /** In the order of the given members: active ones always, members who left only with something to show. */
    val members: List<MemberBreakdown>,
    /** Exactly the payments the settle-up screen suggests. */
    val suggestedPayments: List<SuggestedPayment>,
    /** Debts between pairs of members if every expense and payment were settled directly, without simplifying. */
    val pairwiseDebts: Int,
    /** Some amount was converted from another currency at its stored rate. */
    val usesConversion: Boolean,
    val invalidEntryIds: List<String>,
) {
    val settled: Boolean get() = suggestedPayments.isEmpty()

    /** Simplifying saves payments: worth telling ("2 payments instead of 5"). */
    val simplifies: Boolean get() = pairwiseDebts > suggestedPayments.size
}

/**
 * Explains [groupBalancesOf] for the same data: same valid entries, same balances, same suggested payments.
 * See docs/DOMAIN.md#balance-explanation.
 */
fun balanceExplanationOf(
    baseCurrency: String,
    memberList: List<Member>,
    expenseList: List<Expense>,
    transferList: List<Transfer>,
): BalanceExplanation {
    val balances = groupBalancesOf(baseCurrency, memberList, expenseList, transferList)
    val ledger = ledgerOf(baseCurrency, memberList, expenseList, transferList)
    val explained = BalanceExplainer.explain(ledger.members, ledger.expenses, ledger.transfers, ledger.base)
    val members = memberList.mapNotNull { member ->
        val row = explained.breakdowns.getValue(member.id)
        val balance = balances.balances.getValue(member.id)
        check(row.balanceMinor == balance) { "Breakdown of ${member.id} does not match its balance" }
        MemberBreakdown(member.id, row.paidMinor, row.shareMinor, row.sentMinor, row.receivedMinor, balance)
            .takeIf { member.active || !it.isEmpty || balance != 0L }
    }
    val converted = ledger.expenses.any {
        it.currency != ledger.base
    } || ledger.transfers.any { it.currency != ledger.base }
    return BalanceExplanation(
        baseCurrency = baseCurrency,
        members = members,
        suggestedPayments = balances.suggestedPayments,
        pairwiseDebts = explained.pairwiseDebts,
        usesConversion = converted,
        invalidEntryIds = balances.invalidEntryIds,
    )
}
