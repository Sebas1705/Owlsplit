package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.GroupRoles
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Permission
import es.sebas1705.owlsplit.data.models.SyncMeta
import es.sebas1705.owlsplit.data.models.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRolesTest {
    private val meta = SyncMeta(1, 1, 1, "uid-ana", false, SyncState.SYNCED)

    private fun group(admin: String? = "ana", createdBy: String = "uid-ana") = Group(
        "g", "Ibiza", "plane", "BLUE", "EUR", null, null, "ABCDEFGH", listOf("uid-ana", "uid-luis"), emptyList(),
        createdBy, 0, meta, adminMemberId = admin,
    )

    private fun member(id: String, uid: String?, sortKey: Long = 0, denied: Set<Permission> = emptySet(), active: Boolean = true) =
        Member(id, "g", id, null, 1, uid, active, sortKey, meta, deniedPermissions = denied)

    private val ana = member("ana", "uid-ana")
    private val luis = member("luis", "uid-luis", sortKey = 1)
    private val marta = member("marta", null, sortKey = 2)

    @Test
    fun `the admin member runs the group and always can`() {
        val members = listOf(ana.copy(deniedPermissions = Permission.entries.toSet()), luis, marta)
        assertTrue(GroupRoles.isAdmin(group(), members, "uid-ana"))
        assertFalse(GroupRoles.isAdmin(group(), members, "uid-luis"))
        Permission.entries.forEach { assertTrue(GroupRoles.can(it, group(), members, "uid-ana")) }
        assertTrue(GroupRoles.canSettle(group(), members, "uid-ana", "luis", "marta"))
    }

    @Test
    fun `an old group is run by its creator's member, or by nobody once they left`() {
        assertEquals("ana", GroupRoles.adminOf(group(admin = null), listOf(ana, luis))?.id)
        val left = listOf(ana.copy(userUid = null), luis)
        assertNull(GroupRoles.adminOf(group(admin = null), left))
        assertTrue(GroupRoles.isAdminless(group(admin = null), left))
        assertFalse(GroupRoles.isAdmin(group(admin = null), left, "uid-luis"))
    }

    @Test
    fun `a member can do what the admin did not take away`() {
        val members = listOf(ana, luis.copy(deniedPermissions = setOf(Permission.ADD_EXPENSES)), marta)
        assertFalse(GroupRoles.can(Permission.ADD_EXPENSES, group(), members, "uid-luis"))
        assertTrue(GroupRoles.can(Permission.EDIT_EXPENSES, group(), members, "uid-luis"))
        assertFalse(GroupRoles.can(Permission.EDIT_EXPENSES, group(), members, "uid-nobody"))
    }

    @Test
    fun `a member records payments they take part in, the admin any`() {
        val members = listOf(ana, luis, marta)
        assertTrue(GroupRoles.canSettle(group(), members, "uid-luis", "luis", "ana"))
        assertTrue(GroupRoles.canSettle(group(), members, "uid-luis", "marta", "luis"))
        assertFalse(GroupRoles.canSettle(group(), members, "uid-luis", "ana", "marta"))
        val denied = listOf(ana, luis.copy(deniedPermissions = setOf(Permission.RECORD_PAYMENTS)), marta)
        assertFalse(GroupRoles.canSettle(group(), denied, "uid-luis", "luis", "ana"))
    }

    @Test
    fun `the oldest other member with an account inherits the group`() {
        val members = listOf(ana, marta, luis, member("pablo", "uid-pablo", sortKey = 3))
        assertEquals("luis", GroupRoles.successor(members, "uid-ana")?.id)
        assertEquals("ana", GroupRoles.successor(members.filter { it.id != "pablo" }, "uid-luis")?.id)
        assertNull(GroupRoles.successor(listOf(ana, marta), "uid-ana"))
    }
}
