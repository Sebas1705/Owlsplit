package es.sebas1705.owlsplit.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Backspace
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.TABULAR

private val keyRows = listOf(
    listOf(KeypadKey.Digit(1), KeypadKey.Digit(2), KeypadKey.Digit(3)),
    listOf(KeypadKey.Digit(4), KeypadKey.Digit(5), KeypadKey.Digit(6)),
    listOf(KeypadKey.Digit(7), KeypadKey.Digit(8), KeypadKey.Digit(9)),
    listOf(KeypadKey.Decimal, KeypadKey.Digit(0), KeypadKey.Backspace),
)

/** Numeric keypad for the expense editor; [decimalSeparator] follows the locale. */
@Composable
fun AmountKeypad(
    decimalSeparator: Char,
    backspaceLabel: String,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        keyRows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button, onClick = { onKey(key) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            is KeypadKey.Digit -> KeyText(key.value.toString())
                            KeypadKey.Decimal -> KeyText(decimalSeparator.toString())
                            KeypadKey.Backspace -> Icon(
                                TablerIcons.Backspace,
                                contentDescription = backspaceLabel,
                                tint = Owlsplit.colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, style = Owlsplit.type.title.copy(fontFeatureSettings = TABULAR), color = Owlsplit.colors.textPrimary)
}
