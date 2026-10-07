package es.sebas1705.owlsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.ui.catalog.DesignCatalog
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitTheme

/** Root composable. Shows the design catalog until P9 builds the real navigation. */
@Composable
fun App(modifier: Modifier = Modifier) {
    OwlsplitTheme {
        // Background first, then insets: the colour reaches behind the system bars (edge to edge).
        EnvironmentBadge(modifier.background(Owlsplit.colors.background)) {
            DesignCatalog(Modifier.safeDrawingPadding())
        }
    }
}
