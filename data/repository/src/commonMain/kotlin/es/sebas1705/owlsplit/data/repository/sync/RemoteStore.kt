package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.flow.Flow

/** A document as the server has it: [version] and [updatedAt] (server time) live in its meta. */
sealed interface RemoteDoc {
    val groupId: String
    val id: String
    val version: Long
    val updatedAt: Long
    val type: OutboxType

    data class GroupDoc(val group: Group) : RemoteDoc {
        override val groupId: String get() = group.id
        override val id: String get() = group.id
        override val version: Long get() = group.meta.version
        override val updatedAt: Long get() = group.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.GROUP
    }

    data class MemberDoc(val member: Member) : RemoteDoc {
        override val groupId: String get() = member.groupId
        override val id: String get() = member.id
        override val version: Long get() = member.meta.version
        override val updatedAt: Long get() = member.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.MEMBER
    }

    data class ExpenseDoc(val expense: Expense) : RemoteDoc {
        override val groupId: String get() = expense.groupId
        override val id: String get() = expense.id
        override val version: Long get() = expense.meta.version
        override val updatedAt: Long get() = expense.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.EXPENSE
    }

    data class TransferDoc(val transfer: Transfer) : RemoteDoc {
        override val groupId: String get() = transfer.groupId
        override val id: String get() = transfer.id
        override val version: Long get() = transfer.meta.version
        override val updatedAt: Long get() = transfer.meta.updatedAt ?: 0L
        override val type: OutboxType get() = OutboxType.TRANSFER
    }

    /** History entries are create-only (version 1); [updatedAt] is the server time of that creation. */
    data class ChangeLogDoc(val entry: ChangeLogEntry, override val updatedAt: Long = entry.createdAt) : RemoteDoc {
        override val groupId: String get() = entry.groupId
        override val id: String get() = entry.id
        override val version: Long get() = 1L
        override val type: OutboxType get() = OutboxType.CHANGE_LOG
    }
}

/** Sub-collections of a group that are pulled incrementally by `updatedAt`. */
enum class RemoteCollection(val type: OutboxType) {
    MEMBERS(OutboxType.MEMBER),
    EXPENSES(OutboxType.EXPENSE),
    TRANSFERS(OutboxType.TRANSFER),
    CHANGELOG(OutboxType.CHANGE_LOG),
}

sealed interface PushResult {
    data object Ok : PushResult

    /** Network or server hiccup: retry later. */
    data object Transient : PushResult

    /** The server refused the write (Firestore PERMISSION_DENIED): version or access problem. */
    data object Rejected : PushResult
}

sealed interface FetchResult {
    data class Found(val doc: RemoteDoc) : FetchResult
    data object Missing : FetchResult
    data object NoAccess : FetchResult
    data object Transient : FetchResult

    /** The document exists but cannot be read as its type (malformed, or from a newer app): retrying will not help. */
    data object Unreadable : FetchResult
}

sealed interface PullResult {
    /** The matching documents; unreadable ones are left out. */
    data class Docs(val docs: List<RemoteDoc>) : PullResult
    data object NoAccess : PullResult
    data object Transient : PullResult
}

/** What the sync engine needs from the server. P6b implements it on Firestore (GitLive). */
interface RemoteStore {
    /**
     * Writes the document encoded in [payload] (JSON of the model, as queued by the repositories)
     * as [version], with the server time as `updatedAt` and the caller as `updatedBy`.
     */
    suspend fun push(type: OutboxType, groupId: String, entityId: String, version: Long, payload: String): PushResult

    suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult

    /** Every group whose `memberUids` contains [uid]; emits the full set on each change. */
    fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>>

    /**
     * Documents of [collection] with `updatedAt >= sinceMillis`: the first emission brings them all,
     * later ones at least those that changed. The engine only listens to the changelog this way.
     */
    fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>>

    /** One-off read, from the server, of the documents of [collection] with `updatedAt >= sinceMillis`. */
    suspend fun pull(groupId: String, collection: RemoteCollection, sinceMillis: Long): PullResult
}
