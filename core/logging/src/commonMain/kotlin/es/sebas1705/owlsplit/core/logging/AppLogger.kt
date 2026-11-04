package es.sebas1705.owlsplit.core.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

object AppLogger {
    /** [onLine] gets every warning and error as one short line (no stack traces). */
    fun listen(onLine: (String) -> Unit) = Logger.addLogWriter(
        object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                if (severity >= Severity.Warn) onLine("$severity $tag: $message")
            }
        },
    )

    fun d(tag: String, msg: String) = Logger.d(tag) { msg }
    fun i(tag: String, msg: String) = Logger.i(tag) { msg }
    fun w(tag: String, msg: String) = Logger.w(tag) { msg }
    fun e(tag: String, msg: String, t: Throwable? = null) =
        if (t != null) Logger.e(t, tag) { msg } else Logger.e(tag) { msg }
}
