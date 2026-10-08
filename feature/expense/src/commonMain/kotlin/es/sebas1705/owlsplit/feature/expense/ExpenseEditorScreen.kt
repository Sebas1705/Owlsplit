package es.sebas1705.owlsplit.feature.expense

import es.sebas1705.owlsplit.core.ui.format.formatDecimal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.Trash
import compose.icons.tablericons.X
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.AmountKeypad
import es.sebas1705.owlsplit.core.ui.components.AvatarToggle
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.OwlsplitTextField
import es.sebas1705.owlsplit.core.ui.components.PrimaryPillButton
import es.sebas1705.owlsplit.core.ui.components.SegmentedControl
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.LocalMoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.OwlsplitShapes
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.core.ui.theme.categoryIcon
import es.sebas1705.owlsplit.core.ui.theme.categoryName
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.domain.ledger.SplitIssue
import es.sebas1705.owlsplit.domain.usecases.expenses.DraftProblem
import es.sebas1705.owlsplit.feature.expense.resources.Res
import es.sebas1705.owlsplit.feature.expense.resources.amount_hint
import es.sebas1705.owlsplit.feature.expense.resources.backspace
import es.sebas1705.owlsplit.feature.expense.resources.cancel
import es.sebas1705.owlsplit.feature.expense.resources.category_label
import es.sebas1705.owlsplit.feature.expense.resources.close
import es.sebas1705.owlsplit.feature.expense.resources.delete
import es.sebas1705.owlsplit.feature.expense.resources.delete_body
import es.sebas1705.owlsplit.feature.expense.resources.delete_title
import es.sebas1705.owlsplit.feature.expense.resources.done
import es.sebas1705.owlsplit.feature.expense.resources.edit_expense
import es.sebas1705.owlsplit.feature.expense.resources.exact_extra
import es.sebas1705.owlsplit.feature.expense.resources.exact_missing
import es.sebas1705.owlsplit.feature.expense.resources.invalid_split
import es.sebas1705.owlsplit.feature.expense.resources.missing_amount
import es.sebas1705.owlsplit.feature.expense.resources.missing_rate
import es.sebas1705.owlsplit.feature.expense.resources.payers_extra
import es.sebas1705.owlsplit.feature.expense.resources.payers_missing
import es.sebas1705.owlsplit.feature.expense.resources.missing_title
import es.sebas1705.owlsplit.feature.expense.resources.new_expense
import es.sebas1705.owlsplit.feature.expense.resources.no_beneficiary
import es.sebas1705.owlsplit.feature.expense.resources.no_payer
import es.sebas1705.owlsplit.feature.expense.resources.not_included
import es.sebas1705.owlsplit.feature.expense.resources.paid_by
import es.sebas1705.owlsplit.feature.expense.resources.percent_extra
import es.sebas1705.owlsplit.feature.expense.resources.percent_missing
import es.sebas1705.owlsplit.feature.expense.resources.save
import es.sebas1705.owlsplit.feature.expense.resources.split_between
import es.sebas1705.owlsplit.feature.expense.resources.title_label
import es.sebas1705.owlsplit.feature.expense.resources.title_placeholder
import es.sebas1705.owlsplit.feature.expense.resources.type_equal
import es.sebas1705.owlsplit.feature.expense.resources.type_exact
import es.sebas1705.owlsplit.feature.expense.resources.type_percent
import es.sebas1705.owlsplit.feature.expense.resources.type_weights
import es.sebas1705.owlsplit.feature.expense.resources.you
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs

@Composable
fun ExpenseEditorRoute(
    groupId: String,
    expenseId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The typed amounts use the separator of the app language (the key keeps one per language).
    val separator = LocalMoneyFormat.current.decimalSeparator
    val viewModel = koinViewModel<ExpenseEditorViewModel>(key = "expense-$groupId-$expenseId-$separator") {
        parametersOf(groupId, expenseId, separator)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    ExpenseEditorScreen(state, viewModel::onEvent, onClose, modifier)
}

@Composable
fun ExpenseEditorScreen(
    state: ExpenseEditorState,
    onEvent: (ExpenseEditorIntent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    format: MoneyFormat = LocalMoneyFormat.current,
) {
    if (!state.loaded) return
    val myMemberId = state.myMemberId
    val currency = Currency.of(state.currency)
    Column(modifier.fillMaxSize().background(Owlsplit.colors.background).imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(TablerIcons.X, stringResource(Res.string.close), tint = Owlsplit.colors.textPrimary)
            }
            Text(
                stringResource(if (state.editing) Res.string.edit_expense else Res.string.new_expense),
                style = Owlsplit.type.bodyStrong,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.editing) {
                IconButton(onClick = { onEvent(ExpenseEditorIntent.AskDelete) }) {
                    Icon(TablerIcons.Trash, stringResource(Res.string.delete), tint = Owlsplit.colors.error)
                }
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Amount(state, format, currency) { onEvent(ExpenseEditorIntent.KeypadOpen(!state.keypadOpen)) }
            CurrencyLine(state, format, onEvent)
            if (state.keypadOpen) {
                AmountKeypad(
                    format.decimalSeparator,
                    stringResource(Res.string.backspace),
                    { onEvent(ExpenseEditorIntent.Key(it)) }
                )
            } else {
                Details(state, onEvent, format, currency, myMemberId)
            }
        }
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (state.keypadOpen) {
                PrimaryPillButton(stringResource(Res.string.done), { onEvent(ExpenseEditorIntent.KeypadOpen(false)) })
            } else {
                Problem(state, format, currency)
                PrimaryPillButton(
                    stringResource(Res.string.save),
                    { onEvent(ExpenseEditorIntent.Save) },
                    enabled = state.check.canSave
                )
            }
        }
    }
    if (state.confirmDelete) DeleteDialog(onEvent)
}

@Composable
private fun Amount(state: ExpenseEditorState, format: MoneyFormat, currency: Currency, onClick: () -> Unit) {
    val symbol = MoneyFormat.symbolOf(currency.code)
    val typed = state.amount.text(format.decimalSeparator)
    val shown = when {
        !state.keypadOpen && state.amount.minorUnits > 0 -> format.format(state.amount.minorUnits, currency)
        format.symbolBefore -> "$symbol$typed"
        else -> "$typed $symbol"
    }
    val spoken = format.format(state.amount.minorUnits, currency)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            shown,
            style = Owlsplit.type.amountDisplay,
            color = if (state.amount.minorUnits > 0) Owlsplit.colors.textPrimary else Owlsplit.colors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (!state.keypadOpen) {
            Text(
                stringResource(Res.string.amount_hint),
                style = Owlsplit.type.caption,
                color = Owlsplit.colors.textSecondary
            )
        }
    }
}

@Composable
private fun Details(
    state: ExpenseEditorState,
    onEvent: (ExpenseEditorIntent) -> Unit,
    format: MoneyFormat,
    currency: Currency,
    myMemberId: String?,
) {
    OwlsplitTextField(
        value = state.draft.title,
        onValueChange = { onEvent(ExpenseEditorIntent.TitleChanged(it)) },
        label = stringResource(Res.string.title_label),
        placeholder = stringResource(Res.string.title_placeholder),
    )
    Text(stringResource(Res.string.category_label), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
    Categories(state.draft.category) { onEvent(ExpenseEditorIntent.CategoryChanged(it)) }
    if (state.foreign) Rate(state, onEvent)
    Payers(state, myMemberId, onEvent)
    Text(stringResource(Res.string.split_between), style = Owlsplit.type.label, color = Owlsplit.colors.textSecondary)
    val types = SplitType.entries
    SegmentedControl(
        listOf(
            stringResource(Res.string.type_equal),
            stringResource(Res.string.type_weights),
            stringResource(Res.string.type_exact),
            stringResource(Res.string.type_percent),
        ),
        selectedIndex = types.indexOf(state.draft.splitType),
        onSelect = { onEvent(ExpenseEditorIntent.TypeChanged(types[it])) },
    )
    if (state.draft.splitType == SplitType.EQUAL) {
        EqualSplit(state, format, currency) { onEvent(ExpenseEditorIntent.Toggle(it)) }
    } else {
        ValueSplit(state, format, currency, myMemberId, onEvent)
    }
    DateAndNote(state, onEvent)
}

@Composable
private fun Categories(selected: ExpenseCategory, onSelect: (ExpenseCategory) -> Unit) {
    val colors = Owlsplit.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(ExpenseCategory.entries) { category ->
            val isSelected = category == selected
            Row(
                Modifier
                    .heightIn(min = Sizes.touchTarget)
                    .background(if (isSelected) colors.accentContainer else colors.surface, OwlsplitShapes.pill)
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(category) }
                    .padding(horizontal = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    categoryIcon(category.name),
                    null,
                    tint = if (isSelected) colors.accentText else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    categoryName(category.name),
                    style = Owlsplit.type.label,
                    color = if (isSelected) colors.accentText else colors.textPrimary,
                )
            }
        }
    }
}

@Composable
internal fun memberName(member: Member, myMemberId: String?): String =
    if (member.id == myMemberId) stringResource(Res.string.you, member.name) else member.name

@Composable
internal fun PayerRow(state: ExpenseEditorState, myMemberId: String?, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val payer = state.members.firstOrNull { it.id == state.draft.payerId }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.rowMinHeight)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.paid_by),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                payer?.let { memberName(it, myMemberId) }.orEmpty(),
                style = Owlsplit.type.bodyStrong,
                color = Owlsplit.colors.textPrimary
            )
            Icon(
                TablerIcons.ChevronDown,
                null,
                tint = Owlsplit.colors.textMuted,
                modifier = Modifier.padding(start = Spacing.xs).size(18.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.members.filter { it.active }.forEach { member ->
                DropdownMenuItem(
                    text = { Text(memberName(member, myMemberId), style = Owlsplit.type.body) },
                    onClick = {
                        open = false
                        onSelect(member.id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualSplit(
    state: ExpenseEditorState,
    format: MoneyFormat,
    currency: Currency,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        state.members.forEach { member ->
            val share = state.check.shares[member.id]
            val included = member.id in state.draft.values
            val shareText = if (included && share != null) {
                format.format(
                    share,
                    currency
                )
            } else {
                stringResource(Res.string.not_included)
            }
            // The plain name: the avatar shows its initials.
            AvatarToggle(
                member.name,
                included,
                if (included && share == null) "—" else shareText,
                onToggle = { onToggle(member.id) },
            )
        }
    }
}

@Composable
private fun ValueSplit(
    state: ExpenseEditorState,
    format: MoneyFormat,
    currency: Currency,
    myMemberId: String?,
    onEvent: (ExpenseEditorIntent) -> Unit,
) {
    val colors = Owlsplit.colors
    OwlsplitContainer {
        state.members.forEach { member ->
            val share = state.check.shares[member.id]
            val name = memberName(member, myMemberId)
            Row(
                Modifier.fillMaxWidth().heightIn(
                    min = Sizes.rowMinHeight
                ).padding(horizontal = Spacing.m, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = Owlsplit.type.bodyStrong, color = colors.textPrimary)
                    Text(
                        share?.let { format.format(it, currency) } ?: "—",
                        style = Owlsplit.type.caption,
                        color = colors.textSecondary,
                    )
                }
                OutlinedTextField(
                    value = state.valueTexts[member.id].orEmpty(),
                    onValueChange = { onEvent(ExpenseEditorIntent.ValueChanged(member.id, it)) },
                    singleLine = true,
                    textStyle = Owlsplit.type.body.copy(color = colors.textPrimary, textAlign = TextAlign.End),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (state.draft.splitType == SplitType.WEIGHTS) KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    shape = OwlsplitShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.divider,
                        cursorColor = colors.accent,
                    ),
                    modifier = Modifier.width(112.dp).semantics { contentDescription = name },
                )
            }
        }
    }
}

@Composable
private fun Problem(state: ExpenseEditorState, format: MoneyFormat, currency: Currency) {
    val text = when (val problem = state.check.problem) {
        null -> return
        DraftProblem.MissingAmount -> stringResource(Res.string.missing_amount)
        DraftProblem.MissingTitle -> stringResource(Res.string.missing_title)
        DraftProblem.NoPayer -> stringResource(Res.string.no_payer)
        DraftProblem.MissingRate -> stringResource(Res.string.missing_rate)
        is DraftProblem.PayersMismatch -> {
            val amount = format.format(abs(problem.missingMinor), currency)
            stringResource(if (problem.missingMinor > 0) Res.string.payers_missing else Res.string.payers_extra, amount)
        }
        is DraftProblem.Split -> when (val issue = problem.issue) {
            SplitIssue.NoBeneficiary -> stringResource(Res.string.no_beneficiary)
            is SplitIssue.ExactSumMismatch -> {
                val amount = format.format(abs(issue.missingMinor), currency)
                stringResource(if (issue.missingMinor > 0) Res.string.exact_missing else Res.string.exact_extra, amount)
            }
            is SplitIssue.PercentSumMismatch -> {
                val percent = formatDecimal(abs(issue.missingHundredths), 2, format.decimalSeparator)
                stringResource(
                    if (issue.missingHundredths > 0) Res.string.percent_missing else Res.string.percent_extra,
                    percent
                )
            }
            else -> stringResource(Res.string.invalid_split)
        }
    }
    Text(
        text,
        style = Owlsplit.type.body,
        color = Owlsplit.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DeleteDialog(onEvent: (ExpenseEditorIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(ExpenseEditorIntent.DismissDelete) },
        containerColor = Owlsplit.colors.surface,
        title = {
            Text(
                stringResource(Res.string.delete_title),
                style = Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary
            )
        },
        text = {
            Text(
                stringResource(Res.string.delete_body),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textSecondary
            )
        },
        confirmButton = {
            TextActionButton(
                stringResource(Res.string.delete),
                { onEvent(ExpenseEditorIntent.ConfirmDelete) },
                color = Owlsplit.colors.error
            )
        },
        dismissButton = {
            TextActionButton(
                stringResource(Res.string.cancel),
                { onEvent(ExpenseEditorIntent.DismissDelete) }
            )
        },
    )
}
