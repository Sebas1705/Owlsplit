package es.sebas1705.owlsplit.feature.history

import androidx.compose.runtime.Composable
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.money.FxRate
import es.sebas1705.owlsplit.core.ui.format.MoneyFormat
import es.sebas1705.owlsplit.core.ui.format.dayLabel
import es.sebas1705.owlsplit.core.ui.theme.categoryName
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.domain.usecases.history.HistoryItem
import es.sebas1705.owlsplit.feature.history.resources.Res
import es.sebas1705.owlsplit.feature.history.resources.change
import es.sebas1705.owlsplit.feature.history.resources.change_plain
import es.sebas1705.owlsplit.feature.history.resources.conflict
import es.sebas1705.owlsplit.feature.history.resources.created
import es.sebas1705.owlsplit.feature.history.resources.created_group
import es.sebas1705.owlsplit.feature.history.resources.created_group_me
import es.sebas1705.owlsplit.feature.history.resources.created_me
import es.sebas1705.owlsplit.feature.history.resources.created_member
import es.sebas1705.owlsplit.feature.history.resources.created_member_me
import es.sebas1705.owlsplit.feature.history.resources.created_payment
import es.sebas1705.owlsplit.feature.history.resources.created_payment_me
import es.sebas1705.owlsplit.feature.history.resources.deleted
import es.sebas1705.owlsplit.feature.history.resources.deleted_me
import es.sebas1705.owlsplit.feature.history.resources.field_active
import es.sebas1705.owlsplit.feature.history.resources.field_amount
import es.sebas1705.owlsplit.feature.history.resources.field_category
import es.sebas1705.owlsplit.feature.history.resources.field_colour
import es.sebas1705.owlsplit.feature.history.resources.field_currency
import es.sebas1705.owlsplit.feature.history.resources.field_date
import es.sebas1705.owlsplit.feature.history.resources.field_end
import es.sebas1705.owlsplit.feature.history.resources.field_icon
import es.sebas1705.owlsplit.feature.history.resources.field_name
import es.sebas1705.owlsplit.feature.history.resources.field_note
import es.sebas1705.owlsplit.feature.history.resources.field_payers
import es.sebas1705.owlsplit.feature.history.resources.field_rate
import es.sebas1705.owlsplit.feature.history.resources.field_split
import es.sebas1705.owlsplit.feature.history.resources.field_start
import es.sebas1705.owlsplit.feature.history.resources.field_title
import es.sebas1705.owlsplit.feature.history.resources.field_weight
import es.sebas1705.owlsplit.feature.history.resources.joined
import es.sebas1705.owlsplit.feature.history.resources.joined_me
import es.sebas1705.owlsplit.feature.history.resources.left
import es.sebas1705.owlsplit.feature.history.resources.left_me
import es.sebas1705.owlsplit.feature.history.resources.no
import es.sebas1705.owlsplit.feature.history.resources.none
import es.sebas1705.owlsplit.feature.history.resources.quoted
import es.sebas1705.owlsplit.feature.history.resources.restored
import es.sebas1705.owlsplit.feature.history.resources.restored_me
import es.sebas1705.owlsplit.feature.history.resources.someone
import es.sebas1705.owlsplit.feature.history.resources.unknown_subject
import es.sebas1705.owlsplit.feature.history.resources.updated
import es.sebas1705.owlsplit.feature.history.resources.updated_me
import es.sebas1705.owlsplit.feature.history.resources.yes
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** "Ana añadió «Cena»", "Cambiaste «Taxi»", "Conflicto en «Cena»…" (decision 2 of P9c). */
@Composable
internal fun headline(item: HistoryItem): String {
    val entry = item.entry
    val subject = item.subject?.let {
        // People and payments read better without quotes.
        if (entry.entityType == EntityType.TRANSFER || entry.entityType == EntityType.MEMBER) it else stringResource(Res.string.quoted, it)
    } ?: stringResource(Res.string.unknown_subject)
    val who = item.author ?: stringResource(Res.string.someone)
    // Leaving (or deleting the account) is said of the member, not as an edit of them.
    if (item.leftGroup) {
        return if (item.byMe) {
            stringResource(Res.string.left_me)
        } else {
            stringResource(Res.string.left, item.subject ?: who)
        }
    }
    val (mine, theirs) = when (entry.action) {
        ChangeAction.CREATED -> when (entry.entityType) {
            EntityType.GROUP -> Res.string.created_group_me to Res.string.created_group
            EntityType.TRANSFER -> Res.string.created_payment_me to Res.string.created_payment
            EntityType.MEMBER -> Res.string.created_member_me to Res.string.created_member
            else -> Res.string.created_me to Res.string.created
        }
        ChangeAction.UPDATED -> Res.string.updated_me to Res.string.updated
        ChangeAction.DELETED -> Res.string.deleted_me to Res.string.deleted
        ChangeAction.RESTORED -> Res.string.restored_me to Res.string.restored
        ChangeAction.JOINED -> Res.string.joined_me to Res.string.joined
        ChangeAction.CONFLICT -> return stringResource(Res.string.conflict, subject)
    }
    return if (item.byMe) stringResource(mine, subject) else stringResource(theirs, who, subject)
}

/** The fields an edit changed, readable: "importe 12,40 € → 14,40 €; reparto cambiado". */
@Composable
internal fun changes(item: HistoryItem, format: MoneyFormat, today: LocalDate): String? {
    if (item.entry.action != ChangeAction.UPDATED || item.leftGroup) return null
    // The account link is how the history knows who someone was, not something to read.
    return item.entry.diff.filter { it.field != "userUid" }.map {
        change(
            it,
            item.currency,
            format,
            today
        )
    }.distinct().joinToString("; ").ifEmpty { null }
}

@Composable
private fun change(change: FieldChange, currency: String, format: MoneyFormat, today: LocalDate): String {
    val field = FIELDS[change.field]
    val label = field?.label?.let { stringResource(it) } ?: change.field
    if (field == null || field.kind == Kind.SUMMARY) return stringResource(Res.string.change_plain, label)
    val before = value(change.before, field.kind, currency, format, today)
    val after = value(change.after, field.kind, currency, format, today)
    return stringResource(Res.string.change, label, before, after)
}

@Composable
private fun value(raw: String?, kind: Kind, currency: String, format: MoneyFormat, today: LocalDate): String {
    if (raw == null) return stringResource(Res.string.none)
    return when (kind) {
        Kind.TEXT -> stringResource(Res.string.quoted, raw)
        Kind.MONEY -> raw.toLongOrNull()?.let { format.format(it, Currency.of(currency)) } ?: raw
        Kind.RATE -> raw.toLongOrNull()?.let { FxRate(it).toPlainString().replace('.', format.decimalSeparator) } ?: raw
        Kind.DATE -> runCatching { LocalDate.parse(raw) }.getOrNull()?.let { dayLabel(it, today) } ?: raw
        Kind.CATEGORY -> categoryName(raw)
        Kind.BOOLEAN -> stringResource(if (raw == "true") Res.string.yes else Res.string.no)
        Kind.PLAIN, Kind.SUMMARY -> raw
    }
}

private enum class Kind { TEXT, MONEY, RATE, DATE, CATEGORY, BOOLEAN, PLAIN, SUMMARY }

private class Field(val label: StringResource, val kind: Kind)

/** Field names written by `Diffs` in data:repository. Maps and looks are summarised by name only. */
private val FIELDS = mapOf(
    "title" to Field(Res.string.field_title, Kind.TEXT),
    "amountMinor" to Field(Res.string.field_amount, Kind.MONEY),
    "currency" to Field(Res.string.field_currency, Kind.PLAIN),
    "fxRateScaled" to Field(Res.string.field_rate, Kind.RATE),
    "date" to Field(Res.string.field_date, Kind.DATE),
    "category" to Field(Res.string.field_category, Kind.CATEGORY),
    "note" to Field(Res.string.field_note, Kind.TEXT),
    "payers" to Field(Res.string.field_payers, Kind.SUMMARY),
    "splitType" to Field(Res.string.field_split, Kind.SUMMARY),
    "splitValues" to Field(Res.string.field_split, Kind.SUMMARY),
    "name" to Field(Res.string.field_name, Kind.TEXT),
    "icon" to Field(Res.string.field_icon, Kind.SUMMARY),
    "accent" to Field(Res.string.field_colour, Kind.SUMMARY),
    "baseCurrency" to Field(Res.string.field_currency, Kind.PLAIN),
    "startDate" to Field(Res.string.field_start, Kind.DATE),
    "endDate" to Field(Res.string.field_end, Kind.DATE),
    "defaultWeight" to Field(Res.string.field_weight, Kind.PLAIN),
    "active" to Field(Res.string.field_active, Kind.BOOLEAN),
)
