package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/**
 * A suggestion the user can act on or put off (link your account, a new version): icon, title and
 * text, then the actions at the end. Same surface as [OwlsplitContainer], with room around them.
 */
@Composable
fun CalloutCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    /** Null when the suggestion cannot be put off (e.g. an update already downloaded). */
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(OwlsplitShapes.container)
            .background(Owlsplit.colors.surface, OwlsplitShapes.container)
            .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(
                Modifier.size(Sizes.iconTile).background(Owlsplit.colors.accentContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Owlsplit.colors.accentText,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    title,
                    style = Owlsplit.type.bodyStrong,
                    color = Owlsplit.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(body, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissLabel != null && onDismiss != null) TextActionButton(dismissLabel, onDismiss)
            CompactPillButton(actionLabel, onAction)
        }
    }
}

/** A primary action that does not own the screen (inside a card): accent pill as wide as its label. */
@Composable
fun CompactPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = OwlsplitShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = Owlsplit.colors.accent,
            contentColor = Owlsplit.colors.onAccent,
            disabledContainerColor = Owlsplit.colors.surfaceVariant,
            disabledContentColor = Owlsplit.colors.textMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Spacing.l),
        modifier = modifier.heightIn(min = Sizes.touchTarget),
    ) {
        Text(text, style = Owlsplit.type.bodyStrong)
    }
}
