package es.sebas1705.owlsplit.domain.ledger

import es.sebas1705.owlsplit.core.money.Allocation
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LedgerPropertiesTest {

    private data class Scenario(
        val members: List<LedgerMember>,
        val expenses: List<ExpenseEntry>,
        val transfers: List<TransferEntry>,
    )

    private val foreign = listOf(
        Currency.of("USD") to FxRate.parse("0.923456789"),
        Currency.of("JPY") to FxRate.parse("0.0061"),
        Currency.of("KWD") to FxRate.parse("2.987654321"),
    )

    private fun scenario(random: Random): Scenario {
        val members = List(random.nextInt(2, 9)) { LedgerMember("m$it", sortKey = random.nextLong(0, 5)) }
        val expenses = List(random.nextInt(1, 31)) { index ->
            val (currency, rate) = if (random.nextInt(3) == 0) foreign.random(random) else EUR to FxRate.IDENTITY
            val amount = random.nextLong(1, 500_000)
            val payers = members.shuffled(random).take(random.nextInt(1, 3))
            val paid = Allocation.byWeights(amount, payers.map { random.nextLong(1, 5) })
            val included = members.filter { random.nextBoolean() }.ifEmpty { listOf(members.first()) }
            val split = when (SplitType.entries.random(random)) {
                SplitType.EQUAL -> SplitSpec(SplitType.EQUAL, included.associate { it.id to 1L })
                SplitType.WEIGHTS -> SplitSpec(SplitType.WEIGHTS, included.associate { it.id to random.nextLong(1, 6) })
                SplitType.PERCENT -> SplitSpec(
                    SplitType.PERCENT,
                    included.zip(Allocation.byWeights(10_000, included.map { random.nextLong(1, 10) }))
                        .associate { (m, p) -> m.id to p },
                )
                SplitType.EXACT -> SplitSpec(
                    SplitType.EXACT,
                    included.zip(Allocation.byWeights(amount, included.map { random.nextLong(1, 10) }))
                        .associate { (m, p) -> m.id to p },
                )
            }
            ExpenseEntry("e$index", amount, currency, rate, payers.zip(paid).associate { (m, p) -> m.id to p }, split)
        }
        val transfers = List(random.nextInt(0, 4)) { index ->
            val (from, to) = members.shuffled(random).take(2)
            TransferEntry("t$index", from.id, to.id, random.nextLong(1, 100_000), EUR, FxRate.IDENTITY)
        }
        return Scenario(members, expenses, transfers)
    }

    private fun forEachScenario(block: (Scenario, Random) -> Unit) {
        repeat(500) { seed ->
            val random = Random(seed)
            block(scenario(random), random)
        }
    }

    @Test
    fun `every entry nets to zero and so do the balances`() = forEachScenario { s, _ ->
        s.expenses.forEach { assertEquals(0L, NetCalculator.expenseNet(it, s.members, EUR).values.sum(), it.id) }
        assertEquals(0L, BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR).values.sum())
    }

    @Test
    fun `results do not depend on input order`() = forEachScenario { s, random ->
        val reference = BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR)
        val shuffled = BalanceCalculator.balances(
            s.members.shuffled(random),
            s.expenses.shuffled(random),
            s.transfers.shuffled(random),
            EUR,
        )
        assertEquals(reference, shuffled)
        assertEquals(
            DebtSimplifier.simplify(reference, s.members),
            DebtSimplifier.simplify(reference, s.members.shuffled(random)),
        )
    }

    @Test
    fun `suggested payments settle everyone in at most n - 1 steps`() = forEachScenario { s, _ ->
        val balances = BalanceCalculator.balances(s.members, s.expenses, s.transfers, EUR)
        val payments = DebtSimplifier.simplify(balances, s.members)
        val after = balances.toMutableMap()
        payments.forEach {
            assertTrue(it.amountMinor > 0)
            after[it.fromMemberId] = after.getValue(it.fromMemberId) + it.amountMinor
            after[it.toMemberId] = after.getValue(it.toMemberId) - it.amountMinor
        }
        assertTrue(after.values.all { it == 0L }, "Not settled: $after")
        assertTrue(payments.size <= s.members.size - 1)
    }
}
