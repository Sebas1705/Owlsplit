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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import compose.icons.TablerIcons
import compose.icons.tablericons.BuildingBank
import compose.icons.tablericons.Qrcode
import es.sebas1705.owlsplit.core.ui.components.QrCode
import androidx.compose.ui.text.style.TextAlign
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
import es.sebas1705.owlsplit.domain.usecases.pay.BankTransfer
import es.sebas1705.owlsplit.domain.usecases.pay.PayLinks
import es.sebas1705.owlsplit.domain.usecases.pay.PayMethod
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.cancel
import es.sebas1705.owlsplit.feature.groups.resources.pay_bizum_copied
import es.sebas1705.owlsplit.feature.groups.resources.pay_by_transfer
import es.sebas1705.owlsplit.feature.groups.resources.pay_iban_copied
import es.sebas1705.owlsplit.feature.groups.resources.pay_qr_concept
import es.sebas1705.owlsplit.feature.groups.resources.pay_qr_show
import es.sebas1705.owlsplit.feature.groups.resources.pay_qr_hide
import es.sebas1705.owlsplit.feature.groups.resources.pay_qr_description
import es.sebas1705.owlsplit.feature.groups.resources.pay_qr_hint
import es.sebas1705.owlsplit.feature.groups.resources.pay_hint_add_yours
import es.sebas1705.owlsplit.feature.groups.resources.pay_with
import es.sebas1705.owlsplit.feature.groups.resources.remind_action
import es.sebas1705.owlsplit.feature.groups.resources.settle_body
import es.sebas1705.owlsplit.feature.groups.resources.settle_confirm
import es.sebas1705.owlsplit.feature.groups.resources.settle_did_you_pay
import es.sebas1705.owlsplit.feature.groups.resources.settle_title
import org.jetbrains.compose.resources.stringResource

/**
 * Settling one suggested payment. When I am the one paying and the other person published how to be
 * paid, their services are a tap away (the payment is still recorded here by hand: the app never
 * sees money move). When I am the one being paid, I can send them a reminder with my details or, with
 * an IBAN and in euros, show a QR their banking app scans to fill in the transfer. Coming back from
 * the payment app, it asks whether the payment went through, so it gets recorded.
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
    // Off to Bizum, PayPal, Revolut or the bank, and back: the payment is probably done.
    var paying by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var back by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (paying) left = true }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { if (left) back = true }
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
                    payment.fromMemberId == detail.myMemberId && payeeMethods != null && back ->
                        DidYouPay(payee.name, format.format(payment.amountMinor, currency))
                    payment.fromMemberId == detail.myMemberId && payeeMethods != null ->
                        PayOptions(payee.name, payeeMethods, payment.amountMinor, currency, format) { paying = true }
                    payment.toMemberId == detail.myMemberId ->
                        RequestPayment(detail, payment.amountMinor, currency, onRemind)
                }
            }
        },
        confirmButton = { TextActionButton(stringResource(Res.string.settle_confirm), onConfirm) },
        dismissButton = { TextActionButton(stringResource(Res.string.cancel), onDismiss) },
    )
}

/** Back from paying: Record is the next step. */
@Composable
private fun DidYouPay(name: String, amount: String) {
    Text(
        stringResource(Res.string.settle_did_you_pay, amount, name),
        style = Owlsplit.type.bodyStrong,
        color = Owlsplit.colors.positive,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** [onLeave]: a way to pay was chosen, which takes the user out to another app. */
@Suppress("LongParameterList") // Who, how, how much, and leaving.
@Composable
private fun PayOptions(
    name: String,
    methods: PayMethods,
    amountMinor: Long,
    currency: Currency,
    format: MoneyFormat,
    onLeave: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf<PayMethod?>(null) }
    PayLinks.available(methods).forEach { method ->
        when (method) {
            PayMethod.BIZUM -> OptionRow(TablerIcons.DeviceMobile, stringResource(Res.string.pay_with, "Bizum")) {
                // Bizum has no public link: the phone goes to the clipboard for the bank app.
                clipboard.setText(AnnotatedString(methods.bizum.orEmpty()))
                copied = PayMethod.BIZUM
                onLeave()
            }
            PayMethod.PAYPAL -> OptionRow(TablerIcons.BrandPaypal, stringResource(Res.string.pay_with, "PayPal")) {
                onLeave()
                uri.openUri(PayLinks.paypalUrl(methods.paypal.orEmpty(), amountMinor, currency))
            }
            PayMethod.REVOLUT -> OptionRow(TablerIcons.CreditCard, stringResource(Res.string.pay_with, "Revolut")) {
                onLeave()
                uri.openUri(PayLinks.revolutUrl(methods.revolut.orEmpty()))
            }
            // The IBAN goes to the clipboard, ready to paste as the payee in the banking app.
            PayMethod.IBAN -> OptionRow(TablerIcons.BuildingBank, stringResource(Res.string.pay_by_transfer)) {
                clipboard.setText(AnnotatedString(methods.iban.orEmpty()))
                copied = PayMethod.IBAN
                onLeave()
            }
        }
    }
    val amount = format.format(amountMinor, currency)
    val note = when (copied) {
        PayMethod.BIZUM -> stringResource(Res.string.pay_bizum_copied, methods.bizum.orEmpty(), amount, name)
        PayMethod.IBAN -> stringResource(
            Res.string.pay_iban_copied,
            BankTransfer.readableIban(methods.iban.orEmpty()),
            methods.ibanHolder ?: name,
            amount,
        )
        else -> null
    }
    if (note != null) {
        Text(
            note,
            style = Owlsplit.type.caption,
            color = Owlsplit.colors.positive,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * The same reminder as the bell on the row (without my pay methods it only says what I am owed) and,
 * with my IBAN and in euros, a QR for them to scan with their banking app here and now.
 */
@Composable
private fun RequestPayment(detail: GroupDetail, amountMinor: Long, currency: Currency, onRemind: () -> Unit) {
    OptionRow(TablerIcons.BellRinging, stringResource(Res.string.remind_action), onRemind)
    val me = detail.members.firstOrNull { it.id == detail.myMemberId }
    val mine = me?.payMethods
    val concept = stringResource(Res.string.pay_qr_concept, detail.group.name)
    val qr = mine?.iban?.let { iban ->
        BankTransfer.epcPayload(mine.ibanHolder ?: me.name, iban, amountMinor, currency, concept)
    }
    if (qr != null) TransferQr(qr)
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

/** A "pay me" QR, shown on demand: the payer scans it with their banking app (EPC, SEPA transfer). */
@Composable
private fun TransferQr(payload: String) {
    var shown by remember { mutableStateOf(false) }
    OptionRow(TablerIcons.Qrcode, stringResource(if (shown) Res.string.pay_qr_hide else Res.string.pay_qr_show)) {
        shown = !shown
    }
    if (shown) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            QrCode(payload, stringResource(Res.string.pay_qr_description), Modifier.size(QR_SIZE))
            Text(
                stringResource(Res.string.pay_qr_hint),
                style = Owlsplit.type.caption,
                color = Owlsplit.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val QR_SIZE = 200.dp
