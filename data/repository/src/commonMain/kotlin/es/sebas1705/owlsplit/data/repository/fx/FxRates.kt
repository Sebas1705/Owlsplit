package es.sebas1705.owlsplit.data.repository.fx

import es.sebas1705.owlsplit.core.money.FxRate

/**
 * A published exchange rate: units of the target per one unit of the source, for [day]
 * (`AAAA-MM-DD`, the ECB publication day). [stale] when it comes from an expired cache entry
 * because the network failed.
 */
data class FxQuote(val rate: FxRate, val day: String, val stale: Boolean = false)

/** Exchange rates (Frankfurter in `data:fx`). */
interface FxRates {
    /** Null when there is no rate at all (unsupported currency, or offline with nothing cached). */
    suspend fun latest(from: String, to: String): FxQuote?
}
