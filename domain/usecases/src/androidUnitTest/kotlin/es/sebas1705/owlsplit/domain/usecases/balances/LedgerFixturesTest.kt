package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The web for guests computes balances in JavaScript (firebase/hosting/js/money.js). This writes, from
 * the app's own [groupBalancesOf] and [balanceExplanationOf], the expected results of many random groups to
 * firebase/rules-test/fixtures/ledger.json, which the web's test checks too: both must agree.
 *
 * It fails when the file is out of date; regenerate it with `-Dowlsplit.writeFixtures=true` (or by
 * deleting the file) and commit it.
 */
class LedgerFixturesTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val file = File("../../firebase/rules-test/fixtures/ledger.json")

    @Test
    fun theWebComputesTheSameBalancesAsTheApp() {
        val json = fixtures()
        if (!file.exists() || System.getProperty("owlsplit.writeFixtures") == "true") {
            file.parentFile.mkdirs()
            file.writeText(json)
        }
        assertEquals(json, file.readText(), "Out of date: regenerate ${file.path} (see the class comment)")
    }

    private fun fixtures(): String {
        val random = Random(SEED)
        val cases = (0 until CASES).map { case(random, it) } + oddCase()
        return "{\n  \"cases\": [\n" + cases.joinToString(",\n") + "\n  ]\n}\n"
    }

    @Suppress("LongMethod") // Builds one random group and writes it with its expected results.
    private fun case(random: Random, index: Int): String {
        val base = BASES[random.nextInt(BASES.size)]
        // Few sort keys, so ties fall back on the id.
        val members = (0 until random.nextInt(2, 7)).map { i ->
            Member("m$index-$i-${random.nextInt(1000)}", "g", "M$i", null, 1, null, true, random.nextLong(0, 3), meta)
        }
        val ids = members.map { it.id }
        val expenses = (0 until random.nextInt(1, 13)).map { e ->
            val currency = if (random.nextInt(4) == 0) FOREIGN[random.nextInt(FOREIGN.size)] else base
            val rate = if (currency == base) SCALE else random.nextLong(1_000_000, 900_000_000_000)
            val amount = random.nextLong(1, 500_000)
            val included = ids.shuffled(random).take(random.nextInt(1, ids.size + 1))
            val type = SplitType.entries[random.nextInt(SplitType.entries.size)]
            val values = when (type) {
                SplitType.EQUAL -> included.associateWith { 1L }
                SplitType.WEIGHTS -> included.associateWith { random.nextLong(1, 6) }
                SplitType.EXACT -> included.zip(partition(random, amount, included.size)).toMap()
                SplitType.PERCENT -> included.zip(partition(random, FULL_PERCENT, included.size)).toMap()
            }
            val payers = ids.shuffled(random).take(random.nextInt(1, minOf(3, ids.size) + 1))
            // Now and then an inconsistent entry, which both must leave out.
            val paid = if (random.nextInt(15) == 0) amount + 1 else amount
            Expense(
                "e$index-$e", "g", "E$e", ExpenseCategory.OTHER, null, LocalDate(2026, 10, 1), currency, amount,
                rate, payers.zip(partition(random, paid, payers.size)).toMap(), type, values, null, null, "u", 1, meta,
            )
        }
        val transfers = (0 until random.nextInt(0, 4)).map { t ->
            val (from, to) = ids.shuffled(random).take(2)
            val currency = if (random.nextInt(4) == 0) FOREIGN[random.nextInt(FOREIGN.size)] else base
            val rate = if (currency == base) SCALE else random.nextLong(1_000_000, 900_000_000_000)
            Transfer(
                "t$index-$t", "g", from, to, random.nextLong(1, 100_000), currency, rate,
                LocalDate(2026, 10, 2), null, "u", 1, meta,
            )
        }
        return render(base, members, expenses, transfers)
    }

    /** One group with the entries the app must leave out: unknown currency, rate out of range, overflowing conversion. */
    private fun oddCase(): String {
        val members = listOf(
            Member("ana", "g", "Ana", null, 1, null, true, 0, meta),
            Member("luis", "g", "Luis", null, 1, null, true, 1, meta),
        )
        fun expense(id: String, amount: Long, currency: String, rate: Long) = Expense(
            id, "g", id, ExpenseCategory.OTHER, null, LocalDate(2026, 10, 1), currency, amount, rate,
            mapOf("ana" to amount), SplitType.EQUAL, mapOf("ana" to 1L, "luis" to 1L), null, null, "u", 1, meta,
        )
        val expenses = listOf(
            expense("odd-ok", 1_000, "EUR", SCALE),
            expense("odd-unknown-currency", 1_000, "XYZ", SCALE),
            expense("odd-zero-rate", 1_000, "USD", 0),
            expense("odd-huge-rate", 1_000, "USD", MAX_RATE + 1),
            expense("odd-overflow", MAX_EXPENSE, "JPY", MAX_RATE),
            expense("odd-big-but-fits", MAX_EXPENSE, "USD", MAX_RATE / 1000),
        )
        val day = LocalDate(2026, 10, 2)
        val transfers = listOf(
            Transfer("odd-t-overflow", "g", "ana", "luis", MAX_EXPENSE, "JPY", MAX_RATE, day, null, "u", 1, meta),
            Transfer("odd-t-unknown", "g", "luis", "ana", 500, "ABC", SCALE, day, null, "u", 1, meta),
        )
        return render("EUR", members, expenses, transfers)
    }

    private fun render(base: String, members: List<Member>, expenses: List<Expense>, transfers: List<Transfer>): String {
        val result = groupBalancesOf(base, members, expenses, transfers)
        val explanation = balanceExplanationOf(base, members, expenses, transfers)
        return buildString {
            append("    {\"baseCurrency\": \"$base\", \"members\": [")
            append(members.joinToString { "{\"id\": \"${it.id}\", \"sortKey\": ${it.sortKey}}" })
            append("],\n     \"expenses\": [")
            append(
                expenses.joinToString(",\n       ") {
                    "{\"id\": \"${it.id}\", \"amountMinor\": ${it.amountMinor}, \"currency\": \"${it.currency}\", " +
                        "\"fxRateScaled\": ${it.fxRateScaled}, \"payers\": ${map(it.payers)}, " +
                        "\"splitType\": \"${it.splitType}\", \"splitValues\": ${map(it.splitValues)}}"
                },
            )
            append("],\n     \"transfers\": [")
            append(
                transfers.joinToString {
                    "{\"id\": \"${it.id}\", \"fromMemberId\": \"${it.fromMemberId}\", " +
                        "\"toMemberId\": \"${it.toMemberId}\", \"amountMinor\": ${it.amountMinor}, " +
                        "\"currency\": \"${it.currency}\", \"fxRateScaled\": ${it.fxRateScaled}}"
                },
            )
            append("],\n     \"expected\": {\"balances\": ${map(result.balances)}, \"suggestions\": [")
            append(
                result.suggestedPayments.joinToString {
                    "{\"fromMemberId\": \"${it.fromMemberId}\", \"toMemberId\": \"${it.toMemberId}\", " +
                        "\"amountMinor\": ${it.amountMinor}}"
                },
            )
            append("], \"invalidEntryIds\": [${result.invalidEntryIds.joinToString { "\"$it\"" }}],\n")
            append("       \"explanation\": ${explanationJson(explanation)}}}")
        }
    }

    private fun explanationJson(explanation: BalanceExplanation): String {
        val breakdowns = explanation.members.joinToString(prefix = "{", postfix = "}") {
            "\"${it.memberId}\": {\"paidMinor\": ${it.paidMinor}, \"shareMinor\": ${it.shareMinor}, " +
                "\"sentMinor\": ${it.sentMinor}, \"receivedMinor\": ${it.receivedMinor}}"
        }
        return "{\"breakdowns\": $breakdowns, \"pairwiseDebts\": ${explanation.pairwiseDebts}, " +
            "\"usesConversion\": ${explanation.usesConversion}}"
    }

    /** [total] in [parts] positive pieces (or fewer when it is too small). */
    private fun partition(random: Random, total: Long, parts: Int): List<Long> {
        if (parts <= 1 || total < parts) return listOf(total)
        val cuts = (1 until parts).map { random.nextLong(1, total) }.distinct().sorted()
        return (listOf(0L) + cuts + total).zipWithNext { a, b -> b - a }.filter { it > 0 }
    }

    private fun map(values: Map<String, Long>) = values.entries.joinToString(prefix = "{", postfix = "}") {
        "\"${it.key}\": ${it.value}"
    }

    private companion object {
        const val SEED = 20261016
        const val CASES = 60
        const val SCALE = 1_000_000_000L
        const val FULL_PERCENT = 10_000L
        const val MAX_EXPENSE = 1_000_000_000_000L
        const val MAX_RATE = 900_000L * SCALE
        val BASES = listOf("EUR", "EUR", "USD", "JPY", "KWD")
        val FOREIGN = listOf("USD", "GBP", "JPY", "KWD", "MXN", "CHF")
    }
}
