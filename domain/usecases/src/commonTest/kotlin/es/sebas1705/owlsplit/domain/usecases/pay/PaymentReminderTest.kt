package es.sebas1705.owlsplit.domain.usecases.pay

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.data.models.PayMethods
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentReminderTest {
    // The same templates as the string resources, filled by hand.
    private val spanish = ReminderWording(
        intro = { name, group, amount -> "¡Hola, $name! En «$group» me debes $amount." },
        payBy = { options -> "Puedes pagarme $options." },
        bizum = { phone -> "por Bizum al $phone" },
        paypal = { url -> "por PayPal en $url" },
        revolut = { url -> "por Revolut en $url" },
        listMore = { a, b -> "$a, $b" },
        listLast = { a, b -> "$a o $b" },
        thanks = "¡Gracias!",
    )
    private val english = ReminderWording(
        intro = { name, group, amount -> "Hi $name! You owe me $amount in “$group”." },
        payBy = { options -> "You can pay me $options." },
        bizum = { phone -> "by Bizum at $phone" },
        paypal = { url -> "with PayPal at $url" },
        revolut = { url -> "with Revolut at $url" },
        listMore = { a, b -> "$a, $b" },
        listLast = { a, b -> "$a or $b" },
        thanks = "Thanks!",
    )
    private val eur = Currency.of("EUR")

    private fun message(wording: ReminderWording, methods: PayMethods?, amount: String = "12,50 €") =
        PaymentReminder.message(wording, "Luis", "Piso Lavapiés", amount, 1_250, eur, methods)

    @Test
    fun withoutPayMethodsItIsJustTheDebtAndThanks() {
        assertEquals("¡Hola, Luis! En «Piso Lavapiés» me debes 12,50 €. ¡Gracias!", message(spanish, null))
        assertEquals(
            "Hi Luis! You owe me €12.50 in “Piso Lavapiés”. Thanks!",
            message(english, PayMethods(), amount = "€12.50"),
        )
    }

    @Test
    fun onlyTheMethodsISetAreOffered() {
        assertEquals(
            "¡Hola, Luis! En «Piso Lavapiés» me debes 12,50 €. " +
                "Puedes pagarme por Bizum al 600 111 222 o por PayPal en https://paypal.me/ana/12.50EUR. ¡Gracias!",
            message(spanish, PayMethods(bizum = "600111222", paypal = "ana")),
        )
        assertEquals(
            "Hi Luis! You owe me €12.50 in “Piso Lavapiés”. You can pay me with Revolut at https://revolut.me/ana. Thanks!",
            message(english, PayMethods(revolut = "ana", bizum = " "), amount = "€12.50"),
        )
    }

    @Test
    fun threeMethodsAreJoinedAsAList() {
        assertEquals(
            "Hi Luis! You owe me €12.50 in “Piso Lavapiés”. You can pay me by Bizum at +34 600 111 222, " +
                "with PayPal at https://paypal.me/ana/12.50EUR or with Revolut at https://revolut.me/ana. Thanks!",
            message(english, PayMethods("+34600111222", "ana", "ana"), amount = "€12.50"),
        )
    }

    @Test
    fun spanishPhonesAreGroupedAndOthersKeptAsStored() {
        assertEquals("600 111 222", PaymentReminder.readablePhone("600111222"))
        assertEquals("+34 600 111 222", PaymentReminder.readablePhone("+34600111222"))
        assertEquals("+447700900123", PaymentReminder.readablePhone("+447700900123"))
    }

    @Test
    fun namesAreTrimmed() {
        assertEquals(
            "Hi Luis! You owe me €3.00 in “Flat”. Thanks!",
            PaymentReminder.message(english, " Luis ", "Flat ", "€3.00", 300, eur, null),
        )
    }
}
