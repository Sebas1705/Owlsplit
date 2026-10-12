package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes

/** Pill with the selected option raised onto the surface colour (Gastos · Saldos · Saldar). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Owlsplit.colors.surfaceVariant, OwlsplitShapes.control)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.touchTarget - 8.dp)
                    .clip(OwlsplitShapes.control)
                    .background(
                        if (selected) Owlsplit.colors.segmentSelected else Color.Transparent,
                        OwlsplitShapes.control
                    )
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = Owlsplit.type.label,
                    color = if (selected) Owlsplit.colors.textPrimary else Owlsplit.colors.textSecondary,
                )
            }
        }
    }
}
