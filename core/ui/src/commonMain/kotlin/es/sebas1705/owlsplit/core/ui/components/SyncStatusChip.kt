package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Check
import compose.icons.tablericons.CloudOff
import compose.icons.tablericons.CloudUpload
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Small pill with the sync state; [text] comes localised from the caller ("1 cambio pendiente"). */
@Composable
fun SyncStatusChip(status: SyncStatus, text: String, modifier: Modifier = Modifier) {
    val colors = Owlsplit.colors
    val (icon, tint) = when (status) {
        SyncStatus.SYNCED -> TablerIcons.Check to colors.accentText
        SyncStatus.PENDING -> TablerIcons.CloudUpload to colors.textSecondary
        SyncStatus.OFFLINE -> TablerIcons.CloudOff to colors.textSecondary
        SyncStatus.ERROR -> TablerIcons.AlertTriangle to colors.warning
    }
    Row(
        modifier
            .background(colors.surface, OwlsplitShapes.pill)
            .padding(horizontal = Spacing.s + 2.dp, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text, style = Owlsplit.type.caption, color = colors.textSecondary)
    }
}
