package es.sebas1705.owlsplit.feature.groups.transfer

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.money.Currency
import es.sebas1705.owlsplit.core.ui.format.parseDecimal
import es.sebas1705.owlsplit.data.models.Member
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import es.sebas1705.owlsplit.domain.usecases.groups.RecordTransfer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

data class TransferState(
    val loaded: Boolean = false,
    val currency: String = "EUR",
    val members: List<Member> = emptyList(),
    val myMemberId: String? = null,
    val fromId: String? = null,
    val toId: String? = null,
    val amountText: String = "",
    val amountMinor: Long = 0,
    val note: String = "",
    /** Asking "has it really been paid?" before recording. */
    val confirming: Boolean = false,
    val done: Boolean = false,
) : MVIBaseState {
    val sameMember: Boolean get() = fromId != null && fromId == toId
    val canSave: Boolean get() = fromId != null && toId != null && !sameMember && amountMinor > 0
}

sealed interface TransferIntent : MVIBaseIntent {
    data class FromChanged(val memberId: String) : TransferIntent
    data class ToChanged(val memberId: String) : TransferIntent
    data class AmountChanged(val text: String) : TransferIntent
    data class NoteChanged(val note: String) : TransferIntent
    data object Save : TransferIntent
    data object ConfirmSave : TransferIntent
    data object DismissConfirm : TransferIntent
}

/** "Ana pagó 23,50 € a Luis": a payment typed by hand, in the group currency. */
class TransferViewModel(
    private val groupId: String,
    private val observeDetail: ObserveGroupDetail,
    private val recordTransfer: RecordTransfer,
) : MVIBaseViewModel<TransferState, TransferIntent>() {

    override fun initState() = TransferState()

    init {
        execute {
            val detail = observeDetail(groupId).first() ?: return@execute
            val active = detail.members.filter { it.active }
            val from = detail.myMemberId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
            updateState {
                it.copy(
                    loaded = true,
                    currency = detail.group.baseCurrency,
                    members = active,
                    myMemberId = detail.myMemberId,
                    fromId = from,
                    toId = active.firstOrNull { m -> m.id != from }?.id,
                )
            }
        }
    }

    override fun intentHandler(intent: TransferIntent): Job = execute {
        when (intent) {
            is TransferIntent.FromChanged -> updateState { it.copy(fromId = intent.memberId) }
            is TransferIntent.ToChanged -> updateState { it.copy(toId = intent.memberId) }
            is TransferIntent.AmountChanged -> updateState { state ->
                val minor = parseDecimal(intent.text, Currency.of(state.currency).decimals) ?: return@updateState state
                state.copy(amountText = intent.text, amountMinor = minor)
            }
            is TransferIntent.NoteChanged -> updateState { it.copy(note = intent.note.take(RecordTransfer.MAX_NOTE)) }
            TransferIntent.Save -> updateState { it.copy(confirming = it.canSave) }
            TransferIntent.ConfirmSave -> {
                updateState { it.copy(confirming = false) }
                save()
            }
            TransferIntent.DismissConfirm -> updateState { it.copy(confirming = false) }
        }
    }

    private suspend fun save() {
        val state = currentState
        if (!state.canSave) return
        if (recordTransfer(groupId, state.fromId!!, state.toId!!, state.amountMinor, state.currency, state.note)) {
            updateState { it.copy(done = true) }
        }
    }
}
