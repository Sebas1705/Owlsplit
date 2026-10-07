package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing

/** Big centred balance: green when you are owed, neutral when you owe (never red). */
@Composable
fun BalanceHero(
    label: String,
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    modifier: Modifier = Modifier,
    spokenDescription: String? = null,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = Owlsplit.type.caption, color = Owlsplit.colors.textSecondary)
        AmountText(
            amountMinor = amountMinor,
            currency = currency,
            format = format,
            style = Owlsplit.type.amountDisplay,
            color = if (amountMinor > 0) Owlsplit.colors.positive else Owlsplit.colors.textPrimary,
            showPlus = true,
            spokenDescription = spokenDescription,
        )
        footer()
    }
}
