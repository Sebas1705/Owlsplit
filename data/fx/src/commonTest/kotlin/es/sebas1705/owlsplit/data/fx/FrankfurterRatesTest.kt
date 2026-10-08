package es.sebas1705.owlsplit.data.fx

import es.sebas1705.owlsplit.core.datastore.KeyValueCache
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.repository.fx.FxQuote
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

class FrankfurterRatesTest {
    private class MapCache : KeyValueCache {
        val values = mutableMapOf<String, String>()
        override suspend fun get(key: String) = values[key]
        override suspend fun put(key: String, value: String) {
            values[key] = value
        }
    }

    private var nowMillis = 1_000_000L
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(nowMillis)
    }
    private val cache = MapCache()
    private var calls = 0
    private var online = true

    private fun rates(body: String = """{"amount":1.0,"base":"USD","date":"2026-10-07","rates":{"EUR":0.92345}}""") =
        FrankfurterRates(
            HttpClient(
                MockEngine { request ->
                    calls++
                    when {
                        !online -> error("offline")
                        request.url.parameters["base"] == "XXX" -> respondError(HttpStatusCode.NotFound)
                        else -> respond(body)
                    }
                },
            ),
            "https://api.frankfurter.dev/v1",
            cache,
            maxAgeSeconds = 3_600,
            clock = clock,
        )

    @Test
    fun readsTheRateAsFixedPointAndCachesIt() = runTest {
        val source = rates()
        assertEquals(FxQuote(FxRate(923_450_000), "2026-10-07"), source.latest("USD", "EUR"))
        nowMillis += 60_000
        source.latest("USD", "EUR")
        assertEquals(1, calls) // fresh cache: no second request
    }

    @Test
    fun anExpiredCacheIsRefreshedOrUsedAsStaleWhenOffline() = runTest {
        val source = rates()
        source.latest("USD", "EUR")
        nowMillis += 2 * 3_600_000
        online = false
        assertEquals(FxQuote(FxRate(923_450_000), "2026-10-07", stale = true), source.latest("USD", "EUR"))
        assertNull(rates().latest("GBP", "EUR")) // offline and never cached
    }

    @Test
    fun unsupportedCurrenciesHaveNoRate() = runTest {
        assertNull(rates().latest("XXX", "EUR"))
        assertNull(rates("""{"date":"2026-10-07","rates":{}}""").latest("USD", "EUR"))
    }

    @Test
    fun theSameCurrencyIsOneWithoutAsking() = runTest {
        assertEquals(FxRate.IDENTITY, rates().latest("EUR", "EUR")!!.rate)
        assertEquals(0, calls)
    }

    @Test
    fun jsonNumbersBecomePlainDecimals() {
        assertEquals("0.0000375", plainDecimal("3.75e-05"))
        assertEquals("0.92345", plainDecimal("0.92345"))
        assertEquals("1500", plainDecimal("1.5E3"))
        assertEquals("161.123456789", plainDecimal("161.1234567891234"))
    }
}
