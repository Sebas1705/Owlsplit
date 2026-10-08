package es.sebas1705.owlsplit.errors

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import es.sebas1705.owlsplit.config.isDebugBuild
import es.sebas1705.owlsplit.core.common.ErrorReporter
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import es.sebas1705.owlsplit.core.logging.AppLogger

/**
 * Unexpected errors go to the log and, in release builds, to Crashlytics as non-fatal. Fatal
 * crashes reach Crashlytics by themselves: its collection is on for release builds only, in the
 * Android manifest (`crashlyticsEnabled`), so it also covers crashes during startup.
 */
object CrashReporting {
    private const val TAG = "Unexpected"

    fun start(firebaseConfigured: Boolean) {
        val crashlytics = if (firebaseConfigured && !isDebugBuild) Firebase.crashlytics else null
        UnexpectedErrors.reporter = ErrorReporter { error ->
            AppLogger.e(TAG, error.message ?: error::class.simpleName.orEmpty(), error)
            crashlytics?.recordException(error)
        }
    }
}
