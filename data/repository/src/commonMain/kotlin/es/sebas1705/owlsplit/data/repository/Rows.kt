package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.local.db.Change_log
import es.sebas1705.owlsplit.data.local.db.Expense_entity
import es.sebas1705.owlsplit.data.local.db.Group_entity
import es.sebas1705.owlsplit.data.local.db.Member_entity
import es.sebas1705.owlsplit.data.local.db.Transfer_entity
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal val json = Json { ignoreUnknownKeys = true }
private val stringList = ListSerializer(String.serializer())
private val fieldChanges = ListSerializer(FieldChange.serializer())

// SQLite has no boolean type: flags are stored as 0/1.
private fun Long.asFlag(): Boolean = this != 0L
private fun Boolean.asInt(): Long = if (this) 1L else 0L

private fun meta(version: Long, serverVersion: Long, updatedAt: Long?, updatedBy: String, deleted: Long, state: String) =
    SyncMeta(version, serverVersion, updatedAt, updatedBy, deleted.asFlag(), SyncState.valueOf(state))

internal fun Group_entity.toModel() = Group(
    id = id, name = name, icon = icon, accent = accent, baseCurrency = base_currency,
    startDate = start_date?.let(LocalDate::parse), endDate = end_date?.let(LocalDate::parse),
    inviteCode = invite_code, memberUids = json.decodeFromString(stringList, member_uids),
    archivedBy = json.decodeFromString(stringList, archived_by), createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Group.toRow() = Group_entity(
    id = id, name = name, icon = icon, accent = accent, base_currency = baseCurrency,
    start_date = startDate?.toString(), end_date = endDate?.toString(), invite_code = inviteCode,
    member_uids = json.encodeToString(
        stringList,
        memberUids
    ),
    archived_by = json.encodeToString(stringList, archivedBy),
    created_by = createdBy, created_at = createdAt, version = meta.version, server_version = meta.serverVersion,
    updated_at = meta.updatedAt, updated_by = meta.updatedBy, deleted = meta.deleted.asInt(),
    sync_state = meta.syncState.name,
)

internal fun Member_entity.toModel() = Member(
    id = id, groupId = group_id, name = name, avatar = avatar, defaultWeight = default_weight, userUid = user_uid,
    active = active.asFlag(), sortKey = sort_key,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Member.toRow() = Member_entity(
    id = id, group_id = groupId, name = name, avatar = avatar, default_weight = defaultWeight, user_uid = userUid,
    active = active.asInt(), sort_key = sortKey, version = meta.version, server_version = meta.serverVersion,
    updated_at = meta.updatedAt, updated_by = meta.updatedBy, deleted = meta.deleted.asInt(),
    sync_state = meta.syncState.name,
)

internal fun Expense_entity.toModel(payers: Map<String, Long>, splitValues: Map<String, Long>) = Expense(
    id = id, groupId = group_id, title = title, category = ExpenseCategory.valueOf(category), note = note,
    date = LocalDate.parse(date), currency = currency, amountMinor = amount_minor, fxRateScaled = fx_rate_scaled,
    payers = payers, splitType = SplitType.valueOf(split_type), splitValues = splitValues,
    receiptUrl = receipt_url, recurrenceId = recurrence_id, createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Expense.toRow() = Expense_entity(
    id = id, group_id = groupId, title = title, category = category.name, note = note, date = date.toString(),
    currency = currency, amount_minor = amountMinor, fx_rate_scaled = fxRateScaled, split_type = splitType.name,
    receipt_url = receiptUrl, recurrence_id = recurrenceId, created_by = createdBy, created_at = createdAt,
    version = meta.version, server_version = meta.serverVersion, updated_at = meta.updatedAt,
    updated_by = meta.updatedBy, deleted = meta.deleted.asInt(), sync_state = meta.syncState.name,
)

internal fun Transfer_entity.toModel() = Transfer(
    id = id, groupId = group_id, fromMemberId = from_member_id, toMemberId = to_member_id,
    amountMinor = amount_minor, currency = currency, fxRateScaled = fx_rate_scaled, date = LocalDate.parse(date),
    note = note, createdBy = created_by, createdAt = created_at,
    meta = meta(version, server_version, updated_at, updated_by, deleted, sync_state),
)

internal fun Transfer.toRow() = Transfer_entity(
    id = id, group_id = groupId, from_member_id = fromMemberId, to_member_id = toMemberId,
    amount_minor = amountMinor, currency = currency, fx_rate_scaled = fxRateScaled, date = date.toString(),
    note = note, created_by = createdBy, created_at = createdAt, version = meta.version,
    server_version = meta.serverVersion, updated_at = meta.updatedAt, updated_by = meta.updatedBy,
    deleted = meta.deleted.asInt(), sync_state = meta.syncState.name,
)

internal fun Change_log.toModel() = ChangeLogEntry(
    id = id,
    groupId = group_id,
    entityType = EntityType.valueOf(entity_type),
    entityId = entity_id,
    action = ChangeAction.valueOf(action),
    authorUid = author_uid,
    diff = json.decodeFromString(fieldChanges, diff),
    createdAt = created_at,
)

internal fun ChangeLogEntry.toRow(state: SyncState) = Change_log(
    id = id, group_id = groupId, entity_type = entityType.name, entity_id = entityId, action = action.name,
    author_uid = authorUid, diff = json.encodeToString(fieldChanges, diff), created_at = createdAt,
    sync_state = state.name,
)
