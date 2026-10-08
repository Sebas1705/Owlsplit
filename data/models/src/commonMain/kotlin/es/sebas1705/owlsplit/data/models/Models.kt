package es.sebas1705.owlsplit.data.models

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Local synchronisation state of a document. See docs/SYNC.md. */
enum class SyncState { SYNCED, PENDING, CONFLICT }

/**
 * Sync metadata shared by every editable document.
 * [version] is the local version (serverVersion + 1 while a write is pending);
 * [updatedAt] is the server time of the last synced write, null until synced.
 */
@Serializable
data class SyncMeta(
    val version: Long,
    val serverVersion: Long,
    val updatedAt: Long?,
    val updatedBy: String,
    val deleted: Boolean,
    val syncState: SyncState,
)

@Serializable
data class Group(
    val id: String,
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
    val meta: SyncMeta,
)

@Serializable
data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val avatar: String?,
    val defaultWeight: Long,
    val userUid: String?,
    val active: Boolean,
    val sortKey: Long,
    val meta: SyncMeta,
    /** How the person behind this member wants to be paid; only they set it (from their profile). */
    val payMethods: PayMethods? = null,
)

enum class ExpenseCategory {
    FOOD, GROCERIES, TRANSPORT, ACCOMMODATION, LEISURE, SHOPPING, HOME, UTILITIES, HEALTH, GIFTS, OTHER,
}

enum class SplitType { EQUAL, WEIGHTS, EXACT, PERCENT }

@Serializable
data class Expense(
    val id: String,
    val groupId: String,
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
    val meta: SyncMeta,
)

@Serializable
data class Transfer(
    val id: String,
    val groupId: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amountMinor: Long,
    val currency: String,
    val fxRateScaled: Long,
    val date: LocalDate,
    val note: String?,
    val createdBy: String,
    val createdAt: Long,
    val meta: SyncMeta,
)

enum class EntityType { GROUP, MEMBER, EXPENSE, TRANSFER }

enum class ChangeAction { CREATED, UPDATED, DELETED, RESTORED, JOINED, CONFLICT }

@Serializable
data class FieldChange(val field: String, val before: String?, val after: String?)

@Serializable
data class ChangeLogEntry(
    val id: String,
    val groupId: String,
    val entityType: EntityType,
    val entityId: String,
    val action: ChangeAction,
    val authorUid: String,
    val diff: List<FieldChange>,
    val createdAt: Long,
)

/** What an outbox entry uploads. CHANGE_LOG entries are create-only. */
enum class OutboxType { GROUP, MEMBER, EXPENSE, TRANSFER, CHANGE_LOG }

/** A local edit that lost against someone else's: [entityType] and [entityId] say what it was about. */
data class Conflict(
    val id: Long,
    val groupId: String,
    val entityType: EntityType,
    val entityId: String,
    val createdAt: Long,
)
