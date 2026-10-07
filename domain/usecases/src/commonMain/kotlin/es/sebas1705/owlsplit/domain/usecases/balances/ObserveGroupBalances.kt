package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.domain.ledger.BalanceCalculator
import es.sebas1705.owlsplit.domain.ledger.DebtSimplifier
import es.sebas1705.owlsplit.domain.ledger.ExpenseEntry
import es.sebas1705.owlsplit.domain.ledger.InvalidEntryException
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.ledger.NetCalculator
import es.sebas1705.owlsplit.domain.ledger.SplitSpec
import es.sebas1705.owlsplit.domain.ledger.SplitType
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.ledger.TransferEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class GroupBalances(
    val baseCurrency: String,
    /** Member id → balance in base-currency minor units; positive = is owed. */
    val balances: Map<String, Long>,
    val suggestedPayments: List<SuggestedPayment>,
    /** Entries left out because they are inconsistent (e.g. synced from a buggy client). */
    val invalidEntryIds: List<String>,
)

/** Live balances of one group, recomputed from local data on every change. */
class ObserveGroupBalances(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
) {
    operator fun invoke(groupId: String): Flow<GroupBalances?> = combine(
        groups.observeGroup(groupId),
        members.observeMembers(groupId),
        expenses.observeExpenses(groupId),
        transfers.observeTransfers(groupId),
    ) { group, memberList, expenseList, transferList ->
        if (group == null) return@combine null
        val base = Currency.of(group.baseCurrency)
        val ledgerMembers = memberList.map { LedgerMember(it.id, it.sortKey) }
        val invalid = mutableListOf<String>()
        val validExpenses = expenseList.map { it.toEntry() }.filter { entry ->
            isValid(invalid) { NetCalculator.expenseNet(entry, ledgerMembers, base) }
        }
        val validTransfers = transferList.map { it.toEntry() }.filter { entry ->
            isValid(invalid) { NetCalculator.transferNet(entry, ledgerMembers, base) }
        }
        val balances = BalanceCalculator.balances(ledgerMembers, validExpenses, validTransfers, base)
        GroupBalances(group.baseCurrency, balances, DebtSimplifier.simplify(balances, ledgerMembers), invalid)
    }

    private inline fun isValid(invalid: MutableList<String>, check: () -> Unit): Boolean =
        try {
            check()
            true
        } catch (e: InvalidEntryException) {
            invalid += e.entryId
            false
        }

    private fun Expense.toEntry() = ExpenseEntry(
        id = id,
        amountMinor = amountMinor,
        currency = Currency.of(currency),
        fxRateToBase = FxRate(fxRateScaled),
        payers = payers,
        split = SplitSpec(SplitType.valueOf(splitType.name), splitValues),
    )

    private fun Transfer.toEntry() = TransferEntry(
        id = id,
        fromMemberId = fromMemberId,
        toMemberId = toMemberId,
        amountMinor = amountMinor,
        currency = Currency.of(currency),
        fxRateToBase = FxRate(fxRateScaled),
    )
}
