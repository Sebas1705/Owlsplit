package es.sebas1705.owlsplit.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransferRepositoryTest {
    @Test
    fun `transfers can be created, deleted and restored`() = runTest {
        val env = TestEnv(this)
        val g = env.groups.create(GroupInput("Piso", "home", "green", "EUR"), "Ana")
        val ana = env.members.observeMembersOnce(g).single().id
        val luis = env.members.add(g, MemberInput("Luis"))
        val id = env.transfers.create(g, TransferInput(luis, ana, 1_215, "EUR", 1_000_000_000, LocalDate(2026, 7, 13)))

        env.transfers.observeTransfers(g).test { assertEquals(1_215L, awaitItem().single().amountMinor) }
        env.transfers.delete(id)
        env.transfers.observeTransfers(g).test { assertTrue(awaitItem().isEmpty()) }
        env.transfers.restore(id)
        env.transfers.observeTransfers(g).test { assertEquals(id, awaitItem().single().id) }
    }
}
