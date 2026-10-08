package es.sebas1705.owlsplit.config

/** Settings of the app. There is one Firebase project (`owlsplit`); debug builds differ only in [isDebugBuild]. */
object AppConfig {
    /** Exchange rates of the ECB (docs/DOMAIN.md, "Divisas"). */
    const val FX_API_BASE_URL: String = "https://api.frankfurter.dev/v1"

    /** How long a downloaded rate is reused; the ECB publishes once a day. */
    const val FX_CACHE_SECONDS: Int = 43_200

    /** Firebase Hosting site that serves the invite links (`/j/<code>`). */
    const val INVITE_BASE_URL: String = "https://owlsplit.web.app"
}

/** A debug build (local development): no crash reports, the design catalog and a "debug" badge. */
expect val isDebugBuild: Boolean
