package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.repository.TestEnv
import es.sebas1705.owlsplit.data.repository.observeMembersOnce
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression: the upload loop restarted (collectLatest) every time an upload removed its outbox
 * row, cancelling the wait for the next acknowledgement. Like the Firestore SDK, the fake server
 * keeps a write whose acknowledgement nobody awaits any more; the entry was then pushed a second
 * time, refused (create-only history entries) and only cleared by a conflict-check fetch.
 */
class ChangelogDoublePushReproTest {
    @Test
    fun `each outbox entry is pushed exactly once even when acknowledgements are slow`() = runTest {
        val env = TestEnv(this)
        val remote = FakeRemoteStore { env.ctx.clock.now().toEpochMilliseconds() }
        // Network latency between the server accepting a write and the client seeing the ack.
        remote.beforeAck = { delay(ACK_MILLIS) }
        val engine = SyncEngine(env.db, remote, env.ctx, env.dispatcher, Random(0))
        val job = engine.start(this, "uid-ana")

        val g = env.groups.create(GroupInput("Ibiza 2026", "plane", "BLUE", "EUR"), "Ana")
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()
        val ana = env.members.observeMembersOnce(g).single().id
        env.expenses.create(
            g,
            ExpenseInput(
                "Cena", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 4_860, 1_000_000_000,
                mapOf(ana to 4_860L), SplitType.EQUAL, mapOf(ana to 1L),
            ),
        )
        advanceTimeBy(SETTLE_MILLIS)
        runCurrent()

        // Group, its entry, creator member, its entry, expense, its entry: one push each.
        assertEquals(6, remote.pushLog.size)
        assertEquals(emptyMap(), remote.pushLog.groupingBy { it }.eachCount().filterValues { it > 1 })
        // No push was refused, so nothing had to be checked against the server.
        assertEquals(emptyList(), remote.fetches)
        assertEquals(0L, engine.pendingCount.first())
        job.cancel()
    }

    private companion object {
        const val ACK_MILLIS = 150L
        const val SETTLE_MILLIS = 5_000L
    }
}
