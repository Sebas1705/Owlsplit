package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.SyncState
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
import kotlin.test.assertFalse
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

        fun serverNow() = env.ctx.clock.now().toEpochMilliseconds()

        fun expenseOnServer(id: String) = (remote.doc(OutboxType.EXPENSE, id) as RemoteDoc.ExpenseDoc).expense
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
    fun `remote changes are fetched by the id in their history entry and older versions ignored`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        h.remote.fetches.clear()
        h.remote.pulls.clear()

        val newer = h.expenseOnServer(e).let {
            it.copy(title = "Cena con postre", meta = it.meta.copy(version = 3, updatedAt = h.serverNow() + 10_000))
        }
        h.remote.editByOtherDevice(RemoteDoc.ExpenseDoc(newer))
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)
        // One get of that document: no query over the expenses.
        assertEquals(listOf(OutboxType.EXPENSE to e), h.remote.fetches)
        assertTrue(h.remote.pulls.isEmpty())

        h.remote.editByOtherDevice(
            RemoteDoc.ExpenseDoc(
                newer.copy(title = "Old", meta = newer.meta.copy(version = 2, updatedAt = h.serverNow() + 20_000))
            )
        )
        h.settle()
        assertEquals("Cena con postre", h.env.expenses.get(e)!!.title)
        assertEquals(SyncState.SYNCED, h.env.expenses.get(e)!!.meta.syncState)
        job.cancel()
    }

    @Test
    fun `my own history entries coming back fetch nothing`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        h.env.expenses.create(g, dinner(ana))
        h.settle()
        assertEquals(1, h.remote.pushLog.count { it.first == OutboxType.EXPENSE })
        assertTrue(h.remote.fetches.isEmpty())
        job.cancel()
    }

    @Test
    fun `an idle restart opens one changelog listener per group not archived and reads nothing else`() = runTest {
        val h = Harness(this)
        val groups = listOf("A", "B", "C").map { h.env.groups.create(trip.copy(name = it), "Ana") }
        var job = h.engine.start(this, "uid-ana")
        h.settle()
        h.env.groups.setArchived(groups[2], archived = true)
        h.settle()
        job.cancel()
        runCurrent()
        assertTrue(h.remote.listening.values.all { it == 0 })

        h.remote.reads = 0
        h.remote.pulls.clear()
        job = h.engine.start(this, "uid-ana")
        h.settle()

        val open = h.remote.listening.filterValues { it > 0 }.keys
        assertEquals(setOf(groups[0] to RemoteCollection.CHANGELOG, groups[1] to RemoteCollection.CHANGELOG), open)
        assertTrue(h.remote.pulls.isEmpty())
        assertTrue(h.remote.fetches.isEmpty())
        // My groups (3 documents) + 2 changelog listeners re-reading the margin before their cursor:
        // the 2 entries of each creation. One listener per collection used to cost 3 + 3 × (1 + 1 + 1 + 2).
        assertEquals(3L + 2 + 2, h.remote.reads)
        job.cancel()
    }

    @Test
    fun `an archived group is not listened to and catches up when opened`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        h.env.groups.setArchived(g, archived = true)
        h.settle()
        assertEquals(0, h.remote.listening[g to RemoteCollection.CHANGELOG])

        val theirs = h.expenseOnServer(
            e
        ).let { it.copy(title = "Theirs", meta = it.meta.copy(version = 2, updatedAt = h.serverNow())) }
        h.remote.editByOtherDevice(RemoteDoc.ExpenseDoc(theirs))
        h.settle()
        assertEquals("Cena", h.env.expenses.get(e)!!.title)

        h.engine.refreshGroup(g)
        h.settle()
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(0, h.remote.listening[g to RemoteCollection.CHANGELOG])

        // Reopened right away: throttled.
        val pulls = h.remote.pulls.size
        h.engine.refreshGroup(g)
        h.settle()
        assertEquals(pulls, h.remote.pulls.size)
        job.cancel()
    }

    @Test
    fun `unarchiving a group listens again from its cursor and gets what changed meanwhile`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        h.env.groups.setArchived(g, archived = true)
        h.settle()
        h.remote.editByOtherDevice(
            RemoteDoc.ExpenseDoc(
                h.expenseOnServer(
                    e
                ).let { it.copy(title = "Theirs", meta = it.meta.copy(version = 2, updatedAt = h.serverNow())) }
            )
        )
        h.settle()

        h.env.groups.setArchived(g, archived = false)
        h.settle()
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(1, h.remote.listening[g to RemoteCollection.CHANGELOG])
        job.cancel()
    }

    @Test
    fun `personal member fields, which have no history entry, arrive when the group is opened`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        val member = (
            h.env.members.observeMembersOnce(
                g
            ).single().id
            ).let { h.remote.doc(OutboxType.MEMBER, it) as RemoteDoc.MemberDoc }
        // Like publishMyLinked from Ana's other phone: synced but out of the history.
        val linked = member.member.copy(
            linked = true,
            meta = member.member.meta.copy(version = 2, updatedAt = h.serverNow())
        )
        h.remote.editByOtherDevice(RemoteDoc.MemberDoc(linked), logged = false)
        h.settle()
        assertFalse(h.env.members.observeMembersOnce(g).single().linked)

        h.engine.refreshGroup(g)
        h.settle()
        assertTrue(h.env.members.observeMembersOnce(g).single().linked)
        job.cancel()
    }

    @Test
    fun `a fetch that fails offline is retried and the change is not lost`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()

        h.remote.failingReads = 2
        h.remote.editByOtherDevice(
            RemoteDoc.ExpenseDoc(
                h.expenseOnServer(
                    e
                ).let { it.copy(title = "Theirs", meta = it.meta.copy(version = 2, updatedAt = h.serverNow())) }
            )
        )
        h.settle()
        h.settle()
        assertEquals(0, h.remote.failingReads)
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(
            1,
            h.env.db.changeLogQueries.selectByGroup(g).executeAsList().count { it.id.startsWith("remote-log") }
        )
        job.cancel()
    }

    @Test
    fun `a fetched change over a pending local edit becomes a conflict`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        val job = h.engine.start(this, "uid-ana")
        h.settle()

        repeat(10) { h.remote.scripted += PushResult.Transient } // our edit stays pending
        h.env.expenses.update(e, dinner(ana).copy(title = "Mine"))
        h.remote.editByOtherDevice(
            RemoteDoc.ExpenseDoc(
                h.expenseOnServer(
                    e
                ).let { it.copy(title = "Theirs", meta = it.meta.copy(version = 2, updatedAt = h.serverNow())) }
            )
        )
        h.settle()
        assertEquals("Theirs", h.env.expenses.get(e)!!.title)
        assertEquals(1L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.outbox().none { it.entity_type == "EXPENSE" })
        h.remote.scripted.clear()
        job.cancel()
    }

    @Test
    fun `a new device downloads a group whole once and then only listens to its history`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val e = h.env.expenses.create(g, dinner(ana))
        h.engine.pushNow()

        // Same account on a second phone: same server, empty database.
        val other = TestEnv(this)
        val engine = SyncEngine(other.db, h.remote, other.ctx, other.dispatcher, Random(0))
        val job = engine.start(this, "uid-ana")
        h.settle()

        assertEquals("Cena", other.expenses.get(e)!!.title)
        assertEquals(listOf("Ana"), other.members.observeMembersOnce(g).map { it.name })
        assertEquals(3, other.db.changeLogQueries.selectByGroup(g).executeAsList().size)
        assertEquals(
            listOf(
                RemoteCollection.CHANGELOG,
                RemoteCollection.MEMBERS,
                RemoteCollection.EXPENSES,
                RemoteCollection.TRANSFERS
            ),
            h.remote.pulls.map { it.second },
        )
        // Its entries came with the bootstrap: the listener fetches none of them again.
        assertTrue(h.remote.fetches.isEmpty())
        assertEquals(1, h.remote.listening[g to RemoteCollection.CHANGELOG])

        // Restarted later: no second download.
        job.cancel()
        val again = engine.start(this, "uid-ana")
        h.settle()
        assertEquals(4, h.remote.pulls.size)
        again.cancel()
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
    fun `a write that timed out but reached the server is acknowledged by its echo, not a conflict`() = runTest {
        // Offline, Firestore keeps the write queued while push gives up (Transient); back online the
        // queued write lands and the listener delivers it with the outbox entry still pending.
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        h.engine.pushNow()
        h.remote.lostAcks = 1
        val e = h.env.expenses.create(g, dinner(ana))
        h.engine.pushNow()
        assertTrue(h.env.outbox().any { it.entity_type == "EXPENSE" })

        val job = h.engine.start(this, "uid-ana")
        h.settle()

        assertEquals(0L, h.env.db.conflictQueries.countAll().executeAsOne())
        assertTrue(h.env.outbox().none { it.entity_type == "EXPENSE" })
        assertEquals(1L, h.remote.doc(OutboxType.EXPENSE, e)!!.version)
        assertEquals("Cena", h.env.expenses.get(e)!!.title)
        job.cancel()
    }

    @Test
    fun `flush uploads everything now and reports what could not be uploaded`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        h.remote.scripted += PushResult.Transient
        h.engine.pushNow() // the group now waits for its backoff
        assertTrue(h.env.outbox().isNotEmpty())

        assertTrue(h.engine.flush()) // ignores the backoff
        assertTrue(h.env.outbox().isEmpty())

        h.env.groups.update(g, trip.copy(name = "Offline"))
        repeat(5) { h.remote.scripted += PushResult.Transient }
        assertEquals(false, h.engine.flush())
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

    @Test
    fun `writes stuck in a group that dropped me are not shown as pending, and my flags skip it`() = runTest {
        val h = Harness(this)
        val g = h.env.groups.create(trip, "Ana")
        val ana = h.env.members.observeMembersOnce(g).single().id
        val job = h.engine.start(this, "uid-ana")
        h.settle()
        val doc = h.remote.doc(OutboxType.GROUP, g) as RemoteDoc.GroupDoc
        h.remote.serverWrite(
            doc.copy(group = doc.group.copy(memberUids = listOf("uid-luis"), meta = doc.group.meta.copy(version = 2)))
        )
        h.settle()
        assertTrue(h.engine.isAccessLost(g))

        // Linking Google publishes my "linked" flag: not on a group that no longer lists me.
        h.env.members.publishMyLinked(true)
        assertTrue(h.env.outbox().none { it.entity_type == "MEMBER" })
        // A write that can never be uploaded does not count as pending.
        h.env.expenses.create(g, dinner(ana))
        h.settle()
        assertTrue(h.env.outbox().isNotEmpty())
        assertEquals(0L, h.engine.pendingCount.first())
        job.cancel()
    }

    @Test
    fun `a lost group that lists me again uploads again`() = runTest {
        // Left and joined again (maybe with another account) on this device: not stuck as "lost".
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

        h.remote.serverWrite(
            doc.copy(
                group = doc.group.copy(
                    memberUids = listOf("uid-luis", "uid-ana"),
                    meta = doc.group.meta.copy(version = 3)
                )
            )
        )
        h.settle()
        assertFalse(h.engine.isAccessLost(g))
        job.cancel()
    }
}
