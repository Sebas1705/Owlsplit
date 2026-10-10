package es.sebas1705.owlsplit.domain.usecases.expenses

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

enum class RepeatUnit(internal val code: Char) { WEEK('W'), MONTH('M'), YEAR('Y') }

/**
 * How an expense repeats, stored in its `recurrenceId` as "<series>|<unit><every>" ("…|M1": every
 * month) or "<series>|stop" once someone stopped it. Every expense of a series carries the same
 * series id; the dates follow the first one (the anchor) so the 31st of a month does not drift.
 */
data class Recurrence(val seriesId: String, val unit: RepeatUnit, val every: Int = 1, val stopped: Boolean = false) {
    fun encode(): String = if (stopped) "$seriesId|$STOP" else "$seriesId|${unit.code}$every"

    /** The [index]-th date of a series that started on [anchor] (index 0 is the anchor). */
    fun dateAt(anchor: LocalDate, index: Int): LocalDate = when (unit) {
        RepeatUnit.WEEK -> anchor.plus(index * every * DAYS_PER_WEEK, DateTimeUnit.DAY)
        RepeatUnit.MONTH -> anchor.plus(index * every, DateTimeUnit.MONTH)
        RepeatUnit.YEAR -> anchor.plus(index * every, DateTimeUnit.YEAR)
    }

    companion object {
        private const val STOP = "stop"
        private const val DAYS_PER_WEEK = 7

        /** Null for expenses that do not repeat or a value this version cannot read. */
        fun parse(value: String?): Recurrence? {
            val series = value?.substringBefore('|', "")?.takeIf { it.isNotEmpty() } ?: return null
            val rule = value.substringAfter('|')
            if (rule == STOP) return Recurrence(series, RepeatUnit.MONTH, stopped = true)
            val unit = RepeatUnit.entries.firstOrNull { it.code == rule.firstOrNull() } ?: return null
            val every = rule.drop(1).toIntOrNull()?.takeIf { it in 1..MAX_EVERY } ?: return null
            return Recurrence(series, unit, every)
        }

        private const val MAX_EVERY = 99
    }
}
