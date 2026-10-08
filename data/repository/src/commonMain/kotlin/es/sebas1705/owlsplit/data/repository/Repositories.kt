package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Conflict
import es.sebas1705.owlsplit.data.models.Expense
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Transfer
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeGroups(): Flow<List<Group>>
    fun observeGroup(id: String): Flow<Group?>

    /** Creates the group and the creator's member (linked to the current user). Returns the group id. */
    suspend fun create(input: GroupInput, creatorName: String): String
    suspend fun update(id: String, input: GroupInput)
    suspend fun setArchived(id: String, archived: Boolean)

    /**
     * Releases my member (it stays as a ghost) and removes me from the group. Queued like any write;
     * afterwards the group can no longer be read or written by this user.
     */
    suspend fun leave(id: String)

    /** A new invite code: links with the old one stop working once it is withdrawn. */
    suspend fun regenerateInviteCode(id: String)
}

interface MemberRepository {
    fun observeMembers(groupId: String): Flow<List<Member>>
    suspend fun add(groupId: String, input: MemberInput): String
    suspend fun update(id: String, input: MemberInput)
    suspend fun setActive(id: String, active: Boolean)

    /** Deletes a member without movements; returns false (and changes nothing) otherwise. */
    suspend fun deleteIfUnused(id: String): Boolean

    /** Links a ghost member to me (after joining). */
    suspend fun claim(id: String)

    /** A new member for me, when none of the ghosts is me. */
    suspend fun addMe(groupId: String, name: String, avatar: String?): String
}

interface ExpenseRepository {
    /** Visible expenses; [includeDeleted] adds deleted ones (history, restore). */
    fun observeExpenses(groupId: String, includeDeleted: Boolean = false): Flow<List<Expense>>
    suspend fun get(id: String): Expense?
    suspend fun create(groupId: String, input: ExpenseInput): String
    suspend fun update(id: String, input: ExpenseInput)
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

interface TransferRepository {
    fun observeTransfers(groupId: String): Flow<List<Transfer>>
    suspend fun create(groupId: String, input: TransferInput): String
    suspend fun delete(id: String)
    suspend fun restore(id: String)
}

interface ChangeLogRepository {
    fun observe(groupId: String): Flow<List<ChangeLogEntry>>

    /** The latest [limit] entries of every group (Actividad). */
    fun observeRecent(limit: Long): Flow<List<ChangeLogEntry>>
}

/** Local edits that lost against someone else's (docs/SYNC.md): kept until reapplied or dismissed. */
interface ConflictRepository {
    fun observe(): Flow<List<Conflict>>

    /** Applies the lost version again on top of the current one (a normal edit). False if it cannot. */
    suspend fun reapply(conflictId: Long): Boolean

    suspend fun dismiss(conflictId: Long)
}
