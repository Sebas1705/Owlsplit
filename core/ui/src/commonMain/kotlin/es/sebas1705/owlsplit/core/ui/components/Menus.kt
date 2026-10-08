package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes

/**
 * A menu in the design system's terms: no shadow (depth is contrast, DESIGN_SYSTEM.md), so a
 * hairline border separates it from a surface of the same colour; control corners.
 */
@Composable
fun OwlsplitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Owlsplit.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = OwlsplitShapes.control,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, colors.divider),
        content = content,
    )
}

/** A menu option; [selected] shows a check (and tells screen readers) for the current choice. */
@Composable
fun OwlsplitMenuItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    color: Color = Owlsplit.colors.textPrimary,
) {
    DropdownMenuItem(
        text = { Text(text, style = Owlsplit.type.body, color = color) },
        onClick = onClick,
        trailingIcon = if (selected) {
            { Icon(TablerIcons.Check, null, Modifier.size(18.dp), tint = Owlsplit.colors.accentText) }
        } else {
            null
        },
        modifier = Modifier.semantics { this.selected = selected },
    )
}
