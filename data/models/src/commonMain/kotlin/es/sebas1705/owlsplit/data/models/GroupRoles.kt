package es.sebas1705.owlsplit.data.models

/**
 * Who runs a group and what each member may do (docs/designs/2026-11-03-group-admin.md). Pure: the
 * same answers on every screen and in the use cases; firestore.rules checks the same things.
 */
object GroupRoles {
    /**
     * The member who runs the group: [Group.adminMemberId], or, in groups from before the role, the
     * member of whoever created it. Null when nobody does (the creator left an old group).
     */
    fun adminOf(group: Group, members: List<Member>): Member? {
        val id = group.adminMemberId
        return if (id != null) {
            members.firstOrNull { it.id == id }
        } else {
            members.firstOrNull { it.userUid == group.createdBy }
        }
    }

    fun isAdmin(group: Group, members: List<Member>, uid: String): Boolean =
        adminOf(group, members)?.userUid == uid

    /** An old group nobody runs: any member may take it over. */
    fun isAdminless(group: Group, members: List<Member>): Boolean = adminOf(group, members) == null

    /** Everything an admin does not need a flag for; for the rest, the flag must not be taken away. */
    fun can(permission: Permission, group: Group, members: List<Member>, uid: String): Boolean =
        isAdmin(group, members, uid) ||
            members.firstOrNull { it.userUid == uid }?.let { permission !in it.deniedPermissions } == true

    /** The admin records any payment; the rest, those in which they pay or receive. */
    fun canSettle(group: Group, members: List<Member>, uid: String, fromMemberId: String, toMemberId: String): Boolean {
        if (isAdmin(group, members, uid)) return true
        val me = members.firstOrNull { it.userUid == uid } ?: return false
        return Permission.RECORD_PAYMENTS !in me.deniedPermissions && me.id in setOf(fromMemberId, toMemberId)
    }

    /** Who gets the group when the account [leavingUid] (its admin) goes: the oldest other member with an account. */
    fun successor(members: List<Member>, leavingUid: String): Member? =
        members.filter { it.active && it.userUid != null && it.userUid != leavingUid }.minByOrNull { it.sortKey }
}
