package es.sebas1705.owlsplit.config

/** Settings of the app. There is one Firebase project (`owlsplit`); debug builds differ only in [isDebugBuild]. */
object AppConfig {
    /** Exchange rates of the ECB (docs/DOMAIN.md, "Divisas"). */
    const val FX_API_BASE_URL: String = "https://api.frankfurter.dev/v1"

    /** How long a downloaded rate is reused; the ECB publishes once a day. */
    const val FX_CACHE_SECONDS: Int = 43_200

    /** Firebase Hosting site that serves the invite links (`/j/<code>`). */
    const val INVITE_BASE_URL: String = "https://owlsplit.web.app"

    /** What the download page says about the latest release (written by deploy-firebase.yml). */
    const val LATEST_RELEASE_URL: String = "$INVITE_BASE_URL/download/latest.json"
}

/** A debug build (local development): no crash reports, the design catalog and a "debug" badge. */
expect val isDebugBuild: Boolean

/**
 * The versionCode of this build when it can update from the download page (Android release builds);
 * null otherwise (debug builds, iOS), so the app never offers an APK it cannot use.
 */
expect val updatableVersionCode: Long?
