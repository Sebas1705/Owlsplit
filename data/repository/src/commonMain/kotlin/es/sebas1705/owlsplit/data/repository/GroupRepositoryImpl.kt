package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.GroupInput
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GroupRepositoryImpl(
    private val db: AppDatabase,
    ctx: RepositoryContext,
    private val io: CoroutineDispatcher,
) : GroupRepository {
    private val writer = Writer(db, ctx)

    override fun observeGroups(): Flow<List<Group>> =
        db.groupEntityQueries.selectVisible().asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }

    override fun observeGroup(id: String): Flow<Group?> =
        db.groupEntityQueries.selectById(id).asFlow().mapToOneOrNull(io).map { it?.toModel() }

    override suspend fun create(input: GroupInput, creatorName: String): String = withContext(io) {
        db.transactionWithResult {
            val group = Group(
                id = writer.newId(), name = input.name, icon = input.icon, accent = input.accent,
                baseCurrency = input.baseCurrency, startDate = input.startDate, endDate = input.endDate,
                inviteCode = writer.newInviteCode(), memberUids = listOf(writer.uid()), archivedBy = emptyList(),
                createdBy = writer.uid(), createdAt = writer.now(), meta = writer.createdMeta(),
            )
            save(group, old = null, action = ChangeAction.CREATED)
            val member = Member(
                id = writer.newId(), groupId = group.id, name = creatorName, avatar = null, defaultWeight = 1,
                userUid = writer.uid(), active = true, sortKey = 0, meta = writer.createdMeta(),
            )
            MemberRepositoryImpl.save(db, writer, member, old = null, action = ChangeAction.CREATED)
            group.id
        }
    }

    override suspend fun update(id: String, input: GroupInput) = withContext(io) {
        db.transaction {
            val old = requireGroup(id)
            val new = old.copy(
                name = input.name,
                icon = input.icon,
                accent = input.accent,
                baseCurrency = input.baseCurrency,
                startDate = input.startDate,
                endDate = input.endDate,
                meta = writer.editedMeta(old.meta),
            )
            save(new, old, ChangeAction.UPDATED)
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) = withContext(io) {
        db.transaction {
            val old = requireGroup(id)
            val me = writer.uid()
            val archivedBy = if (archived) (old.archivedBy + me).distinct() else old.archivedBy - me
            if (archivedBy != old.archivedBy) {
                // Archiving is a personal preference: synced, but not part of the group history.
                val new = old.copy(archivedBy = archivedBy, meta = writer.editedMeta(old.meta))
                db.groupEntityQueries.upsert(new.toRow())
                writer.enqueue(new.id, OutboxType.GROUP, new.id, old.meta.serverVersion, payload(new))
            }
        }
    }

    override suspend fun leave(id: String) = withContext(io) {
        db.transaction {
            val me = writer.uid()
            // First release my member (it stays as a ghost, the accounts do not change)...
            db.memberEntityQueries.selectVisibleByGroup(id).executeAsList().map { it.toModel() }
                .filter { it.userUid == me }
                .forEach { old ->
                    val new = old.copy(userUid = null, meta = writer.editedMeta(old.meta))
                    MemberRepositoryImpl.save(db, writer, new, old, ChangeAction.UPDATED)
                }
            // ...then leave. No history entry: once out, the group can no longer be written.
            val old = requireGroup(id)
            if (me in old.memberUids) {
                val new = old.copy(memberUids = old.memberUids - me, meta = writer.editedMeta(old.meta))
                db.groupEntityQueries.upsert(new.toRow())
                writer.enqueue(new.id, OutboxType.GROUP, new.id, old.meta.serverVersion, payload(new))
            }
        }
    }

    private fun requireGroup(id: String): Group =
        requireNotNull(db.groupEntityQueries.selectById(id).executeAsOneOrNull()) { "Unknown group $id" }.toModel()

    private fun payload(group: Group) = json.encodeToString(Group.serializer(), group)

    private fun save(group: Group, old: Group?, action: ChangeAction) {
        db.groupEntityQueries.upsert(group.toRow())
        writer.enqueue(group.id, OutboxType.GROUP, group.id, old?.meta?.serverVersion ?: 0, payload(group))
        writer.log(group.id, EntityType.GROUP, group.id, action, Diffs.group(old, group))
    }
}
