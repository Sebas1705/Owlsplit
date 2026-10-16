package es.sebas1705.owlsplit.domain.usecases.expenses

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickEntryTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val members = listOf("Ana", "Luis", "Beatriz García", "Marta").mapIndexed { i, name ->
        Member("m$i", "g", name, null, 1, if (i == 0) "uid-ana" else null, true, i.toLong(), meta)
    }
    private val ana = "m0"
    private val luis = "m1"
    private val bea = "m2"

    private fun parse(text: String) = QuickEntryParser.parse(text, members, myMemberId = ana)

    @Test
    fun spanishWithPayerTitleAndPeople() {
        assertEquals(
            QuickEntry(4_500, "Cena", ExpenseCategory.FOOD, payerId = luis, participantIds = setOf(luis, ana)),
            parse("Luis pagó 45 de cena con Ana"),
        )
        assertEquals(
            QuickEntry(1_250, "Taxi", ExpenseCategory.TRANSPORT, payerId = ana, participantIds = setOf(bea, ana)),
            parse("pagué 12,50 € del taxi con Bea"),
        )
    }

    @Test
    fun betweenMeansOnlyThoseAndEveryoneMeansTheUsual() {
        assertEquals(setOf(luis, bea), parse("Ana pagó 30 entre Luis y Beatriz").participantIds)
        val all = parse("compra del súper 62.40 para todos")
        assertNull(all.participantIds)
        assertEquals(6_240, all.amountMinor)
        assertEquals(ExpenseCategory.GROCERIES, all.category)
        assertEquals("Compra del súper", all.title)
    }

    @Test
    fun auxiliaryVerbsAndPaidBy() {
        assertEquals(bea, parse("Bea ha pagado el hotel 300").payerId)
        assertEquals(ana, parse("he pagado las entradas 24").payerId)
        assertEquals(luis, parse("gasolina 50 pagado por Luis").payerId)
        assertEquals("Gasolina", parse("gasolina 50 pagado por Luis").title)
    }

    @Test
    fun english() {
        assertEquals(
            QuickEntry(1_800, "Pizza", ExpenseCategory.FOOD, payerId = ana, participantIds = setOf(luis, ana)),
            parse("I paid 18 for pizza with Luis"),
        )
        assertEquals(marta(), parse("Uber 9.90 paid by Marta").payerId)
        assertEquals(ExpenseCategory.TRANSPORT, parse("Uber 9.90 paid by Marta").category)
    }

    @Test
    fun whatIsNotSaidStaysNull() {
        assertEquals(QuickEntry(title = "Regalo Marta", category = ExpenseCategory.GIFTS), parse("regalo Marta"))
        assertEquals(QuickEntry(amountMinor = 120_000), parse("1.200 €"))
        assertEquals(QuickEntry(), parse("   "))
    }

    private fun marta() = "m3"
}
