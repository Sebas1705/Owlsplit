package es.sebas1705.owlsplit.domain.usecases.groups

import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.GroupRoles
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.Permission
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.account.AccountService
import kotlinx.coroutines.flow.first

/**
 * What only the admin of a group does (docs/designs/2026-11-03-group-admin.md): handing the group
 * over and choosing what each member may do. Every call returns false, changing nothing, when the
 * caller is not allowed to (the rules refuse it on the server as well).
 */
class GroupAdmin(
    private val groups: GroupRepository,
    private val members: MemberRepository,
    private val accounts: AccountService,
) {
    /** Allows or denies [permission] to [memberId]. The admin cannot be restricted. */
    suspend fun setPermission(groupId: String, memberId: String, permission: Permission, allowed: Boolean): Boolean {
        val (group, list, uid) = load(groupId) ?: return false
        val target = list.firstOrNull { it.id == memberId } ?: return false
        if (!GroupRoles.isAdmin(group, list, uid) || target.id == GroupRoles.adminOf(group, list)?.id) return false
        val denied = if (allowed) target.deniedPermissions - permission else target.deniedPermissions + permission
        if (denied != target.deniedPermissions) members.setDeniedPermissions(memberId, denied)
        return true
    }

    /** Gives the group to [memberId], who must have an account. Only the admin can. */
    suspend fun makeAdmin(groupId: String, memberId: String): Boolean {
        val (group, list, uid) = load(groupId) ?: return false
        val target = list.firstOrNull { it.id == memberId } ?: return false
        if (!GroupRoles.isAdmin(group, list, uid) || target.userUid == null || !target.active) return false
        groups.setAdmin(groupId, memberId)
        return true
    }

    /** An old group nobody runs anymore: any member takes it over. */
    suspend fun takeOver(groupId: String): Boolean {
        val (group, list, uid) = load(groupId) ?: return false
        val me = list.firstOrNull { it.userUid == uid } ?: return false
        if (!GroupRoles.isAdminless(group, list)) return false
        groups.setAdmin(groupId, me.id)
        return true
    }

    /**
     * Merges [absorbedId] into [intoId] (the one with an account is kept; see [MemberMerge]). Returns the
     * kept member, or null when the caller is not the admin or the two cannot be merged.
     */
    suspend fun merge(groupId: String, absorbedId: String, intoId: String): String? {
        val (group, list, uid) = load(groupId) ?: return null
        if (!GroupRoles.isAdmin(group, list, uid)) return null
        if (list.none { it.id == absorbedId } || list.none { it.id == intoId }) return null
        return members.merge(absorbedId, intoId)
    }

    private data class Loaded(
        val group: Group,
        val members: List<Member>,
        val uid: String,
    )

    private suspend fun load(groupId: String): Loaded? {
        val uid = accounts.session.first()?.uid ?: return null
        val group = groups.observeGroup(groupId).first() ?: return null
        return Loaded(group, members.observeMembers(groupId).first(), uid)
    }
}
