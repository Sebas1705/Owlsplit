package es.sebas1705.owlsplit.feature.groups.stats

import es.sebas1705.owlsplit.core.common.report.SimplePdf
import es.sebas1705.owlsplit.core.money.Conversion
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.domain.ledger.LedgerMember
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.nameOf
import es.sebas1705.owlsplit.domain.usecases.stats.GroupStats
import es.sebas1705.owlsplit.domain.usecases.stats.inBase
import es.sebas1705.owlsplit.domain.usecases.stats.sharesInBase
import kotlinx.datetime.LocalDate

/** The words of a report, already in the app's language (resolved by the screen). */
data class ReportLabels(
    val summary: String,
    val total: String,
    val expenses: String,
    val perDay: String,
    val people: String,
    val paid: String,
    val share: String,
    val balance: String,
    val categories: String,
    val months: String,
    val toSettle: String,
    val allSettled: String,
    val date: String,
    val description: String,
    val category: String,
    val amount: String,
    val currency: String,
    val paidBy: String,
    val transfer: String,
    val footer: String,
    val categoryName: (ExpenseCategory) -> String,
    val dateText: (LocalDate) -> String,
    val monthText: (year: Int, month: Int) -> String,
    /** "Ana pays €12.50 to Luis". */
    val payment: (from: String, amount: String, to: String) -> String,
    /** "Payment from Ana to Luis". */
    val transferTitle: (from: String, to: String) -> String,
)

/** A group's accounts as files to share: CSV for spreadsheets and a printable PDF summary. */
object GroupReport {
    const val CSV_MIME = "text/csv"
    const val PDF_MIME = "application/pdf"

    /** "Trip to Ibiza" → "trip-to-ibiza.csv"; letters and digits only, accents dropped. */
    fun fileName(groupName: String, extension: String): String {
        val slug = groupName.lowercase().map { ACCENTS[it] ?: it }.joinToString("")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(MAX_SLUG)
        return "${slug.ifEmpty { "owlsplit" }}.$extension"
    }

    /**
     * One row per expense and per payment, then a column per member with their part in the base
     * currency: for every member, what they paid minus their column adds up to their balance.
     * Spreadsheets in languages with a decimal comma expect ";" between fields.
     */
    fun csv(detail: GroupDetail, labels: ReportLabels, format: MoneyFormat): ByteArray {
        val base = Currency.of(detail.group.baseCurrency)
        val delimiter = if (format.decimalSeparator == ',') ';' else ','
        val number = { minor: Long, currency: Currency ->
            plainNumber(
                minor,
                currency.decimals,
                format.decimalSeparator
            )
        }
        val ledger = detail.members.map { LedgerMember(it.id, it.sortKey) }
        val rows = mutableListOf<List<String>>()
        rows += listOf(
            labels.date, labels.description, labels.category, labels.amount, labels.currency,
            "${labels.amount} (${base.code})", labels.paidBy,
        ) + detail.members.map { it.name }
        detail.expenses.sortedWith(compareBy({ it.date }, { it.createdAt })).forEach { expense ->
            val shares = expense.sharesInBase(ledger, base) ?: return@forEach
            val currency = Currency.of(expense.currency)
            rows += listOf(
                expense.date.toString(),
                expense.title,
                labels.categoryName(expense.category),
                number(expense.amountMinor, currency),
                currency.code,
                number(expense.inBase(expense.amountMinor, base), base),
                expense.payers.keys.joinToString(", ") { detail.members.nameOf(it) },
            ) + detail.members.map { number(shares[it.id] ?: 0L, base) }
        }
        detail.transfers.sortedWith(compareBy({ it.date }, { it.createdAt })).forEach { transfer ->
            val currency = Currency.of(transfer.currency)
            val amountInBase = if (currency == base) {
                transfer.amountMinor
            } else {
                Conversion.convert(transfer.amountMinor, currency, base, FxRate(transfer.fxRateScaled)).truncated
            }
            rows += listOf(
                transfer.date.toString(),
                labels.transferTitle(detail.members.nameOf(transfer.fromMemberId), detail.members.nameOf(transfer.toMemberId)),
                labels.transfer,
                number(transfer.amountMinor, currency),
                currency.code,
                number(amountInBase, base),
                detail.members.nameOf(transfer.fromMemberId),
            ) + detail.members.map { number(if (it.id == transfer.toMemberId) amountInBase else 0L, base) }
        }
        val text = rows.joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(delimiter.toString()) { csvField(it, delimiter) }
        }
        // The byte order mark makes Excel read the file as UTF-8.
        return ("﻿" + text).encodeToByteArray()
    }

    /** A one-document summary: totals, people, categories, months, what is left to settle, every expense. */
    @Suppress("LongMethod") // A page layout read top to bottom.
    fun pdf(detail: GroupDetail, stats: GroupStats, labels: ReportLabels, format: MoneyFormat): ByteArray {
        val base = Currency.of(detail.group.baseCurrency)
        val money = { minor: Long -> format.format(minor, base) }
        val doc = PdfCursor(SimplePdf())
        doc.title(detail.group.name)
        val range = listOfNotNull(
            stats.firstDate,
            stats.lastDate
        ).distinct().joinToString(" – ", transform = labels.dateText)
        doc.muted(listOf(labels.summary, range).filter { it.isNotEmpty() }.joinToString(" · "))
        doc.gap()
        doc.figures(
            listOf(
                labels.total to money(stats.totalMinor),
                labels.expenses to stats.expenseCount.toString(),
                labels.perDay to money(stats.perDayMinor),
            ),
        )

        doc.heading(labels.people)
        doc.columns(listOf("", labels.paid, labels.share, labels.balance), header = true)
        stats.members.forEach { member ->
            val balance = detail.balances?.balances?.get(member.memberId) ?: (member.paidMinor - member.shareMinor)
            doc.columns(
                listOf(
                    detail.members.nameOf(member.memberId),
                    money(member.paidMinor),
                    money(member.shareMinor),
                    format.format(balance, base, showPlus = true),
                ),
            )
        }

        if (stats.categories.isNotEmpty()) {
            doc.heading(labels.categories)
            val largest = stats.categories.maxOf { it.amountMinor }
            stats.categories.forEach {
                doc.bar(labels.categoryName(it.category), money(it.amountMinor), it.amountMinor.toFloat() / largest)
            }
        }

        if (stats.months.size > 1) {
            doc.heading(labels.months)
            val largest = stats.months.maxOf { it.amountMinor }.coerceAtLeast(1)
            stats.months.forEach {
                doc.bar(labels.monthText(it.year, it.month), money(it.amountMinor), it.amountMinor.toFloat() / largest)
            }
        }

        doc.heading(labels.toSettle)
        val payments = detail.balances?.suggestedPayments.orEmpty()
        if (payments.isEmpty()) doc.line(labels.allSettled)
        payments.forEach {
            doc.line(
                "• " + labels.payment(
                    detail.members.nameOf(it.fromMemberId),
                    money(it.amountMinor),
                    detail.members.nameOf(it.toMemberId),
                ),
            )
        }

        if (detail.expenses.isNotEmpty()) {
            doc.heading(labels.expenses)
            doc.expenseRow(labels.date, labels.description, labels.paidBy, labels.amount, header = true)
            detail.expenses.sortedWith(compareBy({ it.date }, { it.createdAt })).forEach { expense ->
                doc.expenseRow(
                    labels.dateText(expense.date),
                    expense.title,
                    expense.payers.keys.joinToString(", ") { detail.members.nameOf(it) },
                    format.format(expense.amountMinor, Currency.of(expense.currency)),
                )
            }
        }
        doc.footer(labels.footer)
        return doc.pdf.build()
    }

    /** "24,30": every decimal, no grouping or symbol, so spreadsheets read it as a number. */
    private fun plainNumber(minor: Long, decimals: Int, separator: Char): String {
        if (decimals == 0) return minor.toString()
        var factor = 1L
        repeat(decimals) { factor *= DECIMAL_BASE }
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        return "$sign${abs / factor}$separator${(abs % factor).toString().padStart(decimals, '0')}"
    }

    private fun csvField(value: String, delimiter: Char): String =
        if (value.any { it == delimiter || it in CSV_SPECIALS }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private const val MAX_SLUG = 40
    private const val DECIMAL_BASE = 10L
    private const val CSV_SPECIALS = "\"\r\n"
    private val ACCENTS = mapOf(
        'á' to 'a', 'à' to 'a', 'ä' to 'a', 'â' to 'a', 'é' to 'e', 'è' to 'e', 'ë' to 'e', 'ê' to 'e',
        'í' to 'i', 'ì' to 'i', 'ï' to 'i', 'î' to 'i', 'ó' to 'o', 'ò' to 'o', 'ö' to 'o', 'ô' to 'o',
        'ú' to 'u', 'ù' to 'u', 'ü' to 'u', 'û' to 'u', 'ñ' to 'n', 'ç' to 'c',
    )
}

/** Lays out a report top to bottom, starting a new page when the next block does not fit. */
private class PdfCursor(val pdf: SimplePdf) {
    private var y = MARGIN
    private val left = MARGIN
    private val right = pdf.width - MARGIN
    private val width = right - left

    fun title(text: String) {
        y += TITLE
        pdf.text(left, y, pdf.fit(text, TITLE, width), TITLE, bold = true)
        y += SMALL_GAP
    }

    fun muted(text: String) {
        y += BODY + SMALL_GAP
        pdf.text(left, y, text, BODY, rgb = MUTED)
    }

    fun gap() {
        y += SECTION_GAP / 2
    }

    /** Big numbers side by side. */
    fun figures(items: List<Pair<String, String>>) {
        need(BODY + FIGURE + SECTION_GAP)
        val column = width / items.size
        items.forEachIndexed { index, (label, value) ->
            val x = left + index * column
            pdf.text(x, y + BODY, label, BODY, rgb = MUTED)
            pdf.text(x, y + BODY + SMALL_GAP + FIGURE, value, FIGURE, bold = true)
        }
        y += BODY + SMALL_GAP + FIGURE
    }

    fun heading(text: String) {
        need(SECTION_GAP + HEADING + ROW * 2)
        y += SECTION_GAP + HEADING
        pdf.text(left, y, text, HEADING, bold = true)
        y += SMALL_GAP
        pdf.rect(left, y, width, RULE, LINE)
    }

    fun line(text: String) {
        need(ROW)
        y += ROW
        pdf.text(left, y, pdf.fit(text, BODY, width), BODY)
    }

    /** A name and then right-aligned number columns. */
    fun columns(cells: List<String>, header: Boolean = false) {
        need(ROW)
        y += ROW
        val numberColumn = width * NUMBER_COLUMN
        val color = if (header) MUTED else SimplePdf.BLACK
        pdf.text(left, y, pdf.fit(cells.first(), BODY, width - numberColumn * (cells.size - 1)), BODY, rgb = color)
        cells.drop(1).forEachIndexed { index, cell ->
            val x = right - numberColumn * (cells.size - 2 - index)
            pdf.textRight(x, y, cell, BODY, bold = !header && index == cells.size - 2, rgb = color)
        }
    }

    /** A label, a bar as long as [fraction] of the largest, and the amount. */
    fun bar(label: String, amount: String, fraction: Float) {
        need(ROW)
        y += ROW
        val labelWidth = width * BAR_LABEL
        val amountWidth = width * NUMBER_COLUMN
        pdf.text(left, y, pdf.fit(label, BODY, labelWidth - SMALL_GAP), BODY)
        val barWidth = (width - labelWidth - amountWidth - SMALL_GAP) * fraction.coerceIn(0f, 1f)
        pdf.rect(left + labelWidth, y - BAR_HEIGHT, barWidth.coerceAtLeast(1f), BAR_HEIGHT, ACCENT)
        pdf.textRight(right, y, amount, BODY)
    }

    fun expenseRow(date: String, title: String, payers: String, amount: String, header: Boolean = false) {
        need(ROW)
        y += ROW
        val color = if (header) MUTED else SimplePdf.BLACK
        val dateWidth = width * DATE_COLUMN
        val titleWidth = width * TITLE_COLUMN
        val amountWidth = width * NUMBER_COLUMN
        pdf.text(left, y, date, SMALL, rgb = color)
        pdf.text(left + dateWidth, y, pdf.fit(title, SMALL, titleWidth - SMALL_GAP), SMALL, rgb = color)
        val payerWidth = width - dateWidth - titleWidth - amountWidth - SMALL_GAP
        pdf.text(left + dateWidth + titleWidth, y, pdf.fit(payers, SMALL, payerWidth), SMALL, rgb = color)
        pdf.textRight(right, y, amount, SMALL, rgb = color)
    }

    fun footer(text: String) {
        need(SECTION_GAP + SMALL)
        y += SECTION_GAP + SMALL
        pdf.text(left, y, text, SMALL, rgb = MUTED)
    }

    private fun need(height: Float) {
        if (y + height > pdf.height - MARGIN) {
            pdf.newPage()
            y = MARGIN
        }
    }

    private companion object {
        const val MARGIN = 48f
        const val TITLE = 22f
        const val HEADING = 13f
        const val FIGURE = 18f
        const val BODY = 10f
        const val SMALL = 8.5f
        const val ROW = 16f
        const val SMALL_GAP = 6f
        const val SECTION_GAP = 26f
        const val RULE = 0.6f
        const val BAR_HEIGHT = 7f
        const val NUMBER_COLUMN = 0.18f
        const val BAR_LABEL = 0.3f
        const val DATE_COLUMN = 0.16f
        const val TITLE_COLUMN = 0.4f
        const val MUTED = 0x5A6070
        const val LINE = 0xE1E4EC
        const val ACCENT = 0x3B4BF5
    }
}
