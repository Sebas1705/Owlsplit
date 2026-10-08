package es.sebas1705.owlsplit.domain.usecases.pay

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.data.models.PayMethods

/**
 * Reading what people type as their pay handles, and the links that open each service. Nothing
 * here moves money: the app opens the service (or copies the phone for Bizum, which has no public
 * link) and the payment is recorded by hand afterwards.
 */
object PayLinks {
    private val PAYPAL_NAME = Regex("^[A-Za-z0-9]{1,20}$")
    private val REVOLUT_TAG = Regex("^[a-z0-9._-]{3,32}$")
    private const val MIN_PHONE_DIGITS = 9
    private const val MAX_PHONE_DIGITS = 15

    /** Each handle cleaned up; null when blank or not valid (see [invalid]). */
    fun normalise(methods: PayMethods): PayMethods = PayMethods(
        bizum = methods.bizum?.let(::bizum),
        paypal = methods.paypal?.let(::paypal),
        revolut = methods.revolut?.let(::revolut),
    )

    /** The fields of [typed] that are filled in but not valid. */
    fun invalid(typed: PayMethods): Set<PayMethod> = buildSet {
        typed.bizum?.takeIf { it.isNotBlank() && bizum(it) == null }?.let { add(PayMethod.BIZUM) }
        typed.paypal?.takeIf { it.isNotBlank() && paypal(it) == null }?.let { add(PayMethod.PAYPAL) }
        typed.revolut?.takeIf { it.isNotBlank() && revolut(it) == null }?.let { add(PayMethod.REVOLUT) }
    }

    /** A phone number: digits and an optional leading "+", spaces and dashes dropped. */
    fun bizum(typed: String): String? {
        val compact = typed.filterNot { it.isWhitespace() || it == '-' || it == '.' }
        val digits = compact.removePrefix("+")
        return compact.takeIf { digits.length in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS && digits.all(Char::isDigit) }
    }

    /** "paypal.me/ana", "https://www.paypal.com/paypalme/ana", "@ana" or "ana" → "ana". */
    fun paypal(typed: String): String? =
        handle(typed, "paypal.me/", "paypal.com/paypalme/").takeIf { PAYPAL_NAME.matches(it) }

    /** "revolut.me/ana", "@ana" or "ana" → "ana" (Revtags are lowercase). */
    fun revolut(typed: String): String? =
        handle(typed, "revolut.me/").lowercase().takeIf { REVOLUT_TAG.matches(it) }

    /** PayPal.me with the amount filled in, e.g. https://paypal.me/ana/12.50EUR. */
    fun paypalUrl(name: String, amountMinor: Long, currency: Currency): String =
        "https://paypal.me/$name/${plainAmount(amountMinor, currency)}${currency.code}"

    /** Revolut's page for the tag; the amount is typed there. */
    fun revolutUrl(tag: String): String = "https://revolut.me/$tag"

    /** The methods [methods] has, in display order. */
    fun available(methods: PayMethods?): List<PayMethod> = buildList {
        if (methods == null) return@buildList
        if (!methods.bizum.isNullOrBlank()) add(PayMethod.BIZUM)
        if (!methods.paypal.isNullOrBlank()) add(PayMethod.PAYPAL)
        if (!methods.revolut.isNullOrBlank()) add(PayMethod.REVOLUT)
    }

    /** "12.50": a dot and the currency's decimals, as links expect whatever the language. */
    fun plainAmount(amountMinor: Long, currency: Currency): String {
        if (currency.decimals == 0) return amountMinor.toString()
        var factor = 1L
        repeat(currency.decimals) { factor *= DECIMAL_BASE }
        val fraction = (amountMinor % factor).toString().padStart(currency.decimals, '0')
        return "${amountMinor / factor}.$fraction"
    }

    private fun handle(typed: String, vararg prefixes: String): String {
        var s = typed.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        prefixes.firstOrNull { s.startsWith(it, ignoreCase = true) }?.let { s = s.substring(it.length) }
        return s.removePrefix("@").substringBefore('/').substringBefore('?')
    }

    private const val DECIMAL_BASE = 10L
}

enum class PayMethod { BIZUM, PAYPAL, REVOLUT }
