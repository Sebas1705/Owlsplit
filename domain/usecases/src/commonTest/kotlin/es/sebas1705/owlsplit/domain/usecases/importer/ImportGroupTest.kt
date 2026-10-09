package es.sebas1705.owlsplit.domain.usecases.importer

import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.testing.account.FakeProfiles
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImportGroupTest {
    private val ledger = InMemoryLedger()
    private val import = ImportGroup(
        ledger.groups,
        ledger.members,
        ledger.expenses,
        ledger.transfers,
        FakeProfiles(UserProfile("Ana G", "accent:BLUE", "EUR")),
    )
    private val balances = ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers)

    private val csv = """
        Date,Description,Category,Cost,Currency,Ana,Luis,Marta
        2026-07-12,Cena,Dining out,90.00,EUR,60.00,-30.00,-30.00
        2026-07-13,Taxi,Taxi,20.00,EUR,-10.00,10.00,0.00
        2026-07-14,Luis paid Ana,Payment,30.00,EUR,-30.00,30.00,0.00
        2026-07-15,Museo,Entertainment,30.00,USD,-10.00,-10.00,20.00
        2026-07-20,Total balance, , ,EUR,20.00,-10.00,-10.00
    """.trimIndent()

    @Test
    fun theImportedGroupHasSplitwiseBalancesAndMeAsTheChosenPerson() = runTest {
        val parsed = assertNotNull(SplitwiseCsv.parse(csv))
        val result = import(parsed, "Ibiza", "plane", "BLUE", meIndex = 0, rates = emptyMap())

        assertEquals(3, result.imported)
        // The USD row needs a rate that was not given.
        assertEquals(1, result.leftOut)
        val members = ledger.members.observeMembers(result.groupId).first()
        assertEquals(listOf("Ana", "Luis", "Marta"), members.map { it.name })
        assertEquals(ledger.uid, members.first().userUid)
        val byName = balances(
            result.groupId
        ).first()!!.balances.mapKeys { (id, _) -> members.first { it.id == id }.name }
        assertEquals(mapOf("Ana" to 2_000L, "Luis" to 1_000L, "Marta" to -3_000L), byName)
    }

    @Test
    fun otherCurrenciesUseTheGivenRate() = runTest {
        val parsed = assertNotNull(SplitwiseCsv.parse(csv))
        val result =
            import(parsed, "Ibiza", "plane", "BLUE", meIndex = null, rates = mapOf("USD" to FxRate(900_000_000)))

        assertEquals(4, result.imported)
        val members = ledger.members.observeMembers(result.groupId).first()
        // Without choosing, I join with my profile name and the three people stay without an account.
        assertEquals(listOf("Ana G", "Ana", "Luis", "Marta"), members.map { it.name })
        val total = balances(result.groupId).first()!!.balances.values.sum()
        assertEquals(0, total)
    }
}
