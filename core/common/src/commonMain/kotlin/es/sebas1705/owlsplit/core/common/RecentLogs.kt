package es.sebas1705.owlsplit.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last warnings and errors the app logged, kept in memory only so a problem report can say what
 * was happening. Never persisted and never sent unless the user sends a report.
 */
object RecentLogs {
    private const val MAX_LINES = 40
    private const val MAX_LINE_LENGTH = 240
    private val buffer = MutableStateFlow<List<String>>(emptyList())

    fun add(line: String) = buffer.update { (it + line.take(MAX_LINE_LENGTH)).takeLast(MAX_LINES) }

    fun lines(): List<String> = buffer.value
}

/** A problem the user chose to report: it travels as a non-fatal error with their words and the recent logs. */
class UserReport(message: String) : Exception(message)
