package es.sebas1705.owlsplit.domain.usecases.support

import es.sebas1705.owlsplit.core.common.RecentLogs
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import es.sebas1705.owlsplit.core.common.UserReport
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import kotlinx.coroutines.flow.first

/** "Sync now": uploads everything waiting, whatever the retry delays say. False when something is left (offline, refused). */
class SyncNow(private val sync: SyncControl) {
    suspend operator fun invoke(): Boolean = sync.flush()
}

/**
 * "Report a problem": the user's words, how many changes are waiting and the last warnings and errors
 * go out as a non-fatal error (Crashlytics in release builds, only the log otherwise). The build and the
 * device travel with it by themselves. Nothing is sent unless the user asks.
 */
class ReportProblem(private val sync: SyncControl) {
    suspend operator fun invoke(description: String) {
        val message = buildString {
            appendLine(description.trim().take(MAX_DESCRIPTION).ifEmpty { "(no description)" })
            appendLine()
            appendLine("Pending changes: ${sync.pendingCount.first()}")
            RecentLogs.lines().forEach { appendLine(it) }
        }
        UnexpectedErrors.reporter.report(UserReport(message))
    }

    companion object {
        const val MAX_DESCRIPTION = 500
    }
}
