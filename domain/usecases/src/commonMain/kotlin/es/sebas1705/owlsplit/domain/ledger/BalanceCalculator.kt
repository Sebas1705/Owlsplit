package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Currency

/** Net balance of every member in the base currency. Positive = is owed. Sums to exactly 0. */
object BalanceCalculator {
    fun balances(
        members: List<LedgerMember>,
        expenses: List<ExpenseEntry>,
        transfers: List<TransferEntry>,
        base: Currency,
    ): Map<String, Long> {
        val totals = members.sortedWith(memberOrder).associateTo(LinkedHashMap()) { it.id to 0L }
        val nets = expenses.map { NetCalculator.expenseNet(it, members, base) } +
            transfers.map { NetCalculator.transferNet(it, members, base) }
        nets.forEach { net -> net.forEach { (id, value) -> totals[id] = totals.getValue(id) + value } }
        check(totals.values.sum() == 0L) { "Balances must sum to zero, got ${totals.values.sum()}" }
        return totals
    }
}
