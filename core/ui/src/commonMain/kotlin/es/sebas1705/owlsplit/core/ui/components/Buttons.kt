package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** The one primary action of a screen: wide accent pill. */
@Composable
fun PrimaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
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
        // Flat in every state (Material lifts it on hover and focus): no shadows in the design system.
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.fillMaxWidth().height(Sizes.primaryButtonHeight),
    ) {
        Text(text, style = Owlsplit.type.bodyStrong)
    }
}

/** Round shortcut under the balance (Expense, Settle, Group, Join). */
@Composable
fun QuickActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier.size(Sizes.quickAction).background(Owlsplit.colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Owlsplit.colors.textPrimary, modifier = Modifier.size(22.dp))
        }
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
    }
}
