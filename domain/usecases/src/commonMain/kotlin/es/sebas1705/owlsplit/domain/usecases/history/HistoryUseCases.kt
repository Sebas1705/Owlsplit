package es.sebas1705.owlsplit.domain.usecases.history

import es.sebas1705.owlsplit.domain.usecases.groups.Rejoin
import es.sebas1705.owlsplit.domain.usecases.groups.MemberAccounts
import es.sebas1705.owlsplit.domain.usecases.groups.hasMember
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Conflict
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.ChangeLogRepository
import es.sebas1705.owlsplit.data.repository.ConflictRepository
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

/** Search and filters of a group's expense list (decision 1 of P9c). */
data class ExpenseFilter(
    val query: String = "",
    /** Expenses this member paid or takes part in. */
    val memberId: String? = null,
    val category: ExpenseCategory? = null,
) {
    val active: Boolean get() = query.isNotBlank() || memberId != null || category != null

    fun apply(expenses: List<Expense>): List<Expense> {
        val needle = query.trim().folded()
        return expenses.filter { expense ->
            (needle.isEmpty() || needle in expense.title.folded()) &&
                (memberId == null || memberId in expense.payers || memberId in expense.splitValues) &&
                (category == null || expense.category == category)
        }
    }
}

private val ACCENTS = "áéíóúüñàèòç".zip("aeiouunaeoc").toMap()

/** Lower case without accents, so "cafe" finds "Café". */
private fun String.folded(): String = lowercase().map { ACCENTS[it] ?: it }.joinToString("")

/** One line of history, with what the screen needs to say it and act on it. */
data class HistoryItem(
    val entry: ChangeLogEntry,
    val groupName: String,
    /** Name of the member who did it, also after they left; null only when nothing says who. */
    val author: String?,
    val byMe: Boolean,
    /** What it was about: expense title, member or group name, "Ana → Luis" for payments. */
    val subject: String?,
    /** Currency of the amounts in [entry]'s diff (the expense's, or the group's). */
    val currency: String,
    val canRestore: Boolean,
    /** Set on a conflict entry while that conflict is unresolved. */
    val conflictId: Long?,
    /** A member that stopped being someone's account: they left the group or deleted it. */
    val leftGroup: Boolean = false,
    /** A join to a member that had an account before: back with the same one, or with another. */
    val rejoin: Rejoin? = null,
)

/** A group's documents, including deleted expenses, to describe its history. */
internal data class GroupIndex(
    val group: Group?,
    val members: List<Member>,
    val expenses: List<Expense>,
    val transfers: List<Transfer>,
    /** Every account that was a member of the group, uid → member id, from its history. */
    val accounts: Map<String, String> = emptyMap(),
    /** History entry id → how a member that had an account was joined again. */
    val rejoins: Map<String, Rejoin> = emptyMap(),
) {
    /** The member [uid] is or was. */
    fun memberOf(uid: String): Member? =
        members.firstOrNull { it.userUid == uid } ?: accounts[uid]?.let { id -> members.firstOrNull { it.id == id } }
}

private const val USER_UID = "userUid"

/** uid → member id: joins, and the account links recorded in member changes. */
private fun accountsOf(entries: List<ChangeLogEntry>): Map<String, String> = buildMap {
    entries.filter { it.entityType == EntityType.MEMBER }.sortedBy { it.createdAt }.forEach { entry ->
        if (entry.action == ChangeAction.JOINED) put(entry.authorUid, entry.entityId)
        entry.diff.firstOrNull { it.field == USER_UID }?.let { change ->
            change.before?.let { put(it, entry.entityId) }
            change.after?.let { put(it, entry.entityId) }
        }
    }
}

private const val RESTORE_DAYS = 30L
private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

/** Turns history entries into [HistoryItem]s; shared by a group's history and Actividad. */
@Suppress("LongParameterList") // The group's five sources, one per document type.
class HistoryDescriber(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val expenses: ExpenseRepository,
    private val transfers: TransferRepository,
    private val changeLog: ChangeLogRepository,
    private val clock: Clock = Clock.System,
) {
    internal fun index(groupId: String): Flow<GroupIndex> = combine(
        groups.observeGroup(groupId),
        members.observeMembers(groupId),
        expenses.observeExpenses(groupId, includeDeleted = true),
        transfers.observeTransfers(groupId, includeDeleted = true),
        changeLog.observe(groupId),
    ) { group, memberList, expenseList, transferList, entries ->
        GroupIndex(group, memberList, expenseList, transferList, accountsOf(entries), MemberAccounts.rejoins(entries))
    }

    internal fun describe(entry: ChangeLogEntry, index: GroupIndex, uid: String?, conflicts: List<Conflict>): HistoryItem {
        val name = { id: String -> index.members.firstOrNull { it.id == id }?.name }
        val expense = index.expenses.firstOrNull { entry.entityType == EntityType.EXPENSE && it.id == entry.entityId }
        val subject = when (entry.entityType) {
            EntityType.EXPENSE -> expense?.title
            EntityType.MEMBER -> name(entry.entityId)
            EntityType.GROUP -> index.group?.name
            EntityType.TRANSFER -> index.transfers.firstOrNull { it.id == entry.entityId }
                ?.let { "${name(it.fromMemberId).orEmpty()} → ${name(it.toMemberId).orEmpty()}" }
        }
        val recent = clock.now().toEpochMilliseconds() - entry.createdAt < RESTORE_DAYS * DAY_MILLIS
        return HistoryItem(
            entry = entry,
            groupName = index.group?.name.orEmpty(),
            author = index.memberOf(entry.authorUid)?.name,
            byMe = entry.authorUid == uid,
            subject = subject,
            currency = expense?.currency ?: index.group?.baseCurrency ?: "EUR",
            canRestore = entry.action == ChangeAction.DELETED && recent && when (entry.entityType) {
                EntityType.EXPENSE -> expense?.meta?.deleted == true
                EntityType.TRANSFER -> index.transfers.firstOrNull { it.id == entry.entityId }?.meta?.deleted == true
                else -> false
            },
            conflictId = if (entry.action == ChangeAction.CONFLICT) {
                conflicts.lastOrNull { it.groupId == entry.groupId && it.entityId == entry.entityId }?.id
            } else {
                null
            },
            leftGroup = leftGroup(entry, index),
            rejoin = index.rejoins[entry.id],
        )
    }

    /**
     * A member edit that released the account. Entries written before the link was recorded
     * show it as an edit with no changes that a member made to themselves.
     */
    private fun leftGroup(entry: ChangeLogEntry, index: GroupIndex): Boolean {
        if (entry.entityType != EntityType.MEMBER || entry.action != ChangeAction.UPDATED) return false
        val link = entry.diff.firstOrNull { it.field == USER_UID }
        return if (link != null) {
            link.before != null && link.after == null
        } else {
            entry.diff.isEmpty() && index.memberOf(entry.authorUid)?.id == entry.entityId
        }
    }
}

/** History of one group, newest first. */
class ObserveHistory(
    private val describer: HistoryDescriber,
    private val changeLog: ChangeLogRepository,
    private val conflicts: ConflictRepository,
    private val accounts: AccountService,
) {
    operator fun invoke(groupId: String): Flow<List<HistoryItem>> = combine(
        changeLog.observe(groupId),
        describer.index(groupId),
        conflicts.observe(),
        accounts.session,
    ) { entries, index, open, session ->
        entries.map { describer.describe(it, index, session?.uid, open) }
    }
}

data class Activity(
    /** Conflicts still to resolve, shown first. */
    val pending: List<HistoryItem>,
    val items: List<HistoryItem>,
)

/** Actividad: the latest entries of every group (decision 6 of P9c). */
class ObserveActivity(
    private val describer: HistoryDescriber,
    private val changeLog: ChangeLogRepository,
    private val conflicts: ConflictRepository,
    private val accounts: AccountService,
    private val groups: GroupRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Activity> = combine(
        changeLog.observeRecent(LIMIT),
        groups.observeGroups(),
        accounts.session,
    ) { all, groupList, session ->
        // A group I left stays on the device, but its activity is no longer mine.
        val mine = groupList.filter { it.hasMember(session?.uid) }.mapTo(HashSet()) { it.id }
        all.filter { it.groupId in mine }
    }.flatMapLatest { entries ->
        val groupIds = entries.map { it.groupId }.distinct()
        if (groupIds.isEmpty()) return@flatMapLatest flowOf(Activity(emptyList(), emptyList()))
        val indexes = combine(groupIds.map { id -> describer.index(id) }) { list -> groupIds.zip(list).toMap() }
        combine(indexes, conflicts.observe(), accounts.session) { byGroup, open, session ->
            val items = entries.map { describer.describe(it, byGroup.getValue(it.groupId), session?.uid, open) }
            Activity(items.filter { it.conflictId != null }, items)
        }
    }

    private companion object {
        const val LIMIT = 200L
    }
}

/** Undoes the deletion of an expense or a payment. */
class RestoreExpense(private val expenses: ExpenseRepository, private val transfers: TransferRepository? = null) {
    suspend operator fun invoke(expenseId: String) = expenses.restore(expenseId)

    suspend fun restore(type: EntityType, id: String) = when (type) {
        EntityType.EXPENSE -> expenses.restore(id)
        EntityType.TRANSFER -> transfers?.restore(id)
        else -> Unit
    }
}

class ResolveConflict(private val conflicts: ConflictRepository) {
    /** Writes my version again on top of the winning one. False if it no longer applies. */
    suspend fun reapply(conflictId: Long): Boolean = conflicts.reapply(conflictId)

    suspend fun dismiss(conflictId: Long) = conflicts.dismiss(conflictId)
}
