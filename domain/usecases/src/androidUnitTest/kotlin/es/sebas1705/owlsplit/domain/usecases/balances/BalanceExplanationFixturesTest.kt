package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared ledger fixtures (firebase/rules-test/fixtures/ledger.json, written by [LedgerFixturesTest]) cover
 * many random groups with several currencies, splits and inconsistent entries: the explanation of each must
 * agree with the balances it expects, cent by cent.
 */
class BalanceExplanationFixturesTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val day = LocalDate(2026, 10, 1)

    @Test
    fun everyFixtureCaseIsExplainedByItsOwnBalances() {
        val cases = Json.parseToJsonElement(File("../../firebase/rules-test/fixtures/ledger.json").readText())
            .jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 50, "Expected the shared fixtures, found ${cases.size} cases")
        cases.forEach { case ->
            val base = case.string("baseCurrency")
            val members = case.getValue("members").jsonArray.map {
                val m = it.jsonObject
                Member(m.string("id"), "g", m.string("id"), null, 1, null, true, m.long("sortKey"), meta)
            }
            val expenses = case.getValue("expenses").jsonArray.map { it.jsonObject.toExpense() }
            val transfers = case.getValue("transfers").jsonArray.map { it.jsonObject.toTransfer() }
            val expected = case.getValue("expected").jsonObject.getValue("balances").jsonObject

            val explanation = balanceExplanationOf(base, members, expenses, transfers)

            explanation.members.forEach {
                assertEquals(
                    expected.getValue(it.memberId).jsonPrimitive.long,
                    it.balanceMinor,
                    "Balance of ${it.memberId}"
                )
            }
            assertAgreesWithBalances(base, members, expenses, transfers)
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
    private fun JsonObject.longs(key: String) = getValue(key).jsonObject.mapValues { it.value.jsonPrimitive.long }

    private fun JsonObject.toExpense() = Expense(
        string("id"), "g", string("id"), ExpenseCategory.OTHER, null, day, string("currency"), long("amountMinor"),
        long("fxRateScaled"), longs("payers"), SplitType.valueOf(string("splitType")), longs("splitValues"),
        null, null, "u", 1, meta,
    )

    private fun JsonObject.toTransfer() = Transfer(
        string("id"), "g", string("fromMemberId"), string("toMemberId"), long("amountMinor"), string("currency"),
        long("fxRateScaled"), day, null, "u", 1, meta,
    )
}
