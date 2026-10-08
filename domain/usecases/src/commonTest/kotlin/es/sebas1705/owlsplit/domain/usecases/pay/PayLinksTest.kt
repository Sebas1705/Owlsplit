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
}
