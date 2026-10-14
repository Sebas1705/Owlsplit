package es.sebas1705.owlsplit.feature.groups.detail

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.BrandPaypal
import compose.icons.tablericons.CreditCard
import compose.icons.tablericons.DeviceMobile
import compose.icons.tablericons.BellRinging
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.TextActionButton
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.theme.Owlsplit
import es.sebas1705.owlsplit.core.ui.theme.Sizes
import es.sebas1705.owlsplit.core.ui.theme.Spacing
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.pay.PayLinks
import es.sebas1705.owlsplit.domain.usecases.pay.PayMethod
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.cancel
import es.sebas1705.owlsplit.feature.groups.resources.pay_bizum_copied
import es.sebas1705.owlsplit.feature.groups.resources.pay_hint_add_yours
import es.sebas1705.owlsplit.feature.groups.resources.pay_with
import es.sebas1705.owlsplit.feature.groups.resources.remind_action
import es.sebas1705.owlsplit.feature.groups.resources.settle_body
import es.sebas1705.owlsplit.feature.groups.resources.settle_confirm
import es.sebas1705.owlsplit.feature.groups.resources.settle_title
import org.jetbrains.compose.resources.stringResource

/**
 * Settling one suggested payment. When I am the one paying and the other person published how to be
 * paid, their services are a tap away (the payment is still recorded here by hand: the app never
 * sees money move). When I am the one being paid, I can send them a reminder with my details.
 */
@Suppress("LongParameterList") // What the dialog shows, the reminder and its two outcomes.
@Composable
internal fun SettleDialog(
    payment: SuggestedPayment,
    text: String,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    onRemind: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val payee = detail.members.firstOrNull { it.id == payment.toMemberId }
    val payeeMethods = payee?.payMethods
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Owlsplit.colors.surface,
        title = {
            Text(
                stringResource(Res.string.settle_title),
                style = Owlsplit.type.title,
                color = Owlsplit.colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    stringResource(Res.string.settle_body, text),
                    style = Owlsplit.type.body,
                    color = Owlsplit.colors.textSecondary,
                )
                when {
                    payment.fromMemberId == detail.myMemberId && payeeMethods != null ->
                        PayOptions(payee.name, payeeMethods, payment.amountMinor, currency, format)
                    payment.toMemberId == detail.myMemberId -> RequestPayment(detail, onRemind)
                }
            }
        },
        confirmButton = { TextActionButton(stringResource(Res.string.settle_confirm), onConfirm) },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), onDismiss) },
    )
}

@Composable
private fun PayOptions(name: String, methods: PayMethods, amountMinor: Long, currency: Currency, format: MoneyFormat) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    PayLinks.available(methods).forEach { method ->
        when (method) {
            PayMethod.BIZUM -> OptionRow(TablerIcons.DeviceMobile, stringResource(Res.string.pay_with, "Bizum")) {
                // Bizum has no public link: the phone goes to the clipboard for the bank app.
                clipboard.setText(AnnotatedString(methods.bizum.orEmpty()))
                copied = true
            }
            PayMethod.PAYPAL -> OptionRow(TablerIcons.BrandPaypal, stringResource(Res.string.pay_with, "PayPal")) {
                uri.openUri(PayLinks.paypalUrl(methods.paypal.orEmpty(), amountMinor, currency))
            }
            PayMethod.REVOLUT -> OptionRow(TablerIcons.CreditCard, stringResource(Res.string.pay_with, "Revolut")) {
                uri.openUri(PayLinks.revolutUrl(methods.revolut.orEmpty()))
            }
        }
    }
    if (copied) {
        Text(
            stringResource(
                Res.string.pay_bizum_copied,
                methods.bizum.orEmpty(),
                format.format(amountMinor, currency),
                name,
            ),
            style = Owlsplit.type.caption,
            color = Owlsplit.colors.positive,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The same reminder as the bell on the row; without my pay methods it only says what I am owed. */
@Composable
private fun RequestPayment(detail: GroupDetail, onRemind: () -> Unit) {
    OptionRow(TablerIcons.BellRinging, stringResource(Res.string.remind_action), onRemind)
    val mine = detail.members.firstOrNull { it.id == detail.myMemberId }?.payMethods
    if (PayLinks.available(mine).isEmpty()) {
        Text(
            stringResource(Res.string.pay_hint_add_yours),
            style = Owlsplit.type.caption,
            color = Owlsplit.colors.textMuted,
        )
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.rowMinHeight)
            .clip(OwlsplitShapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(icon, null, tint = Owlsplit.colors.accentText, modifier = Modifier.size(20.dp))
        Text(label, style = Owlsplit.type.bodyStrong, color = Owlsplit.colors.accentText)
    }
}
