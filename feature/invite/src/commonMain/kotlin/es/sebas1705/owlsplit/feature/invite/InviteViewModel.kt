package es.sebas1705.owlsplit.feature.invite

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.invite.InviteLink
import es.sebas1705.owlsplit.domain.usecases.invite.InviteResult
import es.sebas1705.owlsplit.domain.usecases.invite.RegenerateInvite
import es.sebas1705.owlsplit.domain.usecases.invite.ShareInvite
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

enum class InviteStatus { PREPARING, READY, OFFLINE, FAILED }

data class InviteState(
    val status: InviteStatus = InviteStatus.PREPARING,
    val groupName: String = "",
    val link: InviteLink? = null,
    val confirmRegenerate: Boolean = false,
) : MVIBaseState

sealed interface InviteIntent : MVIBaseIntent {
    data object Retry : InviteIntent
    data object AskRegenerate : InviteIntent
    data object ConfirmRegenerate : InviteIntent
    data object DismissDialog : InviteIntent
}

/** The group's link and code, published on the server when the screen opens. */
class InviteViewModel(
    private val groupId: String,
    private val observeDetail: ObserveGroupDetail,
    private val shareInvite: ShareInvite,
    private val regenerateInvite: RegenerateInvite,
) : MVIBaseViewModel<InviteState, InviteIntent>() {

    override fun initState() = InviteState()

    init {
        execute {
            val name = observeDetail(groupId).first()?.group?.name.orEmpty()
            updateState { it.copy(groupName = name) }
            prepare { shareInvite(groupId) }
        }
    }

    override fun intentHandler(intent: InviteIntent): Job = execute {
        when (intent) {
            InviteIntent.Retry -> prepare { shareInvite(groupId) }
            InviteIntent.AskRegenerate -> updateState { it.copy(confirmRegenerate = true) }
            InviteIntent.DismissDialog -> updateState { it.copy(confirmRegenerate = false) }
            InviteIntent.ConfirmRegenerate -> {
                updateState { it.copy(confirmRegenerate = false) }
                prepare { regenerateInvite(groupId) }
            }
        }
    }

    private suspend fun prepare(block: suspend () -> InviteResult) {
        updateState { it.copy(status = InviteStatus.PREPARING) }
        val result = block()
        updateState {
            when (result) {
                is InviteResult.Ready -> it.copy(status = InviteStatus.READY, link = result.link)
                InviteResult.Offline -> it.copy(status = InviteStatus.OFFLINE)
                is InviteResult.Failed -> it.copy(status = InviteStatus.FAILED)
            }
        }
    }
}
