package es.sebas1705.owlsplit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.config.EnvironmentBadge
import es.sebas1705.owlsplit.core.ui.theme.AppTheme

/** Root composable. Navigation and real screens arrive with P8–P9. */
@Composable
fun App(modifier: Modifier = Modifier) {
    AppTheme(modifier) {
        EnvironmentBadge {
            Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Owlsplit", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
