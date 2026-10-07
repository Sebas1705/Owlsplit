package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncState
import es.sebas1705.owlsplit.data.repository.ExpenseInput
import es.sebas1705.owlsplit.data.repository.GroupInput
import es.sebas1705.owlsplit.data.repository.TestEnv
import es.sebas1705.owlsplit.data.repository.observeMembersOnce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SyncEngineTest {
    private val trip = GroupInput("Ibiza 2026", "plane", "BLUE", "EUR")

    private fun dinner(payer: String) = ExpenseInput(
        "Cena", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 4_860, 1_000_000_000,
        mapOf(payer to 4_860L), SplitType.EQUAL, mapOf(payer to 1L),
    )

    private class Harness(val scope: TestScope) {
        val env = TestEnv(scope)
        val remote = FakeRemoteStore { env.ctx.clock.now().toEpochMilliseconds() }
        val engine = SyncEngine(env.db, remote, env.ctx, env.dispatcher, Random(0))

        fun settle() {
            scope.advanceTimeBy(5_000)
            scope.runCurrent()
        }
    }

    @Test
    fun `local writes are uploaded automatically while the engine runs`() = runTest {
        val h = Harness(this)
        val job = h.engine.start(this, "uid-ana")
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        h.env.expenses.create(g, dinner(ana))
        h.settle()
        assertNotNull(h.remote.doc(OutboxType.GROUP, g))
        assertEquals(1, h.remote.pushLog.count { it.first == OutboxType.EXPENSE })
        assertEquals(0L, h.engine.pendingCount.first())
        job.cancel()
    }

    @Test
    fun `remote changes are pulled and older versions ignored`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()

        val synced = h.remote.doc(OutboxType.EXPENSE, e) as RemoteDoc.ExpenseDoc
        val newer = synced.expense.copy(
            title = "Cena con postre",
            meta = synced.expense.meta.copy(version = 3, updatedAt = h.env.nowMillis + 10_000),
        )
        h.remote.serverWrite(RemoteDoc.ExpenseDoc(newer))
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)

        h.remote.serverWrite(RemoteDoc.ExpenseDoc(newer.copy(title = "Old", meta = newer.meta.copy(version = 2))))
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)
        assertEquals(SyncState.SYNCED, h.env.expenses.get(e)!!.meta.syncState)
        job.cancel()
    }

    @Test
    fun `a pulled change over a pending local edit becomes a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        h.engine.pushNow()

        h.remote.scripted += PushResult.Transient // keep our next edit pending
        h.env.expenses.update(e, dinner(ana).copy(title = "Mine"))
        h.engine.pushNow()
        val synced = h.remote.doc(OutboxType.EXPENSE, e) as RemoteDoc.ExpenseDoc
        h.remote.serverWrite(
            RemoteDoc.ExpenseDoc(
                synced.expense.copy(
                    title = "Theirs",
                    meta = synced.expense.meta.copy(version = 2, updatedAt = h.env.nowMillis + 1)
                ),
            ),
        )

        val job = h.engine.start(this, "uid-ana")
        h.settle()
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(1L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.outbox().none { it.entity_type == "EXPENSE" })
        job.cancel()
    }

    @Test
    fun `the echo of an upload that arrives before its acknowledgement is not a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val job = h.engine.start(this, "uid-ana")
        h.settle()

        h.remote.beforeAck = { repeat(10) { yield() } }
        val e = h.env.expenses.create(g, dinner(ana))
        h.settle()
        h.env.expenses.update(e, dinner(ana).copy(title = "Cena larga"))
        h.settle()

        assertEquals(0L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertEquals("Cena larga", h.env.expenses.get(e)!!.title)
        assertEquals(2L, h.remote.doc(OutboxType.EXPENSE, e)!!.version)
        assertEquals(0L, h.engine.pendingCount.first())
        job.cancel()
    }

    @Test
    fun `a group that disappears from my groups is marked as lost`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        val doc = h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc
        h.remote.serverWrite(
            doc.copy(group = doc.group.copy(memberUids = listOf("uid-luis"), meta = doc.group.meta.copy(version = 2)))
        )
        h.settle()
        assertTrue(h.engine.isAccessLost(g))
        job.cancel()
    }
}
