package es.sebas1705.owlsplit.core.ui.format

import androidx.compose.runtime.Composable
import es.sebas1705.owlsplit.core.ui.resources.Res
import es.sebas1705.owlsplit.core.ui.resources.months_short
import es.sebas1705.owlsplit.core.ui.resources.today
import es.sebas1705.owlsplit.core.ui.resources.yesterday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.jetbrains.compose.resources.stringResource

/** "Hoy", "Ayer", "12 jul", or "12 jul 2025" for other years. */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String {
    val months = stringResource(Res.string.months_short).split(" ")
    return when (date) {
        today -> stringResource(Res.string.today)
        today.minus(1, DateTimeUnit.DAY) -> stringResource(Res.string.yesterday)
        else -> {
            val day = "${date.day} ${months[date.month.ordinal]}"
            if (date.year == today.year) day else "$day ${date.year}"
        }
    }
}
