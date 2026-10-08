package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member

/** Raw before/after values; the UI formats them (amounts with their currency, dates, names). */
internal object Diffs {
    private fun MutableList<FieldChange>.field(name: String, before: Any?, after: Any?) {
        val b = before?.let(::render)
        val a = after?.let(::render)
        if (b != a) add(FieldChange(name, b, a))
    }

    private fun render(value: Any): String = when (value) {
        is Map<*, *> -> value.entries.sortedBy { it.key.toString() }.joinToString(",") { "${it.key}=${it.value}" }
        else -> value.toString()
    }

    fun expense(old: Expense?, new: Expense): List<FieldChange> = buildList {
        field("title", old?.title, new.title)
        field("amountMinor", old?.amountMinor, new.amountMinor)
        field("currency", old?.currency, new.currency)
        field("fxRateScaled", old?.fxRateScaled, new.fxRateScaled)
        field("date", old?.date, new.date)
        field("category", old?.category, new.category)
        field("note", old?.note, new.note)
        field("payers", old?.payers, new.payers)
        field("splitType", old?.splitType, new.splitType)
        field("splitValues", old?.splitValues, new.splitValues)
    }

    fun group(old: Group?, new: Group): List<FieldChange> = buildList {
        field("name", old?.name, new.name)
        field("icon", old?.icon, new.icon)
        field("accent", old?.accent, new.accent)
        field("baseCurrency", old?.baseCurrency, new.baseCurrency)
        field("startDate", old?.startDate, new.startDate)
        field("endDate", old?.endDate, new.endDate)
    }

    fun member(old: Member?, new: Member): List<FieldChange> = buildList {
        field("name", old?.name, new.name)
        field("defaultWeight", old?.defaultWeight, new.defaultWeight)
        field("active", old?.active, new.active)
        // Who the member is (the uid already in every entry's author): lets the history name the
        // author of old entries after they leave, and tell "left the group" from an edit.
        field(USER_UID, old?.userUid, new.userUid)
    }

    const val USER_UID = "userUid"
}
