package es.sebas1705.owlsplit.feature.history

import es.sebas1705.owlsplit.core.testing.CoroutineTestRule
import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.ledger.FakeConflicts
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.ChangeAction
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import es.sebas1705.owlsplit.data.models.Conflict
import es.sebas1705.owlsplit.data.models.EntityType
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.domain.usecases.history.HistoryDescriber
import es.sebas1705.owlsplit.domain.usecases.history.ObserveActivity
import es.sebas1705.owlsplit.domain.usecases.history.ObserveHistory
import es.sebas1705.owlsplit.domain.usecases.history.ResolveConflict
import es.sebas1705.owlsplit.domain.usecases.history.RestoreExpense
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class HistoryViewModelTest {
    private val rule = CoroutineTestRule()

    @BeforeTest
    fun setUp() = rule.setup()

    @AfterTest
    fun tearDown() = rule.tearDown()

    private val ledger = InMemoryLedger()
    private val conflicts = FakeConflicts()
    private val accounts = FakeAccounts()
    private val describer =
        HistoryDescriber(ledger.groups, ledger.members, ledger.expenses, ledger.transfers, ledger.changeLog)

    private fun viewModel(groupId: String?) = HistoryViewModel(
        groupId,
        ObserveHistory(describer, ledger.changeLog, conflicts, accounts),
        ObserveActivity(describer, ledger.changeLog, conflicts, accounts, ledger.groups),
        RestoreExpense(ledger.expenses),
        ResolveConflict(conflicts),
    )

    @Test
    fun aDeletedExpenseCanBeRestoredAndAConflictReapplied() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val ana = ledger.memberList.value.single().id
        val e = ledger.expenses.create(
            g,
            ExpenseInput(
                "Taxi", ExpenseCategory.TRANSPORT, null, LocalDate(2026, 10, 8), "EUR", 1_000, 1_000_000_000,
                mapOf(ana to 1_000L), SplitType.EQUAL, mapOf(ana to 1L),
            ),
        )
        ledger.expenses.delete(e)
        val now = Clock.System.now().toEpochMilliseconds()
        ledger.log.value = listOf(
            ChangeLogEntry("c2", g, EntityType.EXPENSE, e, ChangeAction.CONFLICT, "uid-ana", emptyList(), now),
            ChangeLogEntry("c1", g, EntityType.EXPENSE, e, ChangeAction.DELETED, "uid-ana", emptyList(), now - 1),
        )
        conflicts.open.value = listOf(Conflict(3, g, EntityType.EXPENSE, e, now))

        val vm = viewModel(g)
        assertFalse(vm.uiState.value.acrossGroups)
        assertTrue(vm.uiState.value.items[1].canRestore)
        vm.onEvent(HistoryIntent.Restore(EntityType.EXPENSE, e))
        assertFalse(ledger.expenseList.value.single().meta.deleted)
        assertFalse(vm.uiState.value.items[1].canRestore)

        vm.onEvent(HistoryIntent.Reapply(3))
        assertEquals(listOf(3L), conflicts.reapplied)

        val activity = viewModel(null)
        assertTrue(activity.uiState.value.acrossGroups)
        assertEquals(2, activity.uiState.value.items.size)
        assertTrue(activity.uiState.value.pending.isEmpty())
    }
}
