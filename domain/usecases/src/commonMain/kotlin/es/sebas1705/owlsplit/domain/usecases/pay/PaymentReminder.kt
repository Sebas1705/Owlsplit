package es.sebas1705.owlsplit.domain.usecases.pay

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.data.models.PayMethods

/**
 * The words of a reminder in the app language. Each piece receives its values already formatted;
 * the UI fills them from string resources so the composition below stays plain and testable.
 */
data class ReminderWording(
    /** "Hi Luis! You owe me €12.50 in “Flat”." */
    val intro: (name: String, group: String, amount: String) -> String,
    /** "You can pay me by Bizum at 600 111 222 or with PayPal at …." */
    val payBy: (options: String) -> String,
    val bizum: (phone: String) -> String,
    val paypal: (url: String) -> String,
    val revolut: (url: String) -> String,
    /** Joins two items of a list that goes on: "a, b". */
    val listMore: (String, String) -> String,
    /** Joins the last item of a list: "a or b". */
    val listLast: (String, String) -> String,
    val thanks: String,
)

/**
 * A friendly message for someone who owes me, to send through any app. It names the group and the
 * amount and, when I set them, how to pay me: the PayPal link carries the amount already.
 */
object PaymentReminder {
    private const val SPANISH_PHONE_DIGITS = 9
    private const val PHONE_GROUP = 3

    @Suppress("LongParameterList") // Who, where, how much and my details.
    fun message(
        wording: ReminderWording,
        debtorName: String,
        groupName: String,
        formattedAmount: String,
        amountMinor: Long,
        currency: Currency,
        myMethods: PayMethods?,
    ): String {
        val options = PayLinks.available(myMethods).map { method ->
            when (method) {
                PayMethod.BIZUM -> wording.bizum(readablePhone(myMethods?.bizum.orEmpty()))
                PayMethod.PAYPAL -> wording.paypal(
                    PayLinks.paypalUrl(myMethods?.paypal.orEmpty(), amountMinor, currency)
                )
                PayMethod.REVOLUT -> wording.revolut(PayLinks.revolutUrl(myMethods?.revolut.orEmpty()))
            }
        }
        return buildList {
            add(wording.intro(debtorName.trim(), groupName.trim(), formattedAmount))
            if (options.isNotEmpty()) add(wording.payBy(joinList(options, wording)))
            add(wording.thanks)
        }.joinToString(" ")
    }

    /** "a", "a or b", "a, b or c". */
    fun joinList(items: List<String>, wording: ReminderWording): String = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> wording.listLast(items.dropLast(1).reduce(wording.listMore), items.last())
    }

    /** A Spanish phone in groups of three ("600 111 222", "+34 600 111 222"); anything else as stored. */
    fun readablePhone(phone: String): String {
        val prefix = if (phone.startsWith(SPAIN_PREFIX)) SPAIN_PREFIX else ""
        val digits = phone.removePrefix(prefix)
        if (digits.length != SPANISH_PHONE_DIGITS || !digits.all(Char::isDigit)) return phone
        val grouped = digits.chunked(PHONE_GROUP).joinToString(" ")
        return if (prefix.isEmpty()) grouped else "$prefix $grouped"
    }

    private const val SPAIN_PREFIX = "+34"
}
