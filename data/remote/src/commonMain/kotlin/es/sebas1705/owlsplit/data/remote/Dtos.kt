package es.sebas1705.owlsplit.data.remote

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.toMilliseconds
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.FieldChange
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.sync.RemoteDoc
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

// Firestore documents, with exactly the keys firebase/firestore.rules accepts. Ids come from the
// document path. `updatedAt` is written as the server time and read back as a Timestamp (null
// while a local write is pending). Nulls are written explicitly: the rules read every key.

@Serializable
internal data class GroupDto(
    val name: String,
    val icon: String,
    val accent: String,
    val baseCurrency: String,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val inviteCode: String,
    val memberUids: List<String>,
    val archivedBy: List<String>,
    val createdBy: String,
    val createdAt: Long,
    val version: Long,
    val updatedAt: BaseTimestamp?,
    val updatedBy: String,
    val deleted: Boolean,
)

@Serializable
internal data class MemberDto(
    val name: String,
    val avatar: String?,
    val defaultWeight: Long,
    val userUid: String?,
    val active: Boolean,
    val sortKey: Long,
    val version: Long,
    val updatedAt: BaseTimestamp?,
    val updatedBy: String,
    val deleted: Boolean,
    /** Missing in documents written before P12a. */
    val payMethods: PayMethods? = null,
)

@Serializable
internal data class ExpenseDto(
    val title: String,
    val category: ExpenseCategory,
    val note: String?,
    val date: LocalDate,
    val currency: String,
    val amountMinor: Long,
    val fxRateScaled: Long,
    val payers: Map<String, Long>,
    val splitType: SplitType,
    val splitValues: Map<String, Long>,
    val receiptUrl: String?,
    val recurrenceId: String?,
    val createdBy: String,
    val createdAt: Long,
    val version: Long,
    val updatedAt: BaseTimestamp?,
    val updatedBy: String,
    val deleted: Boolean,
)

@Serializable
internal data class TransferDto(
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateScaled: Long,
    val date: LocalDate,
    val note: String?,
    val createdBy: String,
    val createdAt: Long,
    val version: Long,
    val updatedAt: BaseTimestamp?,
    val updatedBy: String,
    val deleted: Boolean,
)

@Serializable
internal data class ChangeLogDto(
    val entityType: EntityType,
    val entityId: String,
    val action: ChangeAction,
    val authorUid: String,
    val diff: List<FieldChange>,
    val createdAt: Long,
    val updatedAt: BaseTimestamp?,
)

/** Sync metadata of a write by [uid]: version chained by the engine, time set by the server. */
internal class Stamp(val version: Long, val uid: String)

internal fun Group.toDto(s: Stamp) = GroupDto(
    name, icon, accent, baseCurrency, startDate, endDate, inviteCode, memberUids, archivedBy,
    createdBy, createdAt, s.version, Timestamp.ServerTimestamp, s.uid, meta.deleted,
)

internal fun Member.toDto(s: Stamp) = MemberDto(
    name, avatar, defaultWeight, userUid, active, sortKey, s.version, Timestamp.ServerTimestamp, s.uid, meta.deleted,
    payMethods,
)

internal fun Expense.toDto(s: Stamp) = ExpenseDto(
    title, category, note, date, currency, amountMinor, fxRateScaled, payers, splitType, splitValues,
    receiptUrl, recurrenceId, createdBy, createdAt, s.version, Timestamp.ServerTimestamp, s.uid, meta.deleted,
)

internal fun Transfer.toDto(s: Stamp) = TransferDto(
    fromMemberId, toMemberId, amountMinor, currency, fxRateScaled, date, note, createdBy, createdAt,
    s.version, Timestamp.ServerTimestamp, s.uid, meta.deleted,
)

internal fun ChangeLogEntry.toDto() =
    ChangeLogDto(entityType, entityId, action, authorUid, diff, createdAt, Timestamp.ServerTimestamp)

/** Server time in milliseconds; null while the write is not confirmed. */
private fun BaseTimestamp?.millis(): Long? = (this as? Timestamp)?.toMilliseconds()?.toLong()

private fun meta(version: Long, updatedAt: BaseTimestamp?, updatedBy: String, deleted: Boolean): SyncMeta? =
    updatedAt.millis()?.let { SyncMeta(version, version, it, updatedBy, deleted, SyncState.SYNCED) }

internal fun GroupDto.toModel(id: String): Group? = meta(version, updatedAt, updatedBy, deleted)?.let {
    Group(
        id, name, icon, accent, baseCurrency, startDate, endDate, inviteCode, memberUids, archivedBy,
        createdBy, createdAt, it,
    )
}

internal fun MemberDto.toModel(groupId: String, id: String): Member? =
    meta(version, updatedAt, updatedBy, deleted)?.let {
        Member(id, groupId, name, avatar, defaultWeight, userUid, active, sortKey, it, payMethods)
    }

internal fun ExpenseDto.toModel(groupId: String, id: String): Expense? =
    meta(version, updatedAt, updatedBy, deleted)?.let {
        Expense(
            id, groupId, title, category, note, date, currency, amountMinor, fxRateScaled, payers, splitType,
            splitValues, receiptUrl, recurrenceId, createdBy, createdAt, it,
        )
    }

internal fun TransferDto.toModel(groupId: String, id: String): Transfer? =
    meta(version, updatedAt, updatedBy, deleted)?.let {
        Transfer(
            id, groupId, fromMemberId, toMemberId, amountMinor, currency, fxRateScaled, date, note,
            createdBy, createdAt, it,
        )
    }

internal fun ChangeLogDto.toRemote(groupId: String, id: String): RemoteDoc.ChangeLogDoc? = updatedAt.millis()?.let {
    RemoteDoc.ChangeLogDoc(ChangeLogEntry(id, groupId, entityType, entityId, action, authorUid, diff, createdAt), it)
}
