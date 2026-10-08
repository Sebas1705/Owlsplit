package es.sebas1705.owlsplit.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Wraps the app and marks debug builds with a small "debug" badge, so a screenshot or a
 * phone in hand says which app it is. Release builds (testers and users) show nothing.
 */
@Composable
fun DebugBadge(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (isDebugBuild) {
            Surface(
                modifier = Modifier
                    // In the status bar strip: over the content it would cover real controls.
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "debug",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
