package es.sebas1705.owlsplit.core.testing.ledger

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.repository.ChangeLogRepository
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The four group repositories in memory, without sync or history: enough for use cases and
 * ViewModels. [uid] is the signed-in user; ids are "g1", "m1", "e1"… in creation order.
 */
class InMemoryLedger(var uid: String = "uid-ana") {
    private var next = 0
    private var clock = 0L
    private fun id(prefix: String) = "$prefix${++next}"
    private fun now() = ++clock
    private fun meta() = SyncMeta(1, 0, null, uid, false, SyncState.PENDING)

    val groupList = MutableStateFlow<List<Group>>(emptyList())
    val memberList = MutableStateFlow<List<Member>>(emptyList())
    val expenseList = MutableStateFlow<List<Expense>>(emptyList())
    val transferList = MutableStateFlow<List<Transfer>>(emptyList())

    /** History entries, newest first; tests add them directly (this fake writes none). */
    val log = MutableStateFlow<List<ChangeLogEntry>>(emptyList())

    val groups: GroupRepository = object : GroupRepository {
        override fun observeGroups(): Flow<List<Group>> = groupList.map { list -> list.filter { !it.meta.deleted } }
        override fun observeGroup(id: String): Flow<Group?> = groupList.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun create(input: GroupInput, creatorName: String): String {
            val group = Group(
                id("g"), input.name, input.icon, input.accent, input.baseCurrency, input.startDate, input.endDate,
                "CODE", listOf(uid), emptyList(), uid, now(), meta(),
            )
            groupList.update { it + group }
            memberList.update { it + Member(id("m"), group.id, creatorName, null, 1, uid, true, 0, meta()) }
            return group.id
        }

        override suspend fun update(id: String, input: GroupInput) = editGroup(id) {
            it.copy(
                name = input.name,
                icon = input.icon,
                accent = input.accent,
                baseCurrency = input.baseCurrency,
                startDate = input.startDate,
                endDate = input.endDate,
            )
        }

        override suspend fun setArchived(id: String, archived: Boolean) = editGroup(id) {
            it.copy(archivedBy = if (archived) it.archivedBy + uid else it.archivedBy - uid)
        }

        override suspend fun leave(id: String) {
            val mine = { m: Member -> m.groupId == id && m.userUid == uid }
            memberList.update { list -> list.map { if (mine(it)) it.copy(userUid = null) else it } }
            editGroup(id) { it.copy(memberUids = it.memberUids - uid) }
        }
    }

    val members: MemberRepository = object : MemberRepository {
        override fun observeMembers(groupId: String): Flow<List<Member>> =
            memberList.map { list -> list.filter { it.groupId == groupId && !it.meta.deleted }.sortedBy { it.sortKey } }

        override suspend fun add(groupId: String, input: MemberInput): String {
            val sortKey = memberList.value.count { it.groupId == groupId }.toLong()
            val weight = input.defaultWeight
            val member = Member(id("m"), groupId, input.name, input.avatar, weight, null, true, sortKey, meta())
            memberList.update { it + member }
            return member.id
        }

        override suspend fun update(id: String, input: MemberInput) =
            editMember(id) { it.copy(name = input.name, avatar = input.avatar, defaultWeight = input.defaultWeight) }

        override suspend fun setActive(id: String, active: Boolean) = editMember(id) { it.copy(active = active) }

        override suspend fun deleteIfUnused(id: String): Boolean {
            val used = expenseList.value.any { id in it.payers || id in it.splitValues } ||
                transferList.value.any { it.fromMemberId == id || it.toMemberId == id }
            if (!used) editMember(id) { it.copy(meta = it.meta.copy(deleted = true)) }
            return !used
        }
    }

    val expenses: ExpenseRepository = object : ExpenseRepository {
        override fun observeExpenses(groupId: String, includeDeleted: Boolean): Flow<List<Expense>> =
            expenseList.map { list -> list.filter { it.groupId == groupId && (includeDeleted || !it.meta.deleted) } }

        override suspend fun get(id: String): Expense? = expenseList.value.firstOrNull { it.id == id }

        override suspend fun create(groupId: String, input: ExpenseInput): String {
            val expense = Expense(
                id("e"), groupId, input.title, input.category, input.note, input.date, input.currency,
                input.amountMinor, input.fxRateScaled, input.payers, input.splitType, input.splitValues,
                null, null, uid, now(), meta(),
            )
            expenseList.update { it + expense }
            return expense.id
        }

        override suspend fun update(id: String, input: ExpenseInput) = editExpense(id) {
            it.copy(
                title = input.title, category = input.category, note = input.note, date = input.date,
                currency = input.currency, amountMinor = input.amountMinor, fxRateScaled = input.fxRateScaled,
                payers = input.payers, splitType = input.splitType, splitValues = input.splitValues,
            )
        }

        override suspend fun delete(id: String) = editExpense(id) { it.copy(meta = it.meta.copy(deleted = true)) }
        override suspend fun restore(id: String) = editExpense(id) { it.copy(meta = it.meta.copy(deleted = false)) }
    }

    val transfers: TransferRepository = object : TransferRepository {
        override fun observeTransfers(groupId: String): Flow<List<Transfer>> =
            transferList.map { list -> list.filter { it.groupId == groupId && !it.meta.deleted } }

        override suspend fun create(groupId: String, input: TransferInput): String {
            val transfer = Transfer(
                id("t"), groupId, input.fromMemberId, input.toMemberId, input.amountMinor, input.currency,
                input.fxRateScaled, input.date, input.note, uid, now(), meta(),
            )
            transferList.update { it + transfer }
            return transfer.id
        }

        override suspend fun delete(id: String) = editTransfer(id) { it.copy(meta = it.meta.copy(deleted = true)) }
        override suspend fun restore(id: String) = editTransfer(id) { it.copy(meta = it.meta.copy(deleted = false)) }
    }

    val changeLog: ChangeLogRepository = object : ChangeLogRepository {
        override fun observe(
            groupId: String
        ): Flow<List<ChangeLogEntry>> = log.map { list -> list.filter { it.groupId == groupId } }
        override fun observeRecent(limit: Long): Flow<List<ChangeLogEntry>> = log.map { it.take(limit.toInt()) }
    }

    private fun editGroup(id: String, change: (Group) -> Group) =
        groupList.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun editMember(id: String, change: (Member) -> Member) =
        memberList.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun editExpense(id: String, change: (Expense) -> Expense) =
        expenseList.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun editTransfer(id: String, change: (Transfer) -> Transfer) =
        transferList.update { list -> list.map { if (it.id == id) change(it) else it } }
}
