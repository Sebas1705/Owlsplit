package es.sebas1705.owlsplit.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import es.sebas1705.owlsplit.core.ui.resources.Res
import es.sebas1705.owlsplit.core.ui.resources.day_month
import es.sebas1705.owlsplit.core.ui.resources.day_month_year
import es.sebas1705.owlsplit.core.ui.resources.months_short
import es.sebas1705.owlsplit.core.ui.resources.today
import es.sebas1705.owlsplit.core.ui.resources.yesterday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource

/** "Today", "Yesterday", "Jul 12", or "Jul 12, 2025" for other years (Spanish: "12 jul"). */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val months = stringResource(Res.string.months_short).split(" ")
    return when {
        date == today -> stringResource(Res.string.today)
        date == today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.yesterday)
        date.year == today.year -> stringResource(Res.string.day_month, date.day, months[date.month.ordinal])
        else -> stringResource(Res.string.day_month_year, date.day, months[date.month.ordinal], date.year)
    }
}

/**
 * "12 jul 2026" / "Jul 12, 2026" for any date, and "jul 2026" for a month, outside composition
 * (reports). The pattern is read once with markers in place of the numbers.
 */
class DateTexts(private val pattern: String, private val months: List<String>) {
    fun date(date: LocalDate): String = pattern
        .replace(DAY, date.day.toString())
        .replace(MONTH, months[date.month.ordinal])
        .replace(YEAR, date.year.toString())

    /** [month] is 1..12. */
    fun month(year: Int, month: Int): String = "${months[month - 1]} $year"

    /** "jul" / "Jul": [month] is 1..12. */
    fun monthName(month: Int): String = months[month - 1]

    private companion object {
        const val DAY = "\u0001"
        const val MONTH = "\u0002"
        const val YEAR = "\u0003"
    }
}

@Composable
fun rememberDateTexts(): DateTexts {
    val months = stringResource(Res.string.months_short).split(" ")
    // Compose resources substitute arguments as text, so markers can stand in for the numbers.
    val pattern = stringResource(Res.string.day_month_year, "\u0001", "\u0002", "\u0003")
    return remember(pattern, months) { DateTexts(pattern, months) }
}
