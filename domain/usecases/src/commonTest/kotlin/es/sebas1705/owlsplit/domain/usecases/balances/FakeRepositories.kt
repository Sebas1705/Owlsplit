package es.sebas1705.owlsplit.domain.usecases.balances

import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer
import es.sebas1705.owlsplit.data.repository.ExpenseRepository
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Read-only fakes: tests push state into the flows directly. */
class FakeGroups(val group: MutableStateFlow<Group?>) : GroupRepository {
    override fun observeGroups(): Flow<List<Group>> = group.map { listOfNotNull(it) }
    override fun observeGroup(id: String): Flow<Group?> = group
    override suspend fun create(input: GroupInput, creatorName: String): String = error("unused")
    override suspend fun update(id: String, input: GroupInput) = error("unused")
    override suspend fun setArchived(id: String, archived: Boolean) = error("unused")
    override suspend fun leave(id: String) = error("unused")
}

class FakeMembers(val members: MutableStateFlow<List<Member>>) : MemberRepository {
    override fun observeMembers(groupId: String): Flow<List<Member>> = members
    override suspend fun add(groupId: String, input: MemberInput): String = error("unused")
    override suspend fun update(id: String, input: MemberInput) = error("unused")
    override suspend fun setActive(id: String, active: Boolean) = error("unused")
    override suspend fun deleteIfUnused(id: String): Boolean = error("unused")
}

class FakeExpenses(val expenses: MutableStateFlow<List<Expense>>) : ExpenseRepository {
    override fun observeExpenses(groupId: String): Flow<List<Expense>> = expenses
    override suspend fun get(id: String): Expense? = expenses.value.firstOrNull { it.id == id }
    override suspend fun create(groupId: String, input: ExpenseInput): String = error("unused")
    override suspend fun update(id: String, input: ExpenseInput) = error("unused")
    override suspend fun delete(id: String) = error("unused")
    override suspend fun restore(id: String) = error("unused")
}

class FakeTransfers(val transfers: MutableStateFlow<List<Transfer>>) : TransferRepository {
    override fun observeTransfers(groupId: String): Flow<List<Transfer>> = transfers
    override suspend fun create(groupId: String, input: TransferInput): String = error("unused")
    override suspend fun delete(id: String) = error("unused")
    override suspend fun restore(id: String) = error("unused")
}
