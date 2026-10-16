package es.sebas1705.owlsplit.domain.usecases.expenses

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member

/**
 * What a sentence like "Ana pagó 45 de cena con Luis y yo" says about an expense. Every field is null
 * when the sentence does not say it, so the editor keeps what it had; [participantIds] null means the
 * usual (everyone). The editor always shows the result to confirm: this only fills in the form.
 */
data class QuickEntry(
    val amountMinor: Long? = null,
    val title: String? = null,
    val category: ExpenseCategory? = null,
    val payerId: String? = null,
    val participantIds: Set<String>? = null,
)

/**
 * Reads a typed expense in Spanish or English, on the device and without any AI: the amount, who paid
 * ("Ana pagó", "pagué", "Luis paid", "pagado por Ana"), who takes part ("con Luis y yo", "entre Ana y
 * Bea", "todos", "with Luis") and, from what is left, the title and its category.
 */
object QuickEntryParser {
    @Suppress("CyclomaticComplexMethod", "LongMethod") // One pass over the words, each rule in place.
    fun parse(text: String, members: List<Member>, myMemberId: String?, decimals: Int = 2): QuickEntry {
        val words = WORD.findAll(text).map { it.value }.toMutableList()
        val keys = words.map(::key).toMutableList()
        val used = BooleanArray(words.size)
        val active = members.filter { it.active }

        // The amount: the first number, with an optional currency next to it.
        var amount: Long? = null
        for (i in words.indices) {
            val value = amountOf(keys[i], decimals) ?: continue
            amount = value
            used[i] = true
            if (i + 1 < words.size && keys[i + 1] in CURRENCY_WORDS) used[i + 1] = true
            break
        }

        fun memberAt(i: Int): String? {
            if (i !in words.indices || used[i]) return null
            if (keys[i] in ME_WORDS) return myMemberId
            return active.firstOrNull { matches(it.name, keys[i]) }?.id
        }

        // Who paid: "pagué", "he pagado" or "I paid" is me; otherwise the name before the verb ("Ana
        // pagó", "Ana ha pagado", "Luis paid") or after it ("pagado por Ana", "paid by Luis", "pagó Ana").
        var payer: String? = null
        for (i in words.indices) {
            if (used[i] || (keys[i] !in PAID && keys[i] !in PAID_ME)) continue
            used[i] = true
            var before = i - 1
            if (before >= 0 && keys[before] in AUXILIARIES) {
                used[before] = true
                if (keys[before] in AUX_ME) {
                    payer = myMemberId
                    break
                }
                before--
            }
            if (keys[i] in PAID_ME || (before >= 0 && keys[before] in ME_WORDS)) {
                if (before >= 0 && keys[before] in ME_WORDS) used[before] = true
                payer = myMemberId
                break
            }
            memberAt(before)?.let {
                payer = it
                used[before] = true
                break
            }
            val after = if (i + 1 < words.size && keys[i + 1] in BY_WORDS) i + 2 else i + 1
            memberAt(after)?.let {
                payer = it
                for (k in i + 1..after) used[k] = true
            }
            break
        }

        // Who takes part: the names after "con"/"with" (the payer too) or "entre"/"between" (only them).
        var participants: Set<String>? = null
        for (i in words.indices) {
            if (used[i]) continue
            val with = keys[i] in WITH_WORDS
            val between = keys[i] in BETWEEN_WORDS
            if (!with && !between) continue
            val found = mutableSetOf<String>()
            var j = i + 1
            var all = false
            while (j < words.size) {
                when {
                    keys[j] in ALL_WORDS -> all = true
                    keys[j] in LIST_JOINERS -> Unit
                    memberAt(j) != null -> found += memberAt(j)!!
                    else -> break
                }
                used[j] = true
                j++
            }
            if (all || found.isNotEmpty()) {
                used[i] = true
                participants = when {
                    all -> null
                    with -> found + listOfNotNull(payer ?: myMemberId)
                    else -> found
                }
            }
            break
        }
        if (participants == null) {
            words.indices.firstOrNull { !used[it] && keys[it] in ALL_WORDS }?.let { used[it] = true }
        }

        // The title: what is left, without the little words around it.
        val rest = words.indices.filter { !used[it] }
        val trimmed = rest.dropWhile { keys[it] in EDGE_WORDS }.dropLastWhile { keys[it] in EDGE_WORDS }
        val title = trimmed.joinToString(" ") { words[it] }.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?.take(ExpenseDrafts.MAX_TITLE)
        val category = trimmed.firstNotNullOfOrNull { i -> CATEGORIES.entries.firstOrNull { keys[i] in it.value }?.key }

        return QuickEntry(amount, title, category, payer, participants)
    }

    /** "45", "12,50", "12.50€", "1.200" (thousands) in minor units of a currency with [decimals]. */
    private fun amountOf(word: String, decimals: Int): Long? {
        val number = word.removeSuffix("€").removePrefix("€").removeSuffix("eur").removeSuffix("$").removePrefix("$")
        val match = NUMBER.matchEntire(number) ?: return null
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        // Three digits after a separator are thousands ("1.200"), not cents.
        if (fraction.length == THOUSANDS) return ((whole + fraction).toLongOrNull() ?: return null) * pow10(decimals)
        val units = whole.toLongOrNull() ?: return null
        val cents = fraction.take(decimals).padEnd(decimals, '0').ifEmpty { "0" }.toLong()
        return units * pow10(decimals) + cents
    }

    private fun pow10(n: Int): Long = (1..n).fold(1L) { acc, _ -> acc * DECIMAL_BASE }

    /** A member's name matches its full name, its first word, or the start of it ("Bea" for "Beatriz"). */
    private fun matches(name: String, word: String): Boolean {
        val full = key(name)
        if (word.length < MIN_NAME) return false
        return full == word || full.substringBefore(' ') == word || full.startsWith(word)
    }

    /** Lowercase without accents, for comparing. */
    private fun key(word: String): String = buildString {
        word.lowercase().forEach { append(ACCENTS[it] ?: it) }
    }

    private val WORD = Regex("""[\p{L}\p{N}€$][\p{L}\p{N}€$.,'-]*""")
    private val NUMBER = Regex("""(\d+)(?:[.,](\d{1,3}))?""")
    private const val THOUSANDS = 3
    private const val DECIMAL_BASE = 10L
    private const val MIN_NAME = 2

    private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'à' to 'a',
        'è' to 'e', 'ò' to 'o', 'ï' to 'i', 'ç' to 'c')
    private val CURRENCY_WORDS = setOf("€", "eur", "euro", "euros", "$", "dolares", "dollars", "usd")
    private val ME_WORDS = setOf("yo", "mi", "me", "i", "myself")
    private val PAID_ME = setOf("pague", "puse")
    private val PAID = setOf("pago", "pagado", "paid", "pays", "puso", "pone")
    /** "ha pagado", "has paid"; "he pagado" is me. */
    private val AUXILIARIES = setOf("ha", "he", "has", "have")
    private val AUX_ME = setOf("he", "have")
    private val BY_WORDS = setOf("por", "by")
    private val WITH_WORDS = setOf("con", "with")
    private val BETWEEN_WORDS = setOf("entre", "between")
    private val ALL_WORDS = setOf("todos", "todas", "everyone", "everybody", "all")
    private val LIST_JOINERS = setOf("y", "e", "and", ",", "&")
    private val EDGE_WORDS = setOf("de", "del", "la", "el", "los", "las", "en", "por", "para", "un", "una", "for",
        "the", "a", "an", "on", "of", "y", "and")

    /** Words that give the category away, without accents. */
    private val CATEGORIES: Map<ExpenseCategory, Set<String>> = mapOf(
        ExpenseCategory.FOOD to setOf("cena", "comida", "almuerzo", "desayuno", "restaurante", "bar", "cafe", "cafes",
            "tapas", "pizza", "cervezas", "copas", "dinner", "lunch", "breakfast", "restaurant", "coffee", "drinks"),
        ExpenseCategory.GROCERIES to setOf("super", "supermercado", "mercadona", "compra", "lidl", "carrefour",
            "groceries", "supermarket"),
        ExpenseCategory.TRANSPORT to setOf("taxi", "uber", "cabify", "gasolina", "tren", "bus", "autobus", "metro",
            "parking", "peaje", "avion", "vuelo", "vuelos", "train", "fuel", "flight", "flights"),
        ExpenseCategory.ACCOMMODATION to setOf("hotel", "airbnb", "apartamento", "hostal", "alojamiento", "booking",
            "accommodation", "hostel"),
        ExpenseCategory.LEISURE to setOf("cine", "entradas", "concierto", "museo", "fiesta", "discoteca", "cinema",
            "tickets", "concert", "museum", "party"),
        ExpenseCategory.SHOPPING to setOf("ropa", "tienda", "clothes", "shopping"),
        ExpenseCategory.HOME to setOf("alquiler", "piso", "casa", "limpieza", "rent", "cleaning"),
        ExpenseCategory.UTILITIES to setOf("luz", "agua", "internet", "wifi", "gas", "factura", "electricity",
            "water", "bill"),
        ExpenseCategory.HEALTH to setOf("farmacia", "medico", "pharmacy", "doctor"),
        ExpenseCategory.GIFTS to setOf("regalo", "regalos", "cumple", "cumpleanos", "gift", "gifts", "birthday"),
    )
}
