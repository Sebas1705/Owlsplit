package es.sebas1705.owlsplit.domain.usecases.pay

import es.sebas1705.owlsplit.core.money.Currency

/**
 * Paying by bank transfer: reading an IBAN and the text of the EPC QR code that a banking app scans
 * to fill one in. Since the EU Instant Payments Regulation (October 2025), euro transfers arrive at
 * once and cost no more than a normal one, so an IBAN is a way to pay as quick as Bizum and it works
 * across countries.
 */
object BankTransfer {
    /** The longest holder name an EPC QR allows. */
    const val MAX_HOLDER = 70

    /**
     * An IBAN without spaces or dashes, in capitals, whose check digits are right (ISO 13616, mod 97):
     * "es91 2100 0418 4502 0005 1332" → "ES9121000418450200051332".
     */
    fun iban(typed: String): String? {
        val compact = typed.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        if (!IBAN_SHAPE.matches(compact)) return null
        // Country and check digits go to the end; letters count as 10..35; the remainder must be 1.
        var remainder = 0
        (compact.substring(IBAN_HEAD) + compact.take(IBAN_HEAD)).forEach { char ->
            remainder = if (char.isDigit()) {
                (remainder * ONE_DIGIT + char.digitToInt()) % IBAN_MOD
            } else {
                (remainder * TWO_DIGITS + (char - 'A' + LETTER_BASE)) % IBAN_MOD
            }
        }
        return compact.takeIf { remainder == 1 }
    }

    /** "ES91 2100 0418 4502 0005 1332": groups of four, as printed on cards and statements. */
    fun readableIban(iban: String): String = iban.chunked(IBAN_GROUP).joinToString(" ")

    /**
     * The text of an EPC QR code ("GiroCode", SEPA credit transfer, version 002): a banking app that
     * scans it fills in holder, IBAN, amount and concept. Null when it cannot be one: not in euros, or
     * under a cent. Holder and concept are cut to the 70 and 140 characters the standard allows.
     */
    fun epcPayload(holder: String, iban: String, amountMinor: Long, currency: Currency, concept: String): String? {
        if (currency.code != EURO || amountMinor !in 1..MAX_EPC_CENTS) return null
        return listOf(
            "BCD", "002", "1", "SCT",
            "", // BIC: optional inside the EEA.
            holder.trim().take(MAX_HOLDER),
            iban,
            "EUR${PayLinks.plainAmount(amountMinor, currency)}",
            "", // Purpose code.
            "", // Structured reference (the concept goes as free text instead).
            concept.trim().take(MAX_CONCEPT),
        ).joinToString("\n")
    }

    private val IBAN_SHAPE = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")
    private const val IBAN_HEAD = 4
    private const val IBAN_GROUP = 4
    private const val IBAN_MOD = 97
    private const val ONE_DIGIT = 10
    private const val TWO_DIGITS = 100
    private const val LETTER_BASE = 10
    private const val EURO = "EUR"
    private const val MAX_EPC_CENTS = 99_999_999_999L
    private const val MAX_CONCEPT = 140
}
