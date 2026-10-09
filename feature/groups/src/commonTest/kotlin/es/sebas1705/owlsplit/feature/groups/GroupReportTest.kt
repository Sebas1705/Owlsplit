package es.sebas1705.owlsplit.feature.groups

import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import es.sebas1705.owlsplit.feature.groups.stats.GroupReport
import es.sebas1705.owlsplit.feature.groups.stats.ReportLabels
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupReportTest {
    private val meta = SyncMeta(1, 1, 1, "u", false, SyncState.SYNCED)
    private val group = Group(
        "g", "Viaje a Ibiza", "plane", "BLUE", "EUR", null, null, "ABCDEFGH", listOf("u"), emptyList(), "u", 1, meta,
    )
    private val members = listOf("Ana", "Luis").mapIndexed { i, name ->
        Member(name.lowercase(), "g", name, null, 1, null, true, i.toLong(), meta)
    }
    private val expenses = listOf(
        Expense(
            "e1", "g", "Cena; con \"postre\"", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 4_861,
            1_000_000_000, mapOf("ana" to 4_861L), SplitType.EQUAL, mapOf("ana" to 1L, "luis" to 1L), null, null,
            "u", 1, meta,
        ),
    )
    private val transfers = listOf(
        Transfer("t1", "g", "luis", "ana", 1_000, "EUR", 1_000_000_000, LocalDate(2026, 7, 13), null, "u", 2, meta),
    )
    private val detail = GroupDetail(group, members, expenses, transfers, null, "ana", "u")

    private val labels = ReportLabels(
        summary = "Resumen", total = "Total", expenses = "Gastos", perDay = "Por día", people = "Por persona",
        paid = "Pagó", share = "Le toca", balance = "Saldo", categories = "Por categoría", months = "Por mes",
        toSettle = "Para saldar", allSettled = "Todo saldado", date = "Fecha", description = "Concepto",
        category = "Categoría", amount = "Importe", currency = "Moneda", paidBy = "Pagado por", transfer = "Pago",
        footer = "Hecho con Owlsplit", categoryName = { it.name.lowercase() }, dateText = { it.toString() },
        monthText = { y, m -> "$m/$y" }, payment = { f, a, t -> "$f paga $a a $t" },
        transferTitle = { f, t -> "Pago de $f a $t" },
    )

    @Test
    fun csvHasARowPerMovementAndAColumnPerMember() {
        val text = GroupReport.csv(detail, labels, MoneyFormat.Spanish).decodeToString()
        assertTrue(text.startsWith("﻿"))
        val lines = text.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Fecha;Concepto;Categoría;Importe;Moneda;Importe (EUR);Pagado por;Ana;Luis", lines[0])
        // Fields with the delimiter or quotes are quoted; the odd cent goes to the first member.
        assertEquals("2026-07-12;\"Cena; con \"\"postre\"\"\";food;48,61;EUR;48,61;Ana;24,31;24,30", lines[1])
        assertEquals("2026-07-13;Pago de Luis a Ana;Pago;10,00;EUR;10,00;Luis;10,00;0,00", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun englishCsvUsesCommas() {
        val text = GroupReport.csv(detail, labels, MoneyFormat.English).decodeToString()
        assertTrue("\"Cena; con \"\"postre\"\"\",food,48.61,EUR,48.61,Ana,24.31,24.30" in text)
    }

    @Test
    fun pdfIsAValidDocumentWithTheSummary() {
        val stats = GroupStats.of("EUR", members, expenses)
        val text = GroupReport.pdf(detail, stats, labels, MoneyFormat.Spanish).decodeToString()
        assertTrue(text.startsWith("%PDF-1.4"))
        assertTrue("(Viaje a Ibiza) Tj" in text)
        assertTrue("(48,61 \\200) Tj" in text)
        assertTrue("(Hecho con Owlsplit) Tj" in text)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("viaje-a-ibiza.csv", GroupReport.fileName("Viaje a Ibiza", "csv"))
        assertEquals("cumple-de-nuria-2026.pdf", GroupReport.fileName("¡Cumple de Núria 2026! 🎉", "pdf"))
        assertEquals("owlsplit.pdf", GroupReport.fileName("🎉", "pdf"))
    }
}
