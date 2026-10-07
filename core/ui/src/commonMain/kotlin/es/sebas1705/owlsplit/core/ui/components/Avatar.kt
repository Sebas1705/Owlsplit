package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit

/** Initials on a neutral tone. Decorative: the name is always next to it, so screen readers skip it. */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = Owlsplit.colors.surfaceVariant,
    content: Color = Owlsplit.colors.textPrimary,
) {
    Box(
        modifier.size(size).background(background, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(initials(name), style = Owlsplit.type.label, color = content)
    }
}

internal fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
