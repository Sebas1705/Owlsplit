package es.sebas1705.owlsplit.domain.usecases.history

import es.sebas1705.owlsplit.data.models.FieldChange
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
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.SplitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class HistoryUseCasesTest {
    private val ledger = InMemoryLedger()
    private val conflicts = FakeConflicts()
    private val accounts = FakeAccounts()
    private val now = 40L * 24 * 60 * 60 * 1_000
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(now)
    }
    private val describer =
        HistoryDescriber(ledger.groups, ledger.members, ledger.expenses, ledger.transfers, ledger.changeLog, clock)

    private fun expense(title: String, category: ExpenseCategory, payer: String, others: List<String>) = ExpenseInput(
        title, category, null, LocalDate(2026, 10, 8), "USD", 1_000, 1_000_000_000, mapOf(payer to 1_000L),
        SplitType.EQUAL, (others + payer).associateWith { 1L },
    )

    private fun entry(id: String, group: String, type: EntityType, entity: String, action: ChangeAction, at: Long, author: String) =
        ChangeLogEntry(id, group, type, entity, action, author, emptyList(), at)

    @Test
    fun theFilterSearchesWithoutAccentsAndByPersonOrCategory() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val luis = ledger.members.add(g, MemberInput("Luis"))
        val marta = ledger.members.add(g, MemberInput("Marta"))
        ledger.expenses.create(g, expense("Café del puerto", ExpenseCategory.FOOD, luis, emptyList()))
        ledger.expenses.create(g, expense("Taxi", ExpenseCategory.TRANSPORT, marta, listOf(luis)))
        val all = ledger.expenseList.value

        assertEquals(listOf("Café del puerto"), ExpenseFilter(query = "cafe").apply(all).map { it.title })
        assertEquals(2, ExpenseFilter(memberId = luis).apply(all).size)
        assertEquals(
            listOf(
                "Taxi"
            ),
            ExpenseFilter(memberId = luis, category = ExpenseCategory.TRANSPORT).apply(all).map {
                it.title
            }
        )
        assertFalse(ExpenseFilter().active)
    }

    @Test
    fun peopleWhoLeftKeepTheirNameAndLeavingReadsAsSuch() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        // Both are members without an account now: Luis left, Marta deleted hers.
        val luis = ledger.members.add(g, MemberInput("Luis"))
        val marta = ledger.members.add(g, MemberInput("Marta"))
        val e = ledger.expenses.create(g, expense("Bus", ExpenseCategory.TRANSPORT, luis, emptyList()))
        val unlink = listOf(FieldChange("userUid", "uid-luis", null))
        ledger.log.value = listOf(
            // Written before the account link was recorded: an empty edit of herself.
            entry("m2", g, EntityType.MEMBER, marta, ChangeAction.UPDATED, 6, "uid-marta"),
            entry("m1", g, EntityType.MEMBER, marta, ChangeAction.JOINED, 5, "uid-marta"),
            ChangeLogEntry("l1", g, EntityType.MEMBER, luis, ChangeAction.UPDATED, "uid-luis", unlink, 4),
            ChangeLogEntry(
                "r1", g, EntityType.MEMBER, luis, ChangeAction.UPDATED, "uid-ana",
                listOf(FieldChange("name", "Lu", "Luis")), 3,
            ),
            entry("x1", g, EntityType.EXPENSE, e, ChangeAction.CREATED, 2, "uid-luis"),
            entry("j1", g, EntityType.MEMBER, luis, ChangeAction.JOINED, 1, "uid-luis"),
        )

        val items = ObserveHistory(
            describer,
            ledger.changeLog,
            conflicts,
            accounts
        )(g).first().associateBy { it.entry.id }
        assertEquals("Luis", items.getValue("x1").author)
        assertTrue(items.getValue("l1").leftGroup)
        assertEquals("Luis", items.getValue("l1").author)
        assertTrue(items.getValue("m2").leftGroup)
        assertEquals("Marta", items.getValue("m2").author)
        assertFalse(items.getValue("r1").leftGroup) // a rename is an edit
    }

    @Test
    fun entriesSayWhoDidWhatAndWhatCanBeUndone() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val luis = ledger.members.add(g, MemberInput("Luis"))
        val e = ledger.expenses.create(g, expense("Taxi", ExpenseCategory.TRANSPORT, luis, emptyList()))
        ledger.expenses.delete(e)
        ledger.log.value = listOf(
            entry("c3", g, EntityType.EXPENSE, e, ChangeAction.CONFLICT, now - 1, "uid-ana"),
            entry("c2", g, EntityType.EXPENSE, e, ChangeAction.DELETED, now - 2, "uid-ana"),
            entry("c1", g, EntityType.EXPENSE, e, ChangeAction.CREATED, 0, "uid-luis"),
        )
        conflicts.open.value = listOf(Conflict(7, g, EntityType.EXPENSE, e, now - 1))

        val items = ObserveHistory(describer, ledger.changeLog, conflicts, accounts)(g).first()
        val (conflict, deleted, created) = items
        assertEquals(7L, conflict.conflictId)
        assertTrue(deleted.canRestore)
        assertTrue(deleted.byMe)
        assertEquals("Ana", deleted.author)
        assertEquals("Taxi", created.subject)
        assertEquals("USD", created.currency)
        assertNull(created.author) // Luis never had an account: nothing says who
        assertFalse(created.canRestore)

        ResolveConflict(conflicts).reapply(7)
        assertEquals(listOf(7L), conflicts.reapplied)
        assertNull(ObserveHistory(describer, ledger.changeLog, conflicts, accounts)(g).first().first().conflictId)
    }

    @Test
    fun oldDeletionsCannotBeRestoredAnymore() = runTest {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val ana = ledger.memberList.value.single().id
        val e = ledger.expenses.create(g, expense("Taxi", ExpenseCategory.TRANSPORT, ana, emptyList()))
        ledger.expenses.delete(e)
        ledger.log.value = listOf(entry("c1", g, EntityType.EXPENSE, e, ChangeAction.DELETED, 0, "uid-ana"))
        assertFalse(ObserveHistory(describer, ledger.changeLog, conflicts, accounts)(g).first().single().canRestore)
    }

    @Test
    fun activityMixesGroupsAndListsOpenConflictsFirst() = runTest {
        val a = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val b = ledger.groups.create(GroupInput("Piso", "home", "ORANGE", "EUR"), "Ana")
        ledger.log.value = listOf(
            entry("c2", b, EntityType.GROUP, b, ChangeAction.CONFLICT, 2, "uid-ana"),
            entry("c1", a, EntityType.GROUP, a, ChangeAction.CREATED, 1, "uid-ana"),
        )
        conflicts.open.value = listOf(Conflict(1, b, EntityType.GROUP, b, 2))
        val activity = ObserveActivity(describer, ledger.changeLog, conflicts, accounts)().first()
        assertEquals(listOf("Piso", "Ibiza"), activity.items.map { it.groupName })
        assertEquals(listOf("Piso"), activity.pending.map { it.subject })
    }
}
