package es.sebas1705.owlsplit.feature.groups.editor

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.GroupIcons
import es.sebas1705.owlsplit.data.models.GroupInput
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.groups.GroupActions
import es.sebas1705.owlsplit.domain.usecases.groups.ObserveGroupDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

data class GroupEditorState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val name: String = "",
    val icon: String = GroupIcons.all.first().first,
    val accent: GroupAccent = GroupAccent.BLUE,
    val currency: String = "EUR",
    val saving: Boolean = false,
    /** Set once saved: the screen leaves (to the new group when creating). */
    val savedGroupId: String? = null,
) : MVIBaseState {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

sealed interface GroupEditorIntent : MVIBaseIntent {
    data class NameChanged(val name: String) : GroupEditorIntent
    data class IconChanged(val icon: String) : GroupEditorIntent
    data class AccentChanged(val accent: GroupAccent) : GroupEditorIntent
    data class CurrencyChanged(val currency: String) : GroupEditorIntent
    data object Save : GroupEditorIntent
}

/** Creates a group ([groupId] null) or edits one. New groups start in the user's preferred currency. */
class GroupEditorViewModel(
    private val groupId: String?,
    private val actions: GroupActions,
    private val observeDetail: ObserveGroupDetail,
    private val observeAccount: ObserveAccount,
) : MVIBaseViewModel<GroupEditorState, GroupEditorIntent>() {

    override fun initState() = GroupEditorState(editing = groupId != null)

    init {
        execute {
            val loaded = if (groupId == null) {
                val currency = observeAccount().first().profile?.currency
                currentState.copy(currency = currency ?: currentState.currency)
            } else {
                val group = observeDetail(groupId).first()?.group
                group?.let {
                    currentState.copy(
                        name = it.name,
                        icon = it.icon,
                        accent = GroupAccent.entries.firstOrNull { a -> a.name == it.accent } ?: GroupAccent.BLUE,
                        currency = it.baseCurrency,
                    )
                } ?: currentState
            }
            updateState { loaded.copy(loaded = true) }
        }
    }

    override fun intentHandler(intent: GroupEditorIntent): Job = execute {
        when (intent) {
            is GroupEditorIntent.NameChanged ->
                updateState { it.copy(name = intent.name.take(GroupActions.MAX_NAME)) }
            is GroupEditorIntent.IconChanged -> updateState { it.copy(icon = intent.icon) }
            is GroupEditorIntent.AccentChanged -> updateState { it.copy(accent = intent.accent) }
            is GroupEditorIntent.CurrencyChanged -> updateState { it.copy(currency = intent.currency) }
            GroupEditorIntent.Save -> save()
        }
    }

    private suspend fun save() {
        val state = currentState
        if (!state.canSave) return
        updateState { it.copy(saving = true) }
        val input = GroupInput(state.name, state.icon, state.accent.name, state.currency)
        val id = groupId?.also { actions.update(it, input) } ?: actions.create(input)
        updateState { it.copy(saving = false, savedGroupId = id) }
    }
}
