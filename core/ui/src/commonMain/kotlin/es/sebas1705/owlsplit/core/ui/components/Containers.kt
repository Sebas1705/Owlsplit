package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** White (graphite in dark) rounded container that holds rows. No shadow: depth comes from contrast. */
@Composable
fun OwlsplitContainer(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(OwlsplitShapes.container)
            .background(Owlsplit.colors.surface, OwlsplitShapes.container)
            .padding(vertical = Spacing.xs),
    ) {
        if (title != null) {
            Text(
                title,
                style = Owlsplit.type.bodyStrong,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
                    .semantics { heading() },
            )
        }
        content()
    }
}
