package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes

/**
 * A labelled switch: the whole row toggles, so the label is the switch's name for screen
 * readers ("Varios pagadores, interruptor, desactivado") and the target is the full row.
 */
@Composable
fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Side padding inside the press area: in a container the ripple then fills the whole row. */
    horizontalPadding: Dp = 0.dp,
    /** The press area's shape: rectangle in a container (which clips it), rounded on its own. */
    shape: Shape = RectangleShape,
) {
    val colors = Owlsplit.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .clip(shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Owlsplit.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceVariant,
                uncheckedBorderColor = colors.textMuted,
            )
        )
    }
}
