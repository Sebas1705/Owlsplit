package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.MemberInput
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MemberRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : MemberRepository {
    private val writer = Writer(db, ctx)

    override fun observeMembers(groupId: String): Flow<List<Member>> = db.memberEntityQueries
        .selectVisibleByGroup(groupId)
        .asFlow()
        .mapToList(io)
        .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(groupId: String, input: MemberInput): String = withContext(io) {
        db.transactionWithResult {
            val member = Member(
                id = writer.newId(), groupId = groupId, name = input.name, avatar = input.avatar,
                defaultWeight = input.defaultWeight, userUid = null, active = true,
                sortKey = db.memberEntityQueries.nextSortKey(groupId).executeAsOne(), meta = writer.createdMeta(),
            )
            save(db, writer, member, old = null, action = ChangeAction.CREATED)
            member.id
        }
    }

    override suspend fun update(id: String, input: MemberInput) = edit(id) { old ->
        old.copy(name = input.name, avatar = input.avatar, defaultWeight = input.defaultWeight)
    }

    override suspend fun setActive(id: String, active: Boolean) = edit(id) { it.copy(active = active) }

    override suspend fun deleteIfUnused(id: String): Boolean = withContext(io) {
        db.transactionWithResult {
            if (db.memberEntityQueries.countMovements(id).executeAsOne() > 0) return@transactionWithResult false
            val old = requireMember(id)
            save(db, writer, old.copy(meta = writer.editedMeta(old.meta, deleted = true)), old, ChangeAction.DELETED)
            true
        }
    }

    private suspend fun edit(id: String, change: (Member) -> Member) = withContext(io) {
        db.transaction {
            val old = requireMember(id)
            val new = change(old).copy(meta = writer.editedMeta(old.meta))
            save(db, writer, new, old, ChangeAction.UPDATED)
        }
    }

    private fun requireMember(id: String): Member =
        requireNotNull(db.memberEntityQueries.selectById(id).executeAsOneOrNull()) { "Unknown member $id" }.toModel()

    internal companion object {
        fun save(db: AppDatabase, writer: Writer, member: Member, old: Member?, action: ChangeAction) {
            db.memberEntityQueries.upsert(member.toRow())
            writer.enqueue(
                member.groupId,
                OutboxType.MEMBER,
                member.id,
                old?.meta?.serverVersion ?: 0,
                json.encodeToString(Member.serializer(), member),
            )
            writer.log(member.groupId, EntityType.MEMBER, member.id, action, Diffs.member(old, member))
        }
    }
}
