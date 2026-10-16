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
    fun parse(text: String, members: List<Member>, myMemberId: String?, decimals: Int = 2): QuickEntry {
        val sentence = Sentence(WORD.findAll(text).map { it.value }.toList(), members.filter { it.active }, myMemberId)
        val amount = sentence.takeAmount(decimals)
        val payer = sentence.takePayer()
        val participants = sentence.takeParticipants(payer)
        val (title, category) = sentence.rest()
        return QuickEntry(amount, title, category, payer, participants)
    }

    /** The words of the sentence, compared without case or accents, crossed out as each rule takes them. */
    private class Sentence(val words: List<String>, val members: List<Member>, val me: String?) {
        val keys = words.map(::key)
        val used = BooleanArray(words.size)

        private fun free(i: Int) = i in words.indices && !used[i]

        private fun take(vararg indices: Int) = indices.forEach { if (it in words.indices) used[it] = true }

        private fun memberAt(i: Int): String? = when {
            !free(i) -> null
            keys[i] in ME_WORDS -> me
            else -> members.firstOrNull { matches(it.name, keys[i]) }?.id
        }

        /** The first number, with an optional currency word next to it. */
        fun takeAmount(decimals: Int): Long? {
            val i = keys.indices.firstOrNull { amountOf(keys[it], decimals) != null } ?: return null
            take(i)
            if (keys.getOrNull(i + 1) in CURRENCY_WORDS) take(i + 1)
            return amountOf(keys[i], decimals)
        }

        /**
         * "pagué", "he pagado" or "I paid" is me; otherwise the name before the verb ("Ana pagó", "Ana ha
         * pagado", "Luis paid") or after it ("pagado por Ana", "paid by Luis", "pagó Ana").
         */
        fun takePayer(): String? {
            val verb = keys.indices.firstOrNull { free(it) && (keys[it] in PAID || keys[it] in PAID_ME) } ?: return null
            take(verb)
            var before = verb - 1
            val auxiliary = keys.getOrNull(before)
            if (auxiliary in AUXILIARIES) {
                take(before)
                before--
            }
            val byMe = auxiliary in AUX_ME || keys[verb] in PAID_ME || keys.getOrNull(before) in ME_WORDS
            if (byMe && keys.getOrNull(before) in ME_WORDS) take(before)
            val after = if (keys.getOrNull(verb + 1) in BY_WORDS) verb + 2 else verb + 1
            return when {
                byMe -> me
                memberAt(before) != null -> memberAt(before).also { take(before) }
                memberAt(after) != null -> memberAt(after).also { for (k in verb + 1..after) take(k) }
                else -> null
            }
        }

        /** The names after "con"/"with" (the payer too) or "entre"/"between" (only them); "todos" is everyone. */
        fun takeParticipants(payer: String?): Set<String>? {
            val start = keys.indices.firstOrNull { free(it) && (keys[it] in WITH_WORDS || keys[it] in BETWEEN_WORDS) }
            val found = mutableSetOf<String>()
            var all = false
            if (start != null) {
                val end = (start + 1..words.size).first { j -> j == words.size || !inList(j) }
                for (j in start + 1 until end) {
                    if (keys[j] in ALL_WORDS) all = true
                    memberAt(j)?.let { found += it }
                    take(j)
                }
                if (all || found.isNotEmpty()) take(start)
            }
            return when {
                all || found.isEmpty() -> null
                keys[start!!] in WITH_WORDS -> found + listOfNotNull(payer ?: me)
                else -> found
            }
        }

        /** A word of a list of people: a name, "yo", "y"/"and", or "todos". */
        private fun inList(j: Int) = keys[j] in ALL_WORDS || keys[j] in LIST_JOINERS || memberAt(j) != null

        /** The title (what is left, without the little words around it) and the category it gives away. */
        fun rest(): Pair<String?, ExpenseCategory?> {
            // A loose "para todos" is the usual split: not part of the title.
            keys.indices.firstOrNull { free(it) && keys[it] in ALL_WORDS }?.let { take(it) }
            val left = words.indices.filter { !used[it] }
                .dropWhile { keys[it] in EDGE_WORDS }
                .dropLastWhile { keys[it] in EDGE_WORDS }
            val title = left.joinToString(" ") { words[it] }.takeIf { it.isNotBlank() }
                ?.replaceFirstChar { it.uppercase() }
                ?.take(ExpenseDrafts.MAX_TITLE)
            val category = left.firstNotNullOfOrNull { i -> CATEGORIES.entries.firstOrNull { keys[i] in it.value }?.key }
            return title to category
        }
    }

    /** "45", "12,50", "12.50€", "1.200" (thousands) in minor units of a currency with [decimals]. */
    private fun amountOf(word: String, decimals: Int): Long? {
        val number = word.removeSuffix("€").removePrefix("€").removeSuffix("eur").removeSuffix("$").removePrefix("$")
        val match = NUMBER.matchEntire(number) ?: return null
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        // Three digits after a separator are thousands ("1.200"), not cents.
        val thousands = fraction.length == THOUSANDS
        val units = (if (thousands) whole + fraction else whole).toLongOrNull()
        val cents = if (thousands) 0L else fraction.take(decimals).padEnd(decimals, '0').ifEmpty { "0" }.toLong()
        return units?.let { it * pow10(decimals) + cents }
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

    private val ACCENTS = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'à' to 'a',
        'è' to 'e', 'ò' to 'o', 'ï' to 'i', 'ç' to 'c'
    )
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
    private val EDGE_WORDS = setOf(
        "de", "del", "la", "el", "los", "las", "en", "por", "para", "un", "una", "for",
        "the", "a", "an", "on", "of", "y", "and"
    )

    /** Words that give the category away, without accents. */
    private val CATEGORIES: Map<ExpenseCategory, Set<String>> = mapOf(
        ExpenseCategory.FOOD to setOf(
            "cena", "comida", "almuerzo", "desayuno", "restaurante", "bar", "cafe", "cafes",
            "tapas", "pizza", "cervezas", "copas", "dinner", "lunch", "breakfast", "restaurant", "coffee", "drinks"
        ),
        ExpenseCategory.GROCERIES to setOf(
            "super", "supermercado", "mercadona", "compra", "lidl", "carrefour",
            "groceries", "supermarket"
        ),
        ExpenseCategory.TRANSPORT to setOf(
            "taxi", "uber", "cabify", "gasolina", "tren", "bus", "autobus", "metro",
            "parking", "peaje", "avion", "vuelo", "vuelos", "train", "fuel", "flight", "flights"
        ),
        ExpenseCategory.ACCOMMODATION to setOf(
            "hotel", "airbnb", "apartamento", "hostal", "alojamiento", "booking",
            "accommodation", "hostel"
        ),
        ExpenseCategory.LEISURE to setOf(
            "cine", "entradas", "concierto", "museo", "fiesta", "discoteca", "cinema",
            "tickets", "concert", "museum", "party"
        ),
        ExpenseCategory.SHOPPING to setOf("ropa", "tienda", "clothes", "shopping"),
        ExpenseCategory.HOME to setOf("alquiler", "piso", "casa", "limpieza", "rent", "cleaning"),
        ExpenseCategory.UTILITIES to setOf(
            "luz", "agua", "internet", "wifi", "gas", "factura", "electricity",
            "water", "bill"
        ),
        ExpenseCategory.HEALTH to setOf("farmacia", "medico", "pharmacy", "doctor"),
        ExpenseCategory.GIFTS to setOf("regalo", "regalos", "cumple", "cumpleanos", "gift", "gifts", "birthday"),
    )
}
