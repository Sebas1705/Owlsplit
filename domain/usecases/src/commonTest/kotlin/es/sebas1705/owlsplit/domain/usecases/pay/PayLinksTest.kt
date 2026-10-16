package es.sebas1705.owlsplit.domain.usecases.pay

import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.data.models.PayMethods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayLinksTest {
    @Test
    fun phonesKeepDigitsAndTheirPrefix() {
        assertEquals("600111222", PayLinks.bizum("600 11 12 22"))
        assertEquals("+34600111222", PayLinks.bizum("+34 600-111-222"))
        assertNull(PayLinks.bizum("6001"))
        assertNull(PayLinks.bizum("600 11 12 2a"))
    }

    @Test
    fun handlesAreReadFromLinksAndAts() {
        assertEquals("AnaGarcia", PayLinks.paypal("https://paypal.me/AnaGarcia"))
        assertEquals("ana", PayLinks.paypal("https://www.paypal.com/paypalme/ana/10EUR"))
        assertEquals("ana", PayLinks.paypal("@ana"))
        assertNull(PayLinks.paypal("ana garcía"))
        assertEquals("anag", PayLinks.revolut("revolut.me/AnaG"))
        assertEquals("ana.g", PayLinks.revolut("@ana.g"))
        assertNull(PayLinks.revolut("a"))
    }

    @Test
    fun linksCarryTheAmountWithADot() {
        assertEquals("https://paypal.me/ana/12.50EUR", PayLinks.paypalUrl("ana", 1250, Currency.of("EUR")))
        assertEquals("https://paypal.me/ana/1500JPY", PayLinks.paypalUrl("ana", 1500, Currency.of("JPY")))
        assertEquals("0.05", PayLinks.plainAmount(5, Currency.of("EUR")))
        assertEquals("https://revolut.me/ana", PayLinks.revolutUrl("ana"))
    }

    @Test
    fun invalidFieldsAreReportedAndDropped() {
        val typed = PayMethods(bizum = "12", paypal = "@ana", revolut = " ")
        assertEquals(setOf(PayMethod.BIZUM), PayLinks.invalid(typed))
        assertEquals(PayMethods(paypal = "ana"), PayLinks.normalise(typed))
        assertEquals(listOf(PayMethod.PAYPAL), PayLinks.available(PayLinks.normalise(typed)))
    }

    @Test
    fun ibansAreCompactedAndTheirCheckDigitsVerified() {
        assertEquals("ES9121000418450200051332", BankTransfer.iban(" es91 2100 0418 4502 0005 1332 "))
        assertEquals("DE89370400440532013000", BankTransfer.iban("DE89-3704-0044-0532-0130-00"))
        // One digit off, too short, or not an IBAN at all.
        assertNull(BankTransfer.iban("ES9121000418450200051333"))
        assertNull(BankTransfer.iban("ES91"))
        assertNull(BankTransfer.iban("600111222"))
        assertEquals("ES91 2100 0418 4502 0005 1332", BankTransfer.readableIban("ES9121000418450200051332"))
    }

    @Test
    fun anIbanIsAMethodAndItsHolderIsKeptWithIt() {
        val typed = PayMethods(iban = "es91 2100 0418 4502 0005 1332", ibanHolder = "  Ana García López ")
        val clean = PayLinks.normalise(typed)
        assertEquals(PayMethods(iban = "ES9121000418450200051332", ibanHolder = "Ana García López"), clean)
        assertEquals(listOf(PayMethod.IBAN), PayLinks.available(clean))
        assertEquals(setOf(PayMethod.IBAN), PayLinks.invalid(PayMethods(iban = "ES00 1234")))
        // Without an IBAN the holder means nothing.
        assertEquals(PayMethods(), PayLinks.normalise(PayMethods(ibanHolder = "Ana")))
    }

    @Test
    fun theEpcQrCarriesHolderIbanEuroAmountAndConcept() {
        val payload = BankTransfer.epcPayload(
            "Ana García",
            "ES9121000418450200051332",
            1250,
            Currency.of("EUR"),
            "Owlsplit: Piso"
        )
        assertEquals(
            "BCD\n002\n1\nSCT\n\nAna García\nES9121000418450200051332\nEUR12.50\n\n\nOwlsplit: Piso",
            payload,
        )
        // SEPA transfers are in euros, from one cent.
        assertNull(BankTransfer.epcPayload("Ana", "ES9121000418450200051332", 1250, Currency.of("USD"), "x"))
        assertNull(BankTransfer.epcPayload("Ana", "ES9121000418450200051332", 0, Currency.of("EUR"), "x"))
        // Holder and concept are cut to what the standard allows (70 and 140 characters).
        val long = BankTransfer.epcPayload(
            "A".repeat(90),
            "ES9121000418450200051332",
            100,
            Currency.of("EUR"),
            "c".repeat(200)
        )!!
        assertEquals(70, long.lines()[5].length)
        assertEquals(140, long.lines()[10].length)
    }
}
