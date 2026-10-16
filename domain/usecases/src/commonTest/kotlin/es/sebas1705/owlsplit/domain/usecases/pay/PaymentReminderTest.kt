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
        iban = { iban, holder -> "por transferencia al IBAN $iban" + (holder?.let { " (a nombre de $it)" } ?: "") },
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
        iban = { iban, holder -> "by bank transfer to $iban" + (holder?.let { " ($it)" } ?: "") },
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
    fun anIbanIsGivenReadableWithItsHolderWhenSet() {
        assertEquals(
            "¡Hola, Luis! En «Piso Lavapiés» me debes 12,50 €. " +
                "Puedes pagarme por Bizum al 600 111 222 o por transferencia al IBAN ES91 2100 0418 4502 0005 1332 " +
                "(a nombre de Ana García López). ¡Gracias!",
            message(
                spanish,
                PayMethods(bizum = "600111222", iban = "ES9121000418450200051332", ibanHolder = "Ana García López"),
            ),
        )
        assertEquals(
            "Hi Luis! You owe me €12.50 in “Piso Lavapiés”. You can pay me by bank transfer to DE89 3704 0044 0532 0130 00. Thanks!",
            message(english, PayMethods(iban = "DE89370400440532013000"), amount = "€12.50"),
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
