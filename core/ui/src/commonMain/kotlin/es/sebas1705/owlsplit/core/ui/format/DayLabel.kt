package es.sebas1705.owlsplit.core.ui.format

import androidx.compose.runtime.Composable
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
