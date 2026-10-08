package es.sebas1705.owlsplit.feature.invite

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.models.PreviewResult
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.invite.ClaimMember
import es.sebas1705.owlsplit.domain.usecases.invite.InviteCodes
import es.sebas1705.owlsplit.domain.usecases.invite.JoinGroup
import es.sebas1705.owlsplit.domain.usecases.invite.JoinResult
import es.sebas1705.owlsplit.domain.usecases.invite.PreviewInvite
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

enum class JoinStep { ENTER, SEARCHING, PREVIEW, JOINING, PICK, DONE }

enum class JoinError { NOT_FOUND, OFFLINE, REJECTED }

/** A member nobody has claimed yet: whoever joins can become them. */
data class Ghost(val id: String, val name: String)

data class JoinState(
    val code: String = "",
    val step: JoinStep = JoinStep.ENTER,
    val error: JoinError? = null,
    val preview: InvitePreview? = null,
    /** Null while the group is still arriving from the server. */
    val ghosts: List<Ghost>? = null,
    val myName: String = "",
    val joinedGroupId: String? = null,
) : MVIBaseState {
    val canSearch: Boolean get() = InviteCodes.isValid(InviteCodes.normalize(code)) && step == JoinStep.ENTER
}

sealed interface JoinIntent : MVIBaseIntent {
    data class CodeChanged(val text: String) : JoinIntent
    data object Search : JoinIntent

    /** What the camera read: a link or a code. */
    data class Scanned(val text: String) : JoinIntent
    data object Join : JoinIntent
    data object Back : JoinIntent

    /** Null: a new member with my profile. */
    data class Pick(val memberId: String?) : JoinIntent
}

/** Typing (or opening) a code, seeing the group, joining it and choosing who I am in it. */
class JoinViewModel(
    initialCode: String?,
    private val previewInvite: PreviewInvite,
    private val joinGroup: JoinGroup,
    private val claimMember: ClaimMember,
    private val observeDetail: ObserveGroupDetail,
    private val observeAccount: ObserveAccount,
) : MVIBaseViewModel<JoinState, JoinIntent>() {

    override fun initState() = JoinState()

    init {
        execute {
            val name = observeAccount().first().profile?.name.orEmpty()
            updateState { it.copy(myName = name) }
        }
        if (!initialCode.isNullOrBlank()) {
            execute {
                updateState { it.copy(code = InviteCodes.display(InviteCodes.normalize(initialCode))) }
                search()
            }
        }
    }

    override fun intentHandler(intent: JoinIntent): Job = execute {
        when (intent) {
            is JoinIntent.CodeChanged -> updateState {
                if (it.step == JoinStep.ENTER) it.copy(code = intent.text, error = null) else it
            }
            JoinIntent.Search -> if (uiState.value.canSearch) search()
            is JoinIntent.Scanned -> scanned(intent.text)
            JoinIntent.Back -> updateState { it.copy(step = JoinStep.ENTER, preview = null, error = null) }
            JoinIntent.Join -> join()
            is JoinIntent.Pick -> pick(intent.memberId)
        }
    }

    private suspend fun scanned(text: String) {
        if (uiState.value.step != JoinStep.ENTER) return
        updateState { it.copy(code = InviteCodes.display(InviteCodes.normalize(text)), error = null) }
        if (uiState.value.canSearch) search() else updateState { it.copy(error = JoinError.NOT_FOUND) }
    }

    private suspend fun search() {
        updateState { it.copy(step = JoinStep.SEARCHING, error = null) }
        val result = previewInvite(uiState.value.code)
        updateState {
            when (result) {
                is PreviewResult.Found -> it.copy(step = JoinStep.PREVIEW, preview = result.preview)
                PreviewResult.NotFound -> it.copy(step = JoinStep.ENTER, error = JoinError.NOT_FOUND)
                PreviewResult.Offline -> it.copy(step = JoinStep.ENTER, error = JoinError.OFFLINE)
            }
        }
    }

    private suspend fun join() {
        val preview = uiState.value.preview ?: return
        updateState { it.copy(step = JoinStep.JOINING, error = null) }
        when (joinGroup(preview)) {
            is JoinResult.Joined -> {
                updateState { it.copy(step = JoinStep.PICK) }
                awaitMembers(preview.groupId)
            }
            JoinResult.Offline -> updateState { it.copy(step = JoinStep.PREVIEW, error = JoinError.OFFLINE) }
            JoinResult.Rejected -> updateState { it.copy(step = JoinStep.PREVIEW, error = JoinError.REJECTED) }
        }
    }

    /** Once the members are here: done if I already am one (rejoining), otherwise choose. */
    private suspend fun awaitMembers(groupId: String) {
        val detail = observeDetail(groupId).first { it != null && it.members.isNotEmpty() } ?: return
        if (detail.myMemberId != null) {
            updateState { it.copy(step = JoinStep.DONE, joinedGroupId = groupId) }
            return
        }
        val ghosts = detail.members.filter { it.active && it.userUid == null }.map { Ghost(it.id, it.name) }
        updateState { it.copy(ghosts = ghosts) }
    }

    private suspend fun pick(memberId: String?) {
        val groupId = uiState.value.preview?.groupId ?: return
        if (uiState.value.step != JoinStep.PICK) return
        claimMember(groupId, memberId)
        updateState { it.copy(step = JoinStep.DONE, joinedGroupId = groupId) }
    }
}
