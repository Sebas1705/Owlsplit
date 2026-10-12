package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Member in the split editor: tap to include (accent) or exclude (neutral); shows their share. */
@Composable
fun AvatarToggle(
    name: String,
    included: Boolean,
    shareText: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Owlsplit.colors
    val press = rememberPress()
    Column(
        modifier
            .widthIn(min = Sizes.touchTarget)
            .toggleable(
                value = included,
                interactionSource = press,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onToggle,
            )
            .semantics(mergeDescendants = true) { contentDescription = "$name, $shareText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Avatar(
            name,
            modifier = Modifier.pressFeedback(press, CircleShape),
            size = 36.dp,
            background = if (included) colors.accent else colors.surfaceVariant,
            content = if (included) colors.onAccent else colors.textMuted,
        )
        Text(shareText, style = Owlsplit.type.caption, color = if (included) colors.textSecondary else colors.textMuted)
    }
}
