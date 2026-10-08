package es.sebas1705.owlsplit.feature.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.SwitchRow
import es.sebas1705.owlsplit.core.ui.components.CurrencyPicker
import es.sebas1705.owlsplit.core.ui.components.DateField
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.dayLabel
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.groups.Today
import es.sebas1705.owlsplit.feature.expense.resources.Res
import es.sebas1705.owlsplit.feature.expense.resources.cancel
import es.sebas1705.owlsplit.feature.expense.resources.currency_change
import es.sebas1705.owlsplit.feature.expense.resources.currency_search
import es.sebas1705.owlsplit.feature.expense.resources.currency_title
import es.sebas1705.owlsplit.feature.expense.resources.date_label
import es.sebas1705.owlsplit.feature.expense.resources.in_group_currency
import es.sebas1705.owlsplit.feature.expense.resources.note_label
import es.sebas1705.owlsplit.feature.expense.resources.ok
import es.sebas1705.owlsplit.feature.expense.resources.rate_ecb
import es.sebas1705.owlsplit.feature.expense.resources.rate_label
import es.sebas1705.owlsplit.feature.expense.resources.rate_loading
import es.sebas1705.owlsplit.feature.expense.resources.rate_manual
import es.sebas1705.owlsplit.feature.expense.resources.rate_missing_hint
import es.sebas1705.owlsplit.feature.expense.resources.rate_row
import es.sebas1705.owlsplit.feature.expense.resources.rate_stale
import es.sebas1705.owlsplit.feature.expense.resources.several_payers
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** The currency chip under the amount, and the amount in the group currency when they differ. */
@Composable
internal fun CurrencyLine(state: ExpenseEditorState, format: MoneyFormat, onEvent: (ExpenseEditorIntent) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(Res.string.currency_change, state.currency)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .heightIn(min = Sizes.touchTarget)
                .background(Owlsplit.colors.surface, OwlsplitShapes.pill)
                .clickable(role = Role.Button) { picking = true }
                .semantics { contentDescription = description }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(state.currency, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.textPrimary)
            Icon(TablerIcons.ChevronDown, null, tint = Owlsplit.colors.textMuted, modifier = Modifier.size(16.dp))
        }
        state.baseEquivalent?.let {
            Text(
                stringResource(Res.string.in_group_currency, format.format(it, Currency.of(state.baseCurrency))),
                style = Owlsplit.type.caption,
                color = Owlsplit.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
    if (picking) {
        CurrencyPicker(
            selected = state.currency,
            title = stringResource(Res.string.currency_title),
            searchLabel = stringResource(Res.string.currency_search),
            cancelLabel = stringResource(Res.string.cancel),
            onSelect = {
                picking = false
                onEvent(ExpenseEditorIntent.CurrencyChanged(it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** "1 USD = [0,92345] EUR" with where the rate came from. */
@Composable
internal fun Rate(state: ExpenseEditorState, onEvent: (ExpenseEditorIntent) -> Unit) {
    val colors = Owlsplit.colors
    val rate = state.rate
    OwlsplitContainer(title = stringResource(Res.string.rate_label)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(Res.string.rate_row, state.currency),
                style = Owlsplit.type.body,
                color = colors.textPrimary
            )
            NumberField(
                value = rate.text,
                onValueChange = { onEvent(ExpenseEditorIntent.RateChanged(it)) },
                description = stringResource(Res.string.rate_label),
                modifier = Modifier.weight(1f),
            )
            Text(state.baseCurrency, style = Owlsplit.type.body, color = colors.textPrimary)
        }
        val hint = when {
            rate.loading -> stringResource(Res.string.rate_loading)
            state.draft.fxRateScaled == null -> stringResource(Res.string.rate_missing_hint)
            rate.day != null && rate.stale -> stringResource(Res.string.rate_stale, readableDay(rate.day))
            rate.day != null -> stringResource(Res.string.rate_ecb, readableDay(rate.day))
            else -> stringResource(Res.string.rate_manual)
        }
        Text(
            hint,
            style = Owlsplit.type.caption,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
}

/** Who paid: one member, or several with their amounts. */
@Composable
internal fun Payers(state: ExpenseEditorState, myMemberId: String?, onEvent: (ExpenseEditorIntent) -> Unit) {
    val colors = Owlsplit.colors
    OwlsplitContainer {
        if (!state.draft.multiplePayers) {
            PayerRow(state, myMemberId) { onEvent(ExpenseEditorIntent.PayerChanged(it)) }
        } else {
            state.members.filter { it.active || it.id in state.draft.payers }.forEach { member ->
                val name = memberName(member, myMemberId)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Sizes.rowMinHeight).padding(horizontal = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = Owlsplit.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    NumberField(
                        value = state.payerTexts[member.id].orEmpty(),
                        onValueChange = { onEvent(ExpenseEditorIntent.PayerAmountChanged(member.id, it)) },
                        description = name,
                        modifier = Modifier.width(112.dp),
                    )
                }
            }
        }
        SwitchRow(
            stringResource(Res.string.several_payers),
            state.draft.multiplePayers,
            { onEvent(ExpenseEditorIntent.MultiplePayers(it)) },
            Modifier.heightIn(min = Sizes.rowMinHeight).padding(horizontal = Spacing.m),
        )
    }
}

@Composable
internal fun DateAndNote(
    state: ExpenseEditorState,
    onEvent: (ExpenseEditorIntent) -> Unit,
    today: LocalDate = Today.System()
) {
    val date = state.draft.date ?: today
    OwlsplitContainer {
        DateField(
            label = stringResource(Res.string.date_label),
            text = dayLabel(date, today),
            date = date,
            confirmLabel = stringResource(Res.string.ok),
            cancelLabel = stringResource(Res.string.cancel),
            onChange = { onEvent(ExpenseEditorIntent.DateChanged(it)) },
        )
    }
    OwlsplitTextField(
        value = state.draft.note.orEmpty(),
        onValueChange = { onEvent(ExpenseEditorIntent.NoteChanged(it)) },
        label = stringResource(Res.string.note_label),
    )
}

/** A short decimal field, right-aligned (amounts, rates, weights). */
@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    val colors = Owlsplit.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = Owlsplit.type.body.copy(color = colors.textPrimary, textAlign = TextAlign.End),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = OwlsplitShapes.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            cursorColor = colors.accent,
        ),
        modifier = modifier.semantics { contentDescription = description },
    )
}

/** "2026-10-07" as "7 oct" (or as it came, if it is not a date). */
@Composable
private fun readableDay(day: String): String =
    runCatching { LocalDate.parse(day) }.getOrNull()?.let { dayLabel(it, Today.System()) } ?: day
