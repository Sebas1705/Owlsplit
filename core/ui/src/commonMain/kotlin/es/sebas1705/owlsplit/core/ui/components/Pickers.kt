package es.sebas1705.owlsplit.core.ui.components

import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.Check
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Every supported currency, searchable by code; [selected] is ticked. */
@Composable
fun CurrencyPicker(
    selected: String,
    title: String,
    searchLabel: String,
    cancelLabel: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val codes = remember(query) {
        Currency.all.map { it.code }.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Owlsplit.colors.surface,
        title = { Text(title, style = Owlsplit.type.title, color = Owlsplit.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                OwlsplitTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = searchLabel,
                    maxLength = Currency.CODE_LENGTH,
                    capitalization = KeyboardCapitalization.Characters,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(codes) { code ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Sizes.touchTarget)
                                .clip(OwlsplitShapes.control)
                                .clickable(role = Role.Button) { onSelect(code) }
                                .padding(horizontal = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(code, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
                            Text(
                                MoneyFormat.symbolOf(code).takeIf { it != code }.orEmpty(),
                                style = Owlsplit.type.body,
                                color = Owlsplit.colors.textSecondary,
                                modifier = Modifier.weight(1f).padding(start = Spacing.s),
                            )
                            if (code == selected) {
                                Icon(
                                    TablerIcons.Check,
                                    null,
                                    tint = Owlsplit.colors.accentText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextActionButton(cancelLabel, onDismiss) },
    )
}

/**
 * A date row ("Date · Jul 12") that opens the Material date picker. [text] is the date as shown;
 * the picker works in UTC midnights, converted here so the chosen day never shifts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    text: String,
    date: LocalDate,
    confirmLabel: String,
    cancelLabel: String,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(TablerIcons.Calendar, null, tint = Owlsplit.colors.textSecondary, modifier = Modifier.size(20.dp))
        Text(label, style = Owlsplit.type.body, color = Owlsplit.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(text, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
    }
    if (!open) return
    val state =
        rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextActionButton(confirmLabel, {
                state.selectedDateMillis?.let {
                    onChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                }
                open = false
            })
        },
        dismissButton = { TextActionButton(cancelLabel, { open = false }) },
        colors = DatePickerDefaults.colors(containerColor = Owlsplit.colors.surface),
    ) {
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = Owlsplit.colors.surface,
                selectedDayContainerColor = Owlsplit.colors.accent,
                todayDateBorderColor = Owlsplit.colors.accent,
            ),
        )
    }
}
