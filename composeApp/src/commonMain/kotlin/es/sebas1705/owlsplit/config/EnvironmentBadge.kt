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
 * Wraps the app and overlays the active profile's name.
 *
 * Hidden in production — a badge that is always visible stops being
 * information. `AppConfig` is generated at build time into `commonMain`, so
 * this works identically on Android, iOS, Desktop and Web without a single
 * platform-specific source file.
 */
@Composable
fun EnvironmentBadge(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (AppConfig.ENVIRONMENT != "production") {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = AppConfig.ENVIRONMENT,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
