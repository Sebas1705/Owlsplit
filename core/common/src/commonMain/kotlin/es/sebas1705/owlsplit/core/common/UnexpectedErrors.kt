package es.sebas1705.owlsplit.core.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Where an unexpected error is recorded: Crashlytics (non-fatal) in staging and production, the log in development. */
fun interface ErrorReporter {
    fun report(error: Throwable)
}

/**
 * Errors nobody expected (spec 9: "Crashlytics (non-fatal) + generic message"). ViewModels send
 * here whatever escapes their work; the app root shows a generic message for each one. The app
 * sets [reporter] at startup.
 */
object UnexpectedErrors {
    var reporter: ErrorReporter = ErrorReporter { }

    private val occurred = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)

    /** One event per error, for the generic message; never the trace. */
    val events: SharedFlow<Throwable> = occurred.asSharedFlow()

    fun report(error: Throwable) {
        reporter.report(error)
        occurred.tryEmit(error)
    }
}
