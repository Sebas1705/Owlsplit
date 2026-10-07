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
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
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
        // Large avatars (profile) get a title-sized initial; list avatars keep the label size.
        val style = if (size >= LARGE_AVATAR) Owlsplit.type.title else Owlsplit.type.label
        Text(initials(name), style = style, color = content)
    }
}

internal fun initials(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/** The user's avatar: initials on their colour (`accent:<GroupAccent>`, see `UserProfile.avatar`). */
@Composable
fun ProfileAvatar(name: String, accent: GroupAccent, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val colors = Owlsplit.colors
    Avatar(name, modifier, size, background = accent.container(colors), content = accent.content(colors))
}
private val LARGE_AVATAR = 64.dp
