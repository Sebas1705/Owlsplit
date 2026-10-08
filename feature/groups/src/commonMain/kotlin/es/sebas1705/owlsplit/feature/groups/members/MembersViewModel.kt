package es.sebas1705.owlsplit.feature.groups.members

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.data.models.Member
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
) {
    val canSave: Boolean get() = name.isNotBlank()
}

data class MembersState(
    val loaded: Boolean = false,
    val members: List<Member> = emptyList(),
    val myMemberId: String? = null,
    val form: MemberForm? = null,
) : MVIBaseState

sealed interface MembersIntent : MVIBaseIntent {
    data object Add : MembersIntent
    data class Edit(val member: Member) : MembersIntent
    data class NameChanged(val name: String) : MembersIntent
    data class WeightChanged(val weight: Long) : MembersIntent
    data class ActiveChanged(val active: Boolean) : MembersIntent
    data object Save : MembersIntent
    data object Delete : MembersIntent
    data object Dismiss : MembersIntent
}

class MembersViewModel(
    private val groupId: String,
    observeDetail: ObserveGroupDetail,
    private val actions: MemberActions,
) : MVIBaseViewModel<MembersState, MembersIntent>() {

    private var myUid: String? = null

    override fun initState() = MembersState()

    init {
        viewModelScope.launch {
            observeDetail(groupId).collect { detail ->
                myUid = detail?.myUid
                updateState {
                    it.copy(loaded = true, members = detail?.members.orEmpty(), myMemberId = detail?.myMemberId)
                }
            }
        }
    }

    override fun intentHandler(intent: MembersIntent): Job = execute {
        when (intent) {
            MembersIntent.Add -> updateState { it.copy(form = MemberForm()) }
            is MembersIntent.Edit -> updateState {
                it.copy(
                    form = MemberForm(
                        intent.member.id,
                        intent.member.name,
                        intent.member.defaultWeight,
                        intent.member.active,
                        isMe = intent.member.id == it.myMemberId,
                    ),
                )
            }
            is MembersIntent.NameChanged -> editForm { it.copy(name = intent.name.take(MemberActions.MAX_NAME)) }
            is MembersIntent.WeightChanged ->
                editForm { it.copy(weight = intent.weight.coerceIn(1, MemberActions.MAX_WEIGHT)) }
            is MembersIntent.ActiveChanged -> editForm { if (it.isMe) it else it.copy(active = intent.active) }
            MembersIntent.Save -> save()
            MembersIntent.Delete -> delete()
            MembersIntent.Dismiss -> updateState { it.copy(form = null) }
        }
    }

    private fun editForm(change: (MemberForm) -> MemberForm) = updateState { it.copy(form = it.form?.let(change)) }

    private suspend fun save() {
        val form = currentState.form?.takeIf { it.canSave } ?: return
        updateState { it.copy(form = null) }
        val member = form.memberId?.let { id -> currentState.members.firstOrNull { it.id == id } }
        if (member == null) {
            actions.add(groupId, form.name, form.weight)
        } else {
            actions.update(member, form.name, form.weight)
            if (member.active != form.active) actions.setActive(member, form.active, myUid)
        }
    }

    private suspend fun delete() {
        val form = currentState.form ?: return
        val member = currentState.members.firstOrNull { it.id == form.memberId } ?: return
        if (form.isMe) return
        if (actions.delete(member, myUid)) {
            updateState { it.copy(form = null) }
        } else {
            actions.setActive(member, false, myUid)
            editForm { it.copy(active = false, inUse = true) }
        }
    }
}
