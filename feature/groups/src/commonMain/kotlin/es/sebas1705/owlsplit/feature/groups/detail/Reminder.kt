package es.sebas1705.owlsplit.feature.groups.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.components.TextSharer
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.domain.ledger.SuggestedPayment
import es.sebas1705.owlsplit.domain.usecases.groups.GroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.nameOf
import es.sebas1705.owlsplit.domain.usecases.pay.PaymentReminder
import es.sebas1705.owlsplit.domain.usecases.pay.ReminderWording
import es.sebas1705.owlsplit.feature.groups.resources.Res
import es.sebas1705.owlsplit.feature.groups.resources.remind_bizum
import es.sebas1705.owlsplit.feature.groups.resources.remind_intro
import es.sebas1705.owlsplit.feature.groups.resources.remind_list_last
import es.sebas1705.owlsplit.feature.groups.resources.remind_list_more
import es.sebas1705.owlsplit.feature.groups.resources.remind_pay_by
import es.sebas1705.owlsplit.feature.groups.resources.remind_paypal
import es.sebas1705.owlsplit.feature.groups.resources.remind_revolut
import es.sebas1705.owlsplit.feature.groups.resources.remind_thanks
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the share sheet once with the reminder the view model is holding, then reports it sent.
 * Without a share sheet the message goes to the clipboard, as the invite does.
 */
@Composable
internal fun ReminderEffect(
    reminder: SuggestedPayment?,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
    sharer: TextSharer,
    onSent: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val message = reminder?.let { reminderMessage(it, detail, currency, format) }
    LaunchedEffect(reminder) {
        if (message == null) return@LaunchedEffect
        if (!sharer.share(message)) clipboard.setText(AnnotatedString(message))
        onSent()
    }
}

/** The reminder for [payment] in the app language, with my pay methods when I set them. */
@Composable
internal fun reminderMessage(
    payment: SuggestedPayment,
    detail: GroupDetail,
    currency: Currency,
    format: MoneyFormat,
): String {
    val wording = reminderWording()
    return PaymentReminder.message(
        wording = wording,
        debtorName = detail.members.nameOf(payment.fromMemberId),
        groupName = detail.group.name,
        formattedAmount = format.format(payment.amountMinor, currency),
        amountMinor = payment.amountMinor,
        currency = currency,
        myMethods = detail.members.firstOrNull { it.id == detail.myMemberId }?.payMethods,
    )
}

/** The string templates, read now so the composition logic stays plain (and unit tested). */
@Composable
private fun reminderWording(): ReminderWording {
    val intro = stringResource(Res.string.remind_intro, ARG1, ARG2, ARG3)
    val payBy = stringResource(Res.string.remind_pay_by, ARG1)
    val bizum = stringResource(Res.string.remind_bizum, ARG1)
    val paypal = stringResource(Res.string.remind_paypal, ARG1)
    val revolut = stringResource(Res.string.remind_revolut, ARG1)
    val listMore = stringResource(Res.string.remind_list_more, ARG1, ARG2)
    val listLast = stringResource(Res.string.remind_list_last, ARG1, ARG2)
    val thanks = stringResource(Res.string.remind_thanks)
    return ReminderWording(
        intro = { name, group, amount -> intro.fill(name, group, amount) },
        payBy = { payBy.fill(it) },
        bizum = { bizum.fill(it) },
        paypal = { paypal.fill(it) },
        revolut = { revolut.fill(it) },
        listMore = { a, b -> listMore.fill(a, b) },
        listLast = { a, b -> listLast.fill(a, b) },
        thanks = thanks,
    )
}

// Placeholders that cannot appear in names or links, replaced in one pass so values never clash.
private const val ARG1 = "\u0001"
private const val ARG2 = "\u0002"
private const val ARG3 = "\u0003"

private fun String.fill(vararg values: String): String = buildString {
    this@fill.forEach { c ->
        when (c) {
            ARG1[0] -> append(values.getOrElse(0) { "" })
            ARG2[0] -> append(values.getOrElse(1) { "" })
            ARG3[0] -> append(values.getOrElse(2) { "" })
            else -> append(c)
        }
    }
}
