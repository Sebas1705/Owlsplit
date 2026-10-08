package es.sebas1705.owlsplit.data.fx

import es.sebas1705.owlsplit.core.datastore.KeyValueCache
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.data.repository.fx.FxQuote
import es.sebas1705.owlsplit.data.repository.fx.FxRates
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * ECB rates from Frankfurter (no key). Rates are read as text and parsed into fixed point: never
 * through a Double. Each pair is cached for [maxAgeSeconds]; an expired entry is still returned,
 * marked stale, when the network fails (docs/DOMAIN.md, "Divisas").
 */
class FrankfurterRates(
    private val client: HttpClient,
    private val baseUrl: String,
    private val cache: KeyValueCache,
    private val maxAgeSeconds: Int,
    private val clock: Clock = Clock.System,
) : FxRates {

    private sealed interface Fetch {
        data class Ok(val quote: FxQuote) : Fetch
        data object Unsupported : Fetch
        data object Failed : Fetch
    }

    override suspend fun latest(from: String, to: String): FxQuote? {
        if (from == to) return FxQuote(FxRate.IDENTITY, day = "")
        val key = "fx:$from:$to"
        val cached = cache.get(key)?.let(::decode)
        val now = clock.now().toEpochMilliseconds()
        if (cached != null && now - cached.savedAt < maxAgeSeconds * MILLIS) return cached.quote
        return when (val fetched = fetch(from, to)) {
            is Fetch.Ok -> fetched.quote.also { cache.put(key, encode(it, now)) }
            Fetch.Unsupported -> null
            Fetch.Failed -> cached?.quote?.copy(stale = true)
        }
    }

    // Any transport or parsing failure means "no fresh rate".
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun fetch(from: String, to: String): Fetch = try {
        val response = client.get("$baseUrl/latest") {
            parameter("base", from)
            parameter("symbols", to)
        }
        when {
            response.status.value in CLIENT_ERRORS -> Fetch.Unsupported
            !response.status.isSuccess() -> Fetch.Failed
            else -> {
                val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val rate = body["rates"]?.jsonObject?.get(to)?.jsonPrimitive?.content
                val day = body["date"]?.jsonPrimitive?.content.orEmpty()
                rate?.let { Fetch.Ok(FxQuote(FxRate.parse(plainDecimal(it)), day)) } ?: Fetch.Unsupported
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Fetch.Failed
    }

    private class Cached(val quote: FxQuote, val savedAt: Long)

    private fun encode(quote: FxQuote, savedAt: Long) = "${quote.rate.scaled}|${quote.day}|$savedAt"

    private fun decode(value: String): Cached? {
        val parts = value.split('|')
        val scaled = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val savedAt = parts.getOrNull(2)?.toLongOrNull() ?: return null
        return Cached(FxQuote(FxRate(scaled), parts[1]), savedAt)
    }

    private companion object {
        const val MILLIS = 1_000L
        val CLIENT_ERRORS = 400..499
    }
}

/**
 * A JSON number as a plain decimal with at most 9 decimals ("3.75e-05" → "0.0000375";
 * extra digits are cut, never rounded up).
 */
internal fun plainDecimal(number: String): String {
    val (mantissa, exponent) = number.lowercase().split('e').let { it[0] to (it.getOrNull(1)?.toInt() ?: 0) }
    val integerDigits = mantissa.substringBefore('.')
    val fractionDigits = mantissa.substringAfter('.', "")
    val digits = integerDigits + fractionDigits
    val point = integerDigits.length + exponent
    val padded = when {
        point <= 0 -> "0".repeat(-point + 1) + digits
        point > digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits
    }
    val split = maxOf(point, 1)
    val integer = padded.take(split).trimStart('0').ifEmpty { "0" }
    val fraction = padded.drop(split).take(FxRate.DECIMALS).trimEnd('0')
    return if (fraction.isEmpty()) integer else "$integer.$fraction"
}
