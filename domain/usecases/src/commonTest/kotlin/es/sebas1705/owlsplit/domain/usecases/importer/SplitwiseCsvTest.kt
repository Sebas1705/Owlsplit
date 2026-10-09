package es.sebas1705.owlsplit.domain.usecases.importer

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SplitwiseCsvTest {
    private val export = """
        Date,Description,Category,Cost,Currency,Ana,Luis Pérez,Marta
        2026-07-12,Cena,Dining out,90.00,EUR,60.00,-30.00,-30.00
        2026-07-13,"Taxi, aeropuerto",Taxi,20.00,EUR,-10.00,10.00,0.00
        2026-07-14,Luis Pérez paid Ana,Payment,30.00,EUR,-30.00,30.00,0.00
        2026-07-15,Museo,Entertainment,30.00,USD,-10.00,-10.00,20.00
        2026-07-16,Raro,General,10.00,XXX,5.00,-5.00,0.00

        2026-07-20,Total balance, , ,EUR,20.00,-10.00,-10.00
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun readsMembersExpensesAndPayments() {
        val parsed = assertNotNull(SplitwiseCsv.parse("﻿" + export))
        assertEquals(listOf("Ana", "Luis Pérez", "Marta"), parsed.members)
        assertEquals(3, parsed.expenseCount)
        assertEquals(1, parsed.paymentCount)
        assertEquals(1, parsed.skipped)
        assertEquals("EUR", parsed.mainCurrency)

        val cena = assertIs<ImportedEntry.Expense>(parsed.entries[0])
        assertEquals(LocalDate(2026, 7, 12), cena.date)
        assertEquals(ExpenseCategory.FOOD, cena.category)
        assertEquals(9_000, cena.amountMinor)
        assertEquals(mapOf(0 to 9_000L), cena.payers)
        assertEquals(mapOf(1 to 3_000L, 2 to 3_000L, 0 to 3_000L), cena.shares)

        val taxi = assertIs<ImportedEntry.Expense>(parsed.entries[1])
        assertEquals("Taxi, aeropuerto", taxi.title)
        assertEquals(ExpenseCategory.TRANSPORT, taxi.category)

        val payment = assertIs<ImportedEntry.Payment>(parsed.entries[2])
        assertEquals(1, payment.from)
        assertEquals(0, payment.to)
        assertEquals(3_000, payment.amountMinor)
    }

    @Test
    fun everyExpenseKeepsTheNetsOfSplitwise() {
        val parsed = assertNotNull(SplitwiseCsv.parse(export))
        val totals = LongArray(parsed.members.size)
        parsed.entries.filter { it.currency == "EUR" }.forEach { entry ->
            when (entry) {
                is ImportedEntry.Expense -> {
                    assertEquals(entry.amountMinor, entry.payers.values.sum())
                    assertEquals(entry.amountMinor, entry.shares.values.sum())
                    entry.payers.forEach { (i, v) -> totals[i] += v }
                    entry.shares.forEach { (i, v) -> totals[i] -= v }
                }
                is ImportedEntry.Payment -> {
                    totals[entry.from] += entry.amountMinor
                    totals[entry.to] -= entry.amountMinor
                }
            }
        }
        // EUR rows only: 60 - 10 - 30, -30 + 10 + 30, -30.
        assertEquals(listOf(2_000L, 1_000L, -3_000L), totals.toList())
    }

    @Test
    fun numbersInEitherNotation() {
        assertEquals(1_250, SplitwiseCsv.minor("12.50", 2))
        assertEquals(-1_250, SplitwiseCsv.minor("-12,50", 2))
        assertEquals(123_450, SplitwiseCsv.minor("1,234.50", 2))
        assertEquals(123_450, SplitwiseCsv.minor("1.234,50", 2))
        assertEquals(13, SplitwiseCsv.minor("12.5", 0))
        assertNull(SplitwiseCsv.minor("abc", 2))
    }

    @Test
    fun otherFilesAreNotSplitwise() {
        assertNull(SplitwiseCsv.parse(""))
        assertNull(SplitwiseCsv.parse("name,email\nAna,ana@example.com"))
    }

    @Test
    fun categoriesByKeyword() {
        assertEquals(ExpenseCategory.UTILITIES, SplitwiseCsv.category("Heat/gas"))
        assertEquals(ExpenseCategory.TRANSPORT, SplitwiseCsv.category("Gas/fuel"))
        assertEquals(ExpenseCategory.HOME, SplitwiseCsv.category("Rent"))
        assertEquals(ExpenseCategory.GROCERIES, SplitwiseCsv.category("Groceries"))
        assertEquals(ExpenseCategory.OTHER, SplitwiseCsv.category("General"))
    }
}
