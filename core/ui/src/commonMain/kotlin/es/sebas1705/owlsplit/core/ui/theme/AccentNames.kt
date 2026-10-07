package es.sebas1705.owlsplit.core.ui.theme

import androidx.compose.runtime.Composable
import es.sebas1705.owlsplit.core.ui.resources.Res
import es.sebas1705.owlsplit.core.ui.resources.accent_amber
import es.sebas1705.owlsplit.core.ui.resources.accent_blue
import es.sebas1705.owlsplit.core.ui.resources.accent_cyan
import es.sebas1705.owlsplit.core.ui.resources.accent_graphite
import es.sebas1705.owlsplit.core.ui.resources.accent_green
import es.sebas1705.owlsplit.core.ui.resources.accent_orange
import es.sebas1705.owlsplit.core.ui.resources.accent_pink
import es.sebas1705.owlsplit.core.ui.resources.accent_violet
import org.jetbrains.compose.resources.stringResource

/** Spoken name of a colour (pickers announce it to screen readers). */
@Composable
fun accentName(accent: GroupAccent): String = stringResource(
    when (accent) {
        GroupAccent.BLUE -> Res.string.accent_blue
        GroupAccent.ORANGE -> Res.string.accent_orange
        GroupAccent.VIOLET -> Res.string.accent_violet
        GroupAccent.GREEN -> Res.string.accent_green
        GroupAccent.PINK -> Res.string.accent_pink
        GroupAccent.AMBER -> Res.string.accent_amber
        GroupAccent.CYAN -> Res.string.accent_cyan
        GroupAccent.GRAPHITE -> Res.string.accent_graphite
    },
)
