package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.core.testing.account.FakeAccounts
import es.sebas1705.owlsplit.core.testing.ledger.InMemoryLedger
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.data.models.ExpenseCategory
import es.sebas1705.owlsplit.data.models.ExpenseInput
import es.sebas1705.owlsplit.data.models.SplitType
import es.sebas1705.owlsplit.data.models.TransferInput
import es.sebas1705.owlsplit.domain.usecases.balances.ObserveGroupBalances
import kotlinx.datetime.LocalDate
import es.sebas1705.owlsplit.data.models.MemberInput
import es.sebas1705.owlsplit.data.models.Permission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupAdminTest {
    private val ledger = InMemoryLedger()
    private val accounts = FakeAccounts()
    private val admin = GroupAdmin(ledger.groups, ledger.members, accounts)

    private suspend fun group(): Triple<String, String, String> {
        val g = ledger.groups.create(GroupInput("Ibiza", "plane", "BLUE", "EUR"), "Ana")
        val luis = ledger.members.add(g, MemberInput("Luis"))
        ledger.members.claim(luis)
        // claim linked Luis to the signed-in uid; give him his own account.
        ledger.memberList.value = ledger.memberList.value.map { if (it.id == luis) it.copy(userUid = "uid-luis") else it }
        val ana = ledger.members.observeMembers(g).first().first { it.name == "Ana" }.id
        return Triple(g, ana, luis)
    }

    private suspend fun denied(memberId: String) =
        ledger.members.observeMembers(ledger.groupList.value.first().id).first().first { it.id == memberId }.deniedPermissions

    @Test
    fun `the admin takes permissions away and gives them back`() = runTest {
        val (g, _, luis) = group()
        assertTrue(admin.setPermission(g, luis, Permission.ADD_EXPENSES, allowed = false))
        assertTrue(admin.setPermission(g, luis, Permission.RECORD_PAYMENTS, allowed = false))
        assertEquals(setOf(Permission.ADD_EXPENSES, Permission.RECORD_PAYMENTS), denied(luis))
        assertTrue(admin.setPermission(g, luis, Permission.ADD_EXPENSES, allowed = true))
        assertEquals(setOf(Permission.RECORD_PAYMENTS), denied(luis))
    }

    @Test
    fun `the admin cannot be restricted and others cannot restrict`() = runTest {
        val (g, ana, luis) = group()
        assertFalse(admin.setPermission(g, ana, Permission.ADD_EXPENSES, allowed = false))
        ledger.uid = "uid-luis"
        accounts.session.value = accounts.session.value!!.copy(uid = "uid-luis")
        assertFalse(admin.setPermission(g, luis, Permission.EDIT_EXPENSES, allowed = false))
        assertFalse(admin.makeAdmin(g, luis))
        assertEquals(emptySet(), denied(luis))
    }

    @Test
    fun `the group can be handed over to a member with an account only`() = runTest {
        val (g, ana, luis) = group()
        val marta = ledger.members.add(g, MemberInput("Marta"))
        assertFalse(admin.makeAdmin(g, marta))
        assertEquals(ana, ledger.groups.observeGroup(g).first()!!.adminMemberId)
        assertTrue(admin.makeAdmin(g, luis))
        assertEquals(luis, ledger.groups.observeGroup(g).first()!!.adminMemberId)
        // Ana is now an ordinary member.
        assertFalse(admin.makeAdmin(g, ana))
    }

    @Test
    fun `a member takes over an old group nobody runs`() = runTest {
        val (g, ana, luis) = group()
        assertFalse(admin.takeOver(g))
        // An old group: no admin recorded and the creator gone.
        ledger.groupList.value = ledger.groupList.value.map { it.copy(adminMemberId = null, createdBy = "uid-gone") }
        assertTrue(admin.takeOver(g))
        assertEquals(ana, ledger.groups.observeGroup(g).first()!!.adminMemberId)
        assertEquals(luis, ledger.memberList.value.first { it.id == luis }.id)
    }

    private fun balances(g: String) =
        ObserveGroupBalances(ledger.groups, ledger.members, ledger.expenses, ledger.transfers)

    @Test
    fun `merging two people moves their movements to the one kept and leaves the balances alone`() = runTest {
        val (g, ana, luis) = group()
        val luisAgain = ledger.members.add(g, MemberInput("Luis (Bizum)"))
        fun input(payer: String, vararg who: String) = ExpenseInput(
            "Cena", ExpenseCategory.FOOD, null, LocalDate(2026, 7, 12), "EUR", 6_000, 1_000_000_000L,
            mapOf(payer to 6_000L), SplitType.EQUAL, who.associateWith { 1L }, null,
        )
        ledger.expenses.create(g, input(ana, ana, luis, luisAgain))
        ledger.expenses.create(g, input(luisAgain, ana, luisAgain))
        ledger.transfers.create(g, TransferInput(luisAgain, ana, 500, "EUR", 1_000_000_000L, LocalDate(2026, 7, 13)))
        val before = balances(g)(g).first()!!.balances

        assertEquals(luis, admin.merge(g, luisAgain, luis))

        assertEquals(setOf(ana, luis), ledger.members.observeMembers(g).first().map { it.id }.toSet())
        assertTrue(ledger.expenseList.value.all { luisAgain !in it.payers && luisAgain !in it.splitValues })
        assertEquals(luis, ledger.transferList.value.single().fromMemberId)
        val after = balances(g)(g).first()!!.balances
        assertEquals(before[luis]!! + before[luisAgain]!!, after[luis])
        assertEquals(before[ana], after[ana])
    }

    @Test
    fun `two people with an account or a non admin cannot merge`() = runTest {
        val (g, ana, luis) = group()
        assertEquals(null, admin.merge(g, luis, ana))
        val marta = ledger.members.add(g, MemberInput("Marta"))
        ledger.uid = "uid-luis"
        accounts.session.value = accounts.session.value!!.copy(uid = "uid-luis")
        assertEquals(null, admin.merge(g, marta, luis))
    }
}
