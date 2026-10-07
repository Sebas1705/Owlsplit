package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** An invitation, not an apology: logo, headline, one line and a verb. */
@Composable
fun EmptyState(title: String, body: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OwlsplitLogo(size = 56.dp)
        Text(title, style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary, textAlign = TextAlign.Center)
        Text(body, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary, textAlign = TextAlign.Center)
        PrimaryPillButton(actionLabel, onAction)
    }
}
