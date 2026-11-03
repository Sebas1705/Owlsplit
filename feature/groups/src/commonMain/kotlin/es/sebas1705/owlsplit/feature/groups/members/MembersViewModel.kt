package es.sebas1705.owlsplit.feature.groups.members

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.data.models.MemberMerge
import es.sebas1705.owlsplit.data.models.Permission
import es.sebas1705.owlsplit.domain.usecases.groups.GroupAdmin
import es.sebas1705.owlsplit.domain.usecases.groups.MemberActions
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The member sheet: adding ([memberId] null) or editing one. */
data class MemberForm(
    val memberId: String? = null,
    val name: String = "",
    val weight: Long = 1,
    val active: Boolean = true,
    /** Mine: cannot be deactivated or deleted. */
    val isMe: Boolean = false,
    /** Deleting failed because it has movements (it was deactivated instead). */
    val inUse: Boolean = false,
    /** What the admin took away from this member. */
    val denied: Set<Permission> = emptySet(),
    /** Has an account: the group can be handed over to them. */
    val hasAccount: Boolean = false,
    /** Is the admin: cannot be restricted, merged away or deleted. */
    val isAdmin: Boolean = false,
    /** Only the name can be changed (not the admin, editing my own row). */
    val nameOnly: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

/** Merging [absorbed] into somebody: [target] is null while choosing. */
data class MergeDraft(val absorbed: Member, val target: Member? = null)

data class MembersState(
    val loaded: Boolean = false,
    val members: List<Member> = emptyList(),
    val myMemberId: String? = null,
    val form: MemberForm? = null,
    val isAdmin: Boolean = false,
    val adminId: String? = null,
    /** An old group nobody runs: any member can take it over. */
    val adminless: Boolean = false,
    val merge: MergeDraft? = null,
) : MVIBaseState {
    /** Who the member being merged can be merged into: never two people with an account. */
    val mergeCandidates: List<Member>
        get() = merge?.absorbed?.let { absorbed -> members.filter { MemberMerge.plan(absorbed, it) != null } }.orEmpty()
}

sealed interface MembersIntent : MVIBaseIntent {
    data object Add : MembersIntent
    data class Edit(val member: Member) : MembersIntent
    data class NameChanged(val name: String) : MembersIntent
    data class WeightChanged(val weight: Long) : MembersIntent
    data class ActiveChanged(val active: Boolean) : MembersIntent
    data class PermissionChanged(val permission: Permission, val allowed: Boolean) : MembersIntent
    data object MakeAdmin : MembersIntent
    data object TakeOver : MembersIntent
    data object StartMerge : MembersIntent
    data class MergeInto(val target: Member) : MembersIntent
    data object ConfirmMerge : MembersIntent
    data object CancelMerge : MembersIntent
    data object Save : MembersIntent
    data object Delete : MembersIntent
    data object Dismiss : MembersIntent
}

class MembersViewModel(
    private val groupId: String,
    observeDetail: ObserveGroupDetail,
    private val actions: MemberActions,
    private val admin: GroupAdmin,
) : MVIBaseViewModel<MembersState, MembersIntent>() {

    private var myUid: String? = null

    override fun initState() = MembersState()

    init {
        viewModelScope.launch {
            observeDetail(groupId).collect { detail ->
                myUid = detail?.myUid
                updateState {
                    it.copy(
                        loaded = true,
                        members = detail?.members.orEmpty(),
                        myMemberId = detail?.myMemberId,
                        isAdmin = detail?.isAdmin == true,
                        adminId = detail?.admin?.id,
                        adminless = detail?.adminless == true,
                    )
                }
            }
        }
    }

    override fun intentHandler(intent: MembersIntent): Job = execute {
        when (intent) {
            MembersIntent.Add -> if (currentState.isAdmin) updateState { it.copy(form = MemberForm()) }
            is MembersIntent.Edit -> edit(intent.member)
            is MembersIntent.NameChanged -> editForm { it.copy(name = intent.name.take(MemberActions.MAX_NAME)) }
            is MembersIntent.WeightChanged ->
                editForm {
                    if (it.nameOnly) {
                        it
                    } else {
                        it.copy(
                            weight = intent.weight.coerceIn(1, MemberActions.MAX_WEIGHT)
                        )
                    }
                }
            is MembersIntent.ActiveChanged ->
                editForm { if (it.isMe || it.nameOnly) it else it.copy(active = intent.active) }
            is MembersIntent.PermissionChanged -> setPermission(intent.permission, intent.allowed)
            MembersIntent.MakeAdmin -> makeAdmin()
            MembersIntent.TakeOver -> { admin.takeOver(groupId) }
            MembersIntent.StartMerge -> startMerge()
            is MembersIntent.MergeInto -> updateState { s -> s.copy(merge = s.merge?.copy(target = intent.target)) }
            MembersIntent.ConfirmMerge -> confirmMerge()
            MembersIntent.CancelMerge -> updateState { it.copy(merge = null) }
            MembersIntent.Save -> save()
            MembersIntent.Delete -> delete()
            MembersIntent.Dismiss -> updateState { it.copy(form = null) }
        }
    }

    private fun edit(member: Member) = updateState {
        val isMe = member.id == it.myMemberId
        // Only the admin edits people; anyone else can rename their own row.
        if (!it.isAdmin && !isMe) return@updateState it
        it.copy(
            form = MemberForm(
                member.id,
                member.name,
                member.defaultWeight,
                member.active,
                isMe = isMe,
                denied = member.deniedPermissions,
                hasAccount = member.userUid != null,
                isAdmin = member.id == it.adminId,
                nameOnly = !it.isAdmin,
            ),
        )
    }

    private fun editForm(change: (MemberForm) -> MemberForm) = updateState { it.copy(form = it.form?.let(change)) }

    private suspend fun setPermission(permission: Permission, allowed: Boolean) {
        val form = currentState.form?.takeIf { it.memberId != null && !it.isAdmin } ?: return
        if (admin.setPermission(groupId, form.memberId!!, permission, allowed)) {
            editForm { it.copy(denied = if (allowed) it.denied - permission else it.denied + permission) }
        }
    }

    private suspend fun makeAdmin() {
        val form = currentState.form?.takeIf { it.memberId != null && it.hasAccount && !it.isAdmin } ?: return
        if (admin.makeAdmin(groupId, form.memberId!!)) updateState { it.copy(form = null) }
    }

    private fun startMerge() = updateState { s ->
        val member = s.form?.memberId?.let { id -> s.members.firstOrNull { it.id == id } } ?: return@updateState s
        s.copy(form = null, merge = MergeDraft(member))
    }

    private suspend fun confirmMerge() {
        val draft = currentState.merge ?: return
        val target = draft.target ?: return
        updateState { it.copy(merge = null) }
        admin.merge(groupId, draft.absorbed.id, target.id)
    }

    private suspend fun save() {
        val form = currentState.form?.takeIf { it.canSave } ?: return
        updateState { it.copy(form = null) }
        val member = form.memberId?.let { id -> currentState.members.firstOrNull { it.id == id } }
        if (member == null) {
            actions.add(groupId, form.name, form.weight)
        } else {
            actions.update(member, form.name, form.weight)
            if (member.active != form.active && !form.nameOnly) actions.setActive(member, form.active, myUid)
        }
    }

    private suspend fun delete() {
        val form = currentState.form?.takeIf { !it.isMe && !it.isAdmin && !it.nameOnly } ?: return
        val member = currentState.members.firstOrNull { it.id == form.memberId } ?: return
        if (actions.delete(member, myUid)) {
            updateState { it.copy(form = null) }
        } else {
            actions.setActive(member, false, myUid)
            editForm { it.copy(active = false, inUse = true) }
        }
    }
}
