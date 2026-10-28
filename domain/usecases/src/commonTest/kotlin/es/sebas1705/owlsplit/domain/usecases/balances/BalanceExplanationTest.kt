package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalanceExplanationTest {
    private val meta = SyncMeta(1, 0, null, "u", false, SyncState.SYNCED)
    private val day = LocalDate(2026, 10, 1)

    private fun member(id: String, sort: Long, active: Boolean = true) =
        Member(id, "g", id, null, 1, null, active, sort, meta)

    private val ana = member("ana", 0)
    private val luis = member("luis", 1)
    private val marta = member("marta", 2)
    private val trio = listOf(ana, luis, marta)

    @Suppress("LongParameterList")
    private fun expense(
        id: String,
        amount: Long,
        payers: Map<String, Long>,
        values: Map<String, Long>,
        type: SplitType = SplitType.EQUAL,
        currency: String = "EUR",
        rate: Long = SCALE,
        recurrenceId: String? = null,
    ) = Expense(
        id, "g", id, ExpenseCategory.FOOD, null, day, currency, amount, rate, payers, type, values, null,
        recurrenceId, "u", 0, meta,
    )

    private fun transfer(id: String, from: String, to: String, amount: Long, currency: String = "EUR", rate: Long = SCALE) =
        Transfer(id, "g", from, to, amount, currency, rate, day, null, "u", 0, meta)

    private fun equalAmong(vararg ids: String) = ids.associateWith { 1L }

    @Test
    fun `each member's rows add up to the balance the app shows`() {
        val expenses = listOf(
            expense("e1", 9_000, mapOf("ana" to 9_000), equalAmong("ana", "luis", "marta")),
            expense("e2", 3_000, mapOf("luis" to 3_000), equalAmong("luis", "marta")),
        )
        val transfers = listOf(transfer("t1", "marta", "ana", 2_000))

        val explanation = balanceExplanationOf("EUR", trio, expenses, transfers)
        val balances = groupBalancesOf("EUR", trio, expenses, transfers)

        assertEquals(
            listOf(
                MemberBreakdown(
                    "ana",
                    paidMinor = 9_000,
                    shareMinor = 3_000,
                    sentMinor = 0,
                    receivedMinor = 2_000,
                    balanceMinor = 4_000
                ),
                MemberBreakdown(
                    "luis",
                    paidMinor = 3_000,
                    shareMinor = 4_500,
                    sentMinor = 0,
                    receivedMinor = 0,
                    balanceMinor = -1_500
                ),
                MemberBreakdown(
                    "marta",
                    paidMinor = 0,
                    shareMinor = 4_500,
                    sentMinor = 2_000,
                    receivedMinor = 0,
                    balanceMinor = -2_500
                ),
            ),
            explanation.members,
        )
        explanation.members.forEach {
            assertTrue(it.addsUp, "${it.memberId} does not add up")
            assertEquals(balances.balances.getValue(it.memberId), it.balanceMinor)
        }
        assertEquals(0L, explanation.members.sumOf { it.balanceMinor })
        assertEquals(balances.suggestedPayments, explanation.suggestedPayments)
        assertFalse(explanation.usesConversion)
    }

    @Test
    fun `rounding cents follow the ledger's own split`() {
        // 100 cents among three: the ledger gives the extra cent to the first member.
        val expenses = listOf(expense("e1", 100, mapOf("luis" to 100), equalAmong("ana", "luis", "marta")))

        val explanation = balanceExplanationOf("EUR", trio, expenses, emptyList())

        assertEquals(listOf(34L, 33L, 33L), explanation.members.map { it.shareMinor })
        assertEquals(listOf(-34L, 67L, -33L), explanation.members.map { it.balanceMinor })
        assertTrue(explanation.members.all { it.addsUp })
    }

    @Test
    fun `a foreign expense is shown in the group currency and still adds up exactly`() {
        // 10.00 USD at 0.923456789 EUR per USD, three ways, paid by two people.
        val expenses = listOf(
            expense(
                "e1",
                1_000,
                mapOf("ana" to 333, "luis" to 667),
                equalAmong("ana", "luis", "marta"),
                currency = "USD",
                rate = 923_456_789,
            ),
        )
        val transfers = listOf(transfer("t1", "marta", "luis", 500, currency = "GBP", rate = 1_171_234_567))

        val explanation = balanceExplanationOf("EUR", trio, expenses, transfers)
        val balances = groupBalancesOf("EUR", trio, expenses, transfers).balances

        assertTrue(explanation.usesConversion)
        explanation.members.forEach {
            assertTrue(it.addsUp, "${it.memberId} does not add up: $it")
            assertEquals(balances.getValue(it.memberId), it.balanceMinor)
            assertTrue(it.paidMinor >= 0 && it.shareMinor >= 0 && it.sentMinor >= 0 && it.receivedMinor >= 0)
        }
        // 10.00 USD ≈ 9.23 EUR in total, split about 3.08 each.
        assertEquals(923L, explanation.members.sumOf { it.paidMinor })
        assertEquals(923L, explanation.members.sumOf { it.shareMinor })
        assertEquals(585L, explanation.members.first { it.memberId == "marta" }.sentMinor)
        assertEquals(585L, explanation.members.first { it.memberId == "luis" }.receivedMinor)
    }

    @Test
    fun `members who left appear only while they have something to explain`() {
        val gone = member("pablo", 3, active = false)
        val alsoGone = member("zoe", 4, active = false)
        val expenses = listOf(expense("e1", 4_000, mapOf("pablo" to 4_000), equalAmong("ana", "pablo")))

        val explanation = balanceExplanationOf("EUR", trio + gone + alsoGone, expenses, emptyList())

        assertEquals(listOf("ana", "luis", "marta", "pablo"), explanation.members.map { it.memberId })
        assertEquals(2_000L, explanation.members.last().balanceMinor)
    }

    @Test
    fun `repeating expenses count like any other`() {
        val expenses = listOf(
            expense("r1", 6_000, mapOf("ana" to 6_000), equalAmong("ana", "luis", "marta"), recurrenceId = "rec-1"),
            expense("r2", 6_000, mapOf("ana" to 6_000), equalAmong("ana", "luis", "marta"), recurrenceId = "rec-1"),
        )

        val explanation = balanceExplanationOf("EUR", trio, expenses, emptyList())

        assertEquals(12_000L, explanation.members.first().paidMinor)
        assertEquals(listOf(4_000L, 4_000L, 4_000L), explanation.members.map { it.shareMinor })
    }

    @Test
    fun `fewer payments than debts between pairs when simplifying helps`() {
        // A chain: luis owes ana, marta owes luis. Without simplifying: 2 debts; simplified: marta pays ana once.
        val expenses = listOf(
            expense("e1", 1_000, mapOf("ana" to 1_000), mapOf("luis" to 1L)),
            expense("e2", 1_000, mapOf("luis" to 1_000), mapOf("marta" to 1L)),
        )

        val explanation = balanceExplanationOf("EUR", trio, expenses, emptyList())

        assertEquals(2, explanation.pairwiseDebts)
        assertEquals(1, explanation.suggestedPayments.size)
        assertTrue(explanation.simplifies)
    }

    @Test
    fun `debts between the same two people in both directions cancel out`() {
        val expenses = listOf(
            expense("e1", 1_000, mapOf("ana" to 1_000), mapOf("luis" to 1L)),
            expense("e2", 400, mapOf("luis" to 400), mapOf("ana" to 1L)),
        )
        val transfers = listOf(transfer("t1", "luis", "ana", 600))

        val explanation = balanceExplanationOf("EUR", trio, expenses, transfers)

        assertEquals(0, explanation.pairwiseDebts)
        assertTrue(explanation.settled)
        assertFalse(explanation.simplifies)
    }

    @Test
    fun `nothing to simplify when every debt is already direct`() {
        val expenses = listOf(expense("e1", 3_000, mapOf("ana" to 3_000), equalAmong("ana", "luis", "marta")))

        val explanation = balanceExplanationOf("EUR", trio, expenses, emptyList())

        assertEquals(2, explanation.pairwiseDebts)
        assertEquals(2, explanation.suggestedPayments.size)
        assertFalse(explanation.simplifies)
    }

    @Test
    fun `everyone settled gives an empty explanation`() {
        val explanation = balanceExplanationOf("EUR", trio, emptyList(), emptyList())

        assertTrue(explanation.settled)
        assertTrue(explanation.members.all { it.balanceMinor == 0L && it.isEmpty })
    }

    @Test
    fun `inconsistent entries are left out exactly as the balances leave them out`() {
        val expenses = listOf(
            expense("ok", 2_000, mapOf("ana" to 2_000), equalAmong("ana", "luis")),
            expense("bad", 1_000, mapOf("ana" to 999), equalAmong("ana", "luis")),
        )

        val explanation = balanceExplanationOf("EUR", trio, expenses, emptyList())

        assertEquals(listOf("bad"), explanation.invalidEntryIds)
        assertEquals(2_000L, explanation.members.first().paidMinor)
    }

    @Test
    fun `random groups always add up and never need more payments than pairwise debts`() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val base = listOf("EUR", "JPY", "KWD")[random.nextInt(3)]
            val members = (0 until random.nextInt(2, 7)).map { i ->
                member(
                    "m$i",
                    random.nextLong(0, 3),
                    random.nextBoolean() || i == 0
                )
            }
            val ids = members.map { it.id }
            val expenses = (0 until random.nextInt(0, 10)).map { e ->
                val foreign = random.nextInt(3) == 0
                val amount = random.nextLong(1, 300_000)
                val payer = ids.random(random)
                val among = ids.shuffled(random).take(random.nextInt(1, ids.size + 1))
                expense(
                    "e$e",
                    amount,
                    mapOf(payer to amount),
                    among.associateWith { random.nextLong(1, 4) },
                    SplitType.WEIGHTS,
                    currency = if (foreign) "USD" else base,
                    rate = if (foreign) random.nextLong(1_000_000, 900_000_000_000) else SCALE,
                )
            }
            val transfers = (0 until random.nextInt(0, 3)).map { t ->
                val (from, to) = ids.shuffled(random).take(2)
                transfer("t$t", from, to, random.nextLong(1, 50_000))
            }
            assertAgreesWithBalances(base, members, expenses, transfers)
        }
    }

    private companion object {
        const val SCALE = 1_000_000_000L
        const val SEED = 20261028
        const val RANDOM_CASES = 300
    }
}

/** The explanation must tell the same story as [groupBalancesOf], cent by cent. Shared with the fixture test. */
internal fun assertAgreesWithBalances(
    base: String,
    members: List<Member>,
    expenses: List<Expense>,
    transfers: List<Transfer>
) {
    val balances = groupBalancesOf(base, members, expenses, transfers)
    val explanation = balanceExplanationOf(base, members, expenses, transfers)
    explanation.members.forEach {
        assertTrue(it.addsUp, "${it.memberId} does not add up: $it")
        assertEquals(balances.balances.getValue(it.memberId), it.balanceMinor)
        assertTrue(it.paidMinor >= 0 && it.shareMinor >= 0 && it.sentMinor >= 0 && it.receivedMinor >= 0, "$it")
    }
    // Members left out are only those who left with nothing to explain.
    members.filter { m -> explanation.members.none { it.memberId == m.id } }.forEach {
        assertFalse(it.active)
        assertEquals(0L, balances.balances.getValue(it.id))
    }
    assertEquals(0L, explanation.members.sumOf { it.balanceMinor })
    assertEquals(balances.suggestedPayments, explanation.suggestedPayments)
    assertEquals(balances.invalidEntryIds, explanation.invalidEntryIds)
    assertTrue(explanation.pairwiseDebts >= explanation.suggestedPayments.size, "$explanation")
}
