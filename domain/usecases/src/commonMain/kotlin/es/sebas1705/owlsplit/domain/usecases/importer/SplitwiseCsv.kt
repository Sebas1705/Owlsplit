package es.sebas1705.owlsplit.domain.usecases.importer

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import kotlinx.datetime.LocalDate

/** One movement read from another app, with members by their column index. */
sealed interface ImportedEntry {
    val date: LocalDate
    val currency: String

    /** [payers] and [shares] each add up to [amountMinor] (an exact split). */
    data class Expense(
        override val date: LocalDate,
        override val currency: String,
        val title: String,
        val category: ExpenseCategory,
        val amountMinor: Long,
        val payers: Map<Int, Long>,
        val shares: Map<Int, Long>,
    ) : ImportedEntry

    data class Payment(
        override val date: LocalDate,
        override val currency: String,
        val from: Int,
        val to: Int,
        val amountMinor: Long,
    ) : ImportedEntry
}

data class ParsedImport(
    val members: List<String>,
    val entries: List<ImportedEntry>,
    /** Rows that could not be read (unknown currency, bad numbers); the rest is still imported. */
    val skipped: Int,
) {
    /** The currency most rows use: the group's. */
    val mainCurrency: String
        get() = entries.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: DEFAULT_CURRENCY

    val expenseCount: Int get() = entries.count { it is ImportedEntry.Expense }
    val paymentCount: Int get() = entries.count { it is ImportedEntry.Payment }

    private companion object {
        const val DEFAULT_CURRENCY = "EUR"
    }
}

/**
 * Reads the spreadsheet Splitwise exports for a group ("Export as spreadsheet"): five fixed columns
 * (date, description, category, cost, currency; their titles depend on the language) and then one
 * column per person with what they paid minus their share. The last row holds the totals and has no
 * cost. Only the net of each person is known, so each expense becomes an exact split that gives the
 * same nets: whoever is owed paid, everyone else's share is what they owe, and the rest of the cost
 * is the share of the person who paid the most. Balances therefore come out exactly as in Splitwise.
 */
object SplitwiseCsv {
    private const val FIXED_COLUMNS = 5
    private const val COST = 3
    private const val CURRENCY = 4
    private const val MAX_MEMBERS = 50

    /** Null when the text is not a Splitwise group export. */
    fun parse(text: String): ParsedImport? {
        val rows = Csv.parse(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        val header = rows.firstOrNull() ?: return null
        val members = header.drop(FIXED_COLUMNS).map { it.trim() }.filter { it.isNotEmpty() }
        if (header.size < FIXED_COLUMNS + 1 || members.size > MAX_MEMBERS) return null
        var skipped = 0
        val entries = rows.drop(1).mapNotNull { row ->
            // The totals row (and anything without a cost) is not a movement.
            if (row.getOrNull(COST).isNullOrBlank()) return@mapNotNull null
            readRow(row, members.size).also { if (it == null) skipped++ }
        }
        if (entries.isEmpty() && skipped == 0) return null
        return ParsedImport(members, entries, skipped)
    }

    @Suppress("ReturnCount") // One early exit per field that can be wrong.
    private fun readRow(row: List<String>, memberCount: Int): ImportedEntry? {
        val date = runCatching { LocalDate.parse(row[0].trim().take(DATE_LENGTH)) }.getOrNull() ?: return null
        val code = row[CURRENCY].trim().uppercase()
        if (!Currency.isKnown(code)) return null
        val currency = Currency.of(code)
        val cost = minor(row[COST], currency.decimals)?.takeIf { it > 0 } ?: return null
        val nets = (0 until memberCount).associateWith {
            minor(row.getOrNull(FIXED_COLUMNS + it).orEmpty(), currency.decimals) ?: 0L
        }
            .filterValues { it != 0L }
            .let(::balanced)
        if (nets.isEmpty()) return null
        val title = row[1].trim().ifEmpty { row[2].trim() }.take(MAX_TITLE)
        val positive = nets.filterValues { it > 0 }
        val negative = nets.filterValues { it < 0 }
        if (isPayment(row[2], title) && positive.size == 1 && negative.size == 1) {
            return ImportedEntry.Payment(
                date,
                code,
                positive.keys.single(),
                negative.keys.single(),
                positive.values.single()
            )
        }
        val owed = negative.values.sumOf { -it }
        // The cost can never be below what is owed; if it is, the owed amount is the real total.
        val amount = maxOf(cost, owed)
        val payerShare = amount - owed
        val mainPayer = positive.maxByOrNull { it.value }?.key ?: return null
        val shares = negative.mapValues { -it.value }.toMutableMap()
        if (payerShare > 0) shares[mainPayer] = payerShare
        val payers = positive.mapValues { (id, net) -> net + (shares[id] ?: 0L) }
        return ImportedEntry.Expense(date, code, title, category(row[2]), amount, payers, shares)
    }

    /** Splitwise rounds each column on its own: a cent of difference goes to the largest net. */
    private fun balanced(nets: Map<Int, Long>): Map<Int, Long> {
        val error = nets.values.sum()
        if (error == 0L || nets.isEmpty()) return nets
        val largest = nets.maxByOrNull { kotlin.math.abs(it.value) }!!.key
        return nets + (largest to nets.getValue(largest) - error)
    }

    private fun isPayment(category: String, title: String): Boolean {
        val c = category.trim().lowercase()
        val t = title.lowercase()
        return c in PAYMENT_CATEGORIES || (c.isEmpty() && (" paid " in t || " pagó " in t))
    }

    /** "12.50", "-12.50", "12,50", "1,234.50", "1.234,50" → minor units. */
    @Suppress("ReturnCount") // One early exit per way the text can be wrong.
    internal fun minor(text: String, decimals: Int): Long? {
        var s = text.trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-") || s.startsWith("−")
        s = s.trimStart('-', '−', '+')
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalMark = if (lastDot > lastComma) '.' else ','
        val parts = s.split(decimalMark)
        if (parts.size > 2) return null
        val whole = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
        if (parts[0].any { !it.isDigit() && it != '.' && it != ',' }) return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.any { !it.isDigit() }) return null
        // More decimals than the currency has: round half up.
        val padded = fraction.padEnd(decimals + 1, '0')
        var value = (whole + padded.take(decimals)).toLongOrNull() ?: return null
        if (padded[decimals] >= '5') value++
        return if (negative) -value else value
    }

    /** Splitwise categories (English and Spanish) by keyword, most specific first. */
    internal fun category(name: String): ExpenseCategory {
        val n = name.lowercase()
        return CATEGORY_KEYWORDS.firstOrNull { (words, _) -> words.any { it in n } }?.second ?: ExpenseCategory.OTHER
    }

    private const val DATE_LENGTH = 10
    private const val MAX_TITLE = 80
    private val PAYMENT_CATEGORIES = setOf("payment", "pago", "pagos", "paiement", "zahlung")
    private val CATEGORY_KEYWORDS = listOf(
        listOf(
            "heat", "calefacción", "electric", "electricidad", "luz", "water", "agua", "tv", "phone", "teléfono",
            "internet", "utilit", "suministro"
        ) to ExpenseCategory.UTILITIES,
        listOf("grocer", "supermerc", "comestible", "alimentación") to ExpenseCategory.GROCERIES,
        listOf("dining", "restaur", "food", "comida", "cena", "bar", "liquor", "alcohol", "bebida")
            to ExpenseCategory.FOOD,
        listOf("hotel", "lodging", "alojamiento") to ExpenseCategory.ACCOMMODATION,
        listOf(
            "taxi", "car", "coche", "fuel", "gasolina", "gas", "bus", "train", "tren", "plane", "avión", "vuelo",
            "parking", "aparcamiento", "transport", "bicycle", "bici"
        ) to ExpenseCategory.TRANSPORT,
        listOf(
            "rent", "alquiler", "mortgage", "hipoteca", "household", "hogar", "furniture", "mueble",
            "maintenance", "mantenimiento", "cleaning", "limpieza", "pets", "mascota", "services", "home", "casa"
        )
            to ExpenseCategory.HOME,
        listOf("medical", "médic", "health", "salud", "farmacia") to ExpenseCategory.HEALTH,
        listOf("gift", "regalo") to ExpenseCategory.GIFTS,
        listOf("clothing", "ropa", "electronics", "electrónica", "shopping", "compras") to ExpenseCategory.SHOPPING,
        listOf(
            "entertainment", "entretenimiento", "movies", "cine", "games", "juegos", "music", "música",
            "sports", "deporte", "ocio"
        ) to ExpenseCategory.LEISURE,
    )
}

/** RFC 4180 CSV: commas, quoted fields with "" for a quote, CRLF or LF line ends. */
internal object Csv {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> {
                    row += field.toString()
                    field.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString()
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }
}
