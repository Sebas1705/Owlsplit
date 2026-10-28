package es.sebas1705.owlsplit.feature.groups.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsLeftRight
import compose.icons.tablericons.Check
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.EmptyState
import es.sebas1705.owlsplit.core.ui.components.OwlsplitContainer
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.domain.usecases.balances.BalanceExplanation
import es.sebas1705.owlsplit.domain.usecases.balances.MemberBreakdown
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.all_settled_body
import es.sebas1705.owlsplit.feature.groups.resources.all_settled_title
import es.sebas1705.owlsplit.feature.groups.resources.close
import es.sebas1705.owlsplit.feature.groups.resources.explain_adds_up
import es.sebas1705.owlsplit.feature.groups.resources.explain_balance
import es.sebas1705.owlsplit.feature.groups.resources.explain_balances
import es.sebas1705.owlsplit.feature.groups.resources.explain_conversion
import es.sebas1705.owlsplit.feature.groups.resources.explain_fewer
import es.sebas1705.owlsplit.feature.groups.resources.explain_fewer_detail
import es.sebas1705.owlsplit.feature.groups.resources.explain_paid
import es.sebas1705.owlsplit.feature.groups.resources.explain_payments
import es.sebas1705.owlsplit.feature.groups.resources.explain_received
import es.sebas1705.owlsplit.feature.groups.resources.explain_sent
import es.sebas1705.owlsplit.feature.groups.resources.explain_settle_body
import es.sebas1705.owlsplit.feature.groups.resources.explain_settle_title
import es.sebas1705.owlsplit.feature.groups.resources.explain_share
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_card
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_nothing
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_paid
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_received
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_sent
import es.sebas1705.owlsplit.feature.groups.resources.explain_spoken_share
import es.sebas1705.owlsplit.feature.groups.resources.explain_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Where does this come from?" as a sheet over the group: it opens fully expanded, so it reads like a page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BalanceExplanationSheet(
    explanation: BalanceExplanation,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Owlsplit.colors.background,
    ) {
        BalanceExplanationContent(explanation, detail, currency, format, onDismiss)
    }
}

/**
 * Each member's balance as arithmetic (paid − share + sent − received = balance, only the rows that are not
 * zero) and why the suggested payments are so few. Screen readers hear each card as one sentence.
 */
@Composable
fun BalanceExplanationContent(
    explanation: BalanceExplanation,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nothingYet = explanation.members.all { it.isEmpty }
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.explain_title),
                    style = Owlsplit.type.title,
                    color = Owlsplit.colors.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextActionButton(stringResource(Res.string.close), onClose)
            }
        }
        if (explanation.settled && nothingYet) {
            item(key = "settled") {
                EmptyState(stringResource(Res.string.all_settled_title), stringResource(Res.string.all_settled_body))
            }
            return@LazyColumn
        }
        item(key = "balances") { SectionTitle(stringResource(Res.string.explain_balances)) }
        explanation.members.forEach { row ->
            item(
                key = "member-${row.memberId}"
            ) { BreakdownCard(row, memberLabel(row.memberId, detail), currency, format) }
        }
        item(key = "settle") { SectionTitle(stringResource(Res.string.explain_settle_title)) }
        item(key = "why") {
            if (explanation.settled) {
                EmptyState(stringResource(Res.string.all_settled_title), stringResource(Res.string.all_settled_body))
            } else {
                Why(explanation, detail, currency, format)
            }
        }
        if (explanation.usesConversion) {
            item(key = "conversion") {
                Text(
                    stringResource(Res.string.explain_conversion, currency.code),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = Owlsplit.type.label,
        color = Owlsplit.colors.textSecondary,
        modifier = Modifier.semantics { heading() },
    )
}

/** One line of a member's arithmetic: [sign] is how it enters the balance ("" for the first one, paid). */
private class Line(val label: StringResource, val spoken: StringResource, val sign: String, val amountMinor: Long)

@Composable
private fun BreakdownCard(row: MemberBreakdown, name: String, currency: Currency, format: MoneyFormat) {
    val lines = listOf(
        Line(Res.string.explain_paid, Res.string.explain_spoken_paid, "", row.paidMinor),
        Line(Res.string.explain_share, Res.string.explain_spoken_share, MINUS, row.shareMinor),
        Line(Res.string.explain_sent, Res.string.explain_spoken_sent, "+", row.sentMinor),
        Line(Res.string.explain_received, Res.string.explain_spoken_received, MINUS, row.receivedMinor),
    ).filter { it.amountMinor != 0L }
    val balance = format.format(row.balanceMinor, currency, showPlus = true)
    val spokenLines = lines.map { stringResource(it.spoken, format.format(it.amountMinor, currency)) }
    val spoken = if (lines.isEmpty()) {
        stringResource(Res.string.explain_spoken_nothing, name, balance)
    } else {
        stringResource(Res.string.explain_spoken_card, name, spokenLines.joinToString(", "), balance)
    }
    val colors = Owlsplit.colors
    OwlsplitContainer(Modifier.clearAndSetSemantics { contentDescription = spoken }, title = name) {
        lines.forEachIndexed { index, line ->
            // The first line needs no sign unless it adds to something before it.
            val sign = if (index == 0 && line.sign == "+") "" else line.sign
            AmountLine(stringResource(line.label), sign + format.format(line.amountMinor, currency))
        }
        if (lines.isNotEmpty()) {
            Box(
                Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).fillMaxWidth().height(1.dp)
                    .background(colors.divider),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(Res.string.explain_balance),
                style = Owlsplit.type.bodyStrong,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (row.addsUp) {
                Icon(
                    TablerIcons.Check,
                    stringResource(Res.string.explain_adds_up),
                    tint = colors.accentText,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                balance,
                style = Owlsplit.type.bodyStrong,
                color = if (row.balanceMinor > 0) colors.positive else colors.textPrimary,
            )
        }
    }
}

@Composable
private fun AmountLine(label: String, amount: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Owlsplit.type.body, color = Owlsplit.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(amount, style = Owlsplit.type.body, color = Owlsplit.colors.textPrimary)
    }
}

/** The suggested payments (the same the settle tab shows) and, when it helps, how many debts they replace. */
@Composable
private fun Why(explanation: BalanceExplanation, detail: GroupDetail, currency: Currency, format: MoneyFormat) {
    val payments = explanation.suggestedPayments
    OwlsplitContainer {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(Res.string.explain_settle_body),
                style = Owlsplit.type.body,
                color = Owlsplit.colors.textSecondary,
            )
            if (explanation.simplifies) {
                val count = pluralStringResource(Res.plurals.explain_payments, payments.size, payments.size)
                Text(
                    stringResource(Res.string.explain_fewer, count, explanation.pairwiseDebts),
                    style = Owlsplit.type.bodyStrong,
                    color = Owlsplit.colors.textPrimary,
                )
                Text(
                    stringResource(Res.string.explain_fewer_detail, explanation.pairwiseDebts),
                    style = Owlsplit.type.caption,
                    color = Owlsplit.colors.textSecondary,
                )
            }
        }
        payments.forEach { payment ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(
                    TablerIcons.ArrowsLeftRight,
                    null,
                    tint = Owlsplit.colors.accentText,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    paymentText(payment, detail, currency, format),
                    style = Owlsplit.type.body,
                    color = Owlsplit.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private const val MINUS = "−"
