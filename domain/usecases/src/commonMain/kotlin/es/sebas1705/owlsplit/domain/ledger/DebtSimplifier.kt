package es.sebas1705.owlsplit.domain.ledger

/**
 * Greedy settlement (as in Settle Up): the largest debtor pays the largest creditor until
 * everyone is at zero. At most n − 1 payments; ties follow member order.
 * See docs/DOMAIN.md#debt-simplification.
 */
object DebtSimplifier {
    fun simplify(balances: Map<String, Long>, members: List<LedgerMember>): List<SuggestedPayment> {
        require(balances.values.sum() == 0L) { "Balances must sum to zero" }
        val rank = members.sortedWith(memberOrder).withIndex().associate { (index, member) -> member.id to index }
        require(balances.keys.all { it in rank }) { "Every balance must belong to a known member" }

        val byAmountThenRank = compareByDescending<Position> { it.amount }.thenBy { rank.getValue(it.memberId) }
        val creditors = balances.filter { it.value > 0 }.map { Position(it.key, it.value) }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { Position(it.key, -it.value) }.toMutableList()

        val payments = mutableListOf<SuggestedPayment>()
        while (debtors.isNotEmpty()) {
            debtors.sortWith(byAmountThenRank)
            creditors.sortWith(byAmountThenRank)
            val debtor = debtors.first()
            val creditor = creditors.first()
            val amount = minOf(debtor.amount, creditor.amount)
            payments += SuggestedPayment(debtor.memberId, creditor.memberId, amount)
            debtor.amount -= amount
            creditor.amount -= amount
            if (debtor.amount == 0L) debtors.removeAt(0)
            if (creditor.amount == 0L) creditors.removeAt(0)
        }
        return payments
    }

    private class Position(val memberId: String, var amount: Long)
}
