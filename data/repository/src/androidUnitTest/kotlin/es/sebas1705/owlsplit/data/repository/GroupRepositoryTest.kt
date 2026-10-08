package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.models.GroupInput
import app.cash.turbine.test
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupRepositoryTest {
    private val input = GroupInput(name = "Ibiza 2026", icon = "plane", accent = "blue", baseCurrency = "EUR")

    @Test
    fun `creating a group also creates the creator member and queues both`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, creatorName = "Ana")

        val group = env.groups.observeGroup(groupId).first()!!
        assertEquals("Ibiza 2026", group.name)
        assertEquals(listOf("uid-ana"), group.memberUids)
        assertEquals("CODE0", group.inviteCode)
        assertEquals(1L, group.meta.version)
        assertEquals(SyncState.PENDING, group.meta.syncState)

        env.members.observeMembers(groupId).test {
            val members = awaitItem()
            assertEquals(1, members.size)
            assertEquals("Ana", members.single().name)
            assertEquals("uid-ana", members.single().userUid)
            assertEquals(0L, members.single().sortKey)
        }

        val types = env.outbox().map { OutboxType.valueOf(it.entity_type) }
        assertEquals(listOf(OutboxType.GROUP, OutboxType.CHANGE_LOG, OutboxType.MEMBER, OutboxType.CHANGE_LOG), types)
    }

    @Test
    fun `edits before sync are compacted into one outbox entry with the original base version`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, "Ana")
        env.groups.update(groupId, input.copy(name = "Ibiza"))
        env.groups.update(groupId, input.copy(name = "Ibiza!"))

        val groupEntries = env.outbox().filter { it.entity_type == OutboxType.GROUP.name }
        assertEquals(1, groupEntries.size)
        assertEquals(0L, groupEntries.single().base_version)
        assertTrue(groupEntries.single().payload.contains("Ibiza!"))

        // The outbox is compacted, the history is not: both edits are recorded.
        env.changeLog.observe(groupId).test {
            val groupHistory = awaitItem().filter { it.entityType == EntityType.GROUP }
            assertEquals(1, groupHistory.count { it.action == ChangeAction.CREATED })
            assertEquals(2, groupHistory.count { it.action == ChangeAction.UPDATED })
        }
    }

    @Test
    fun `archiving is per user and hides nothing from the list`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, "Ana")
        env.groups.setArchived(groupId, true)
        env.groups.observeGroups().test {
            assertEquals(listOf("uid-ana"), awaitItem().single().archivedBy)
        }
        env.groups.setArchived(groupId, false)
        env.groups.observeGroups().test {
            assertTrue(awaitItem().single().archivedBy.isEmpty())
        }
    }

    @Test
    fun `leaving leaves my member as a ghost and removes me from the group`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, creatorName = "Ana")

        env.groups.leave(groupId)

        val member = env.members.observeMembers(groupId).first().single()
        assertEquals(null, member.userUid)
        assertEquals(emptyList(), env.groups.observeGroup(groupId).first()!!.memberUids)
    }

    @Test
    fun `leaving a synced group queues the member release before the group`() = runTest {
        val env = TestEnv(this)
        val groupId = env.groups.create(input, creatorName = "Ana")
        env.db.outboxQueries.selectAll().executeAsList().forEach { env.db.outboxQueries.deleteById(it.id) }

        env.groups.leave(groupId)

        val types = env.outbox().map { OutboxType.valueOf(it.entity_type) }
        assertEquals(listOf(OutboxType.MEMBER, OutboxType.CHANGE_LOG, OutboxType.GROUP), types)
        assertTrue(env.outbox().first().payload.contains("\"userUid\":null"))
    }

    @Test
    fun `wiping removes every local row`() = runTest {
        val env = TestEnv(this)
        env.groups.create(input, creatorName = "Ana")
        LocalData(env.db, env.dispatcher).wipe()
        assertTrue(env.groups.observeGroups().first().isEmpty())
        assertTrue(env.outbox().isEmpty())
        assertTrue(env.changeLog.observe("any").first().isEmpty())
    }
}
