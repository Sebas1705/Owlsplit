package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.TestEnv
import es.sebas1705.owlsplit.data.repository.Writer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxProcessorTest {
    private val trip = GroupInput("Ibiza 2026", "plane", "BLUE", "EUR")

    private class Harness(scope: TestScope) {
        val env = TestEnv(scope)
        val remote = FakeRemoteStore { env.ctx.clock.now().toEpochMilliseconds() }
        private val writer = Writer(env.db, env.ctx)
        val local = SyncLocalStore(env.db, writer)
        val processor = OutboxProcessor(local, RemoteApplier(env.db, writer), remote, Random(0))

        suspend fun push() = processor.processReady()
        suspend fun group(id: String) = env.groups.observeGroup(id).first()!!
        fun remoteGroup(id: String) = remote.doc(OutboxType.GROUP, id) as RemoteDoc.GroupDoc
    }

    @Test
    fun `a new group and its creator member are uploaded and marked synced`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        assertEquals(1L, h.remoteGroup(g).version)
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
        assertEquals(1L, h.group(g).meta.serverVersion)
        assertTrue(h.env.outbox().isEmpty())
        assertEquals(
            listOf(OutboxType.GROUP, OutboxType.CHANGE_LOG, OutboxType.MEMBER, OutboxType.CHANGE_LOG),
            h.remote.pushLog.map { it.first },
        )
    }

    @Test
    fun `offline edits upload once and later edits chain the version`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.env.groups.update(g, trip.copy(name = "Ibiza"))
        h.env.groups.update(g, trip.copy(name = "Ibiza!"))
        h.push()
        assertEquals(1, h.remote.pushLog.count { it.first == OutboxType.GROUP })
        assertEquals("Ibiza!", h.remoteGroup(g).group.name)

        h.env.groups.update(g, trip.copy(name = "Ibiza 26"))
        h.push()
        assertEquals(2L, h.remoteGroup(g).version)
    }

    @Test
    fun `a transient failure retries later and blocks the rest of the group`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.remote.scripted += PushResult.Transient
        h.push()
        assertEquals(1, h.remote.pushLog.size) // the member waited for its group
        val entry = h.env.outbox().first()
        assertEquals(1L, entry.attempts)
        assertTrue(entry.next_attempt_at > h.local.now())

        h.push() // too early: nothing is retried
        assertEquals(1, h.remote.pushLog.size)

        h.env.nowMillis = entry.next_attempt_at
        h.push()
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
    }

    @Test
    fun `an edit made during the upload advances the base instead of being lost`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val entry = h.env.outbox().first { it.entity_type == "GROUP" }
        // Uploaded as v1, but the user edits before the upload is confirmed locally.
        h.remote.push(OutboxType.GROUP, g, g, 1, entry.payload)
        h.env.groups.update(g, trip.copy(name = "Edited meanwhile"))
        h.local.onPushed(entry, 1L)

        assertEquals(1L, h.env.outbox().first { it.entity_type == "GROUP" }.base_version)
        h.push()
        assertEquals(2L, h.remoteGroup(g).version)
        assertEquals("Edited meanwhile", h.remoteGroup(g).group.name)
    }

    @Test
    fun `losing an acknowledgement does not create a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val entry = h.env.outbox().first { it.entity_type == "GROUP" }
        h.remote.push(OutboxType.GROUP, g, g, 1, entry.payload) // written, but the app died before recording it
        h.push()
        assertEquals(SyncState.SYNCED, h.group(g).meta.syncState)
        assertEquals(0L, h.env.db.conflictQueries.countAll().executeAsOne())
    }

    @Test
    fun `a concurrent edit by someone else wins and ours is kept as a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        val theirs = h.remoteGroup(g)
        h.remote.serverWrite(
            theirs.copy(group = theirs.group.copy(name = "Theirs", meta = theirs.group.meta.copy(version = 2)))
        )

        h.env.groups.update(g, trip.copy(name = "Mine"))
        h.push()

        assertEquals("Theirs", h.group(g).name)
        assertEquals(1L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.db.conflictQueries.selectByGroup(g).executeAsList().single().local_payload.contains("Mine"))
        assertTrue(h.env.changeLog.observe(g).first().any { it.action == ChangeAction.CONFLICT })
    }

    @Test
    fun `losing access stops uploads for that group`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.push()
        val doc = h.remoteGroup(g)
        h.remote.serverWrite(
            doc.copy(group = doc.group.copy(memberUids = listOf("uid-luis"), meta = doc.group.meta.copy(version = 2)))
        )

        h.env.groups.update(g, trip.copy(name = "Still mine?"))
        h.push()
        assertTrue(h.local.isAccessLost(g))
        val pushes = h.remote.pushLog.size
        h.push()
        assertEquals(pushes, h.remote.pushLog.size)
    }
}
