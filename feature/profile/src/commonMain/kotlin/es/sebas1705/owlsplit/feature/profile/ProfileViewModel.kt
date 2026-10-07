package es.sebas1705.owlsplit.feature.profile

import androidx.lifecycle.viewModelScope
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.ThemeMode
import es.sebas1705.owlsplit.core.datastore.ThemeSettings
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.avatarAccent
import es.sebas1705.owlsplit.core.ui.theme.toAvatar
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.DeleteAccount
import es.sebas1705.owlsplit.domain.usecases.account.GoogleOutcome
import es.sebas1705.owlsplit.domain.usecases.account.LinkGoogle
import es.sebas1705.owlsplit.domain.usecases.account.ObserveAccount
import es.sebas1705.owlsplit.domain.usecases.account.ReauthenticateGoogle
import es.sebas1705.owlsplit.domain.usecases.account.SaveProfile
import es.sebas1705.owlsplit.domain.usecases.account.SignOut
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class ProfileMessage { LINKED, CREDENTIAL_IN_USE, OFFLINE, FAILED, SAVED }

/** A confirmation sheet on top of the screen. */
enum class ProfileDialog { CONFIRM_DELETE, REAUTHENTICATE }

data class ProfileState(
    val loaded: Boolean = false,
    val isAnonymous: Boolean = true,
    val linkedGoogle: Boolean = false,
    val canLink: Boolean = false,
    /** What is saved, to know whether the edit fields changed. */
    val saved: UserProfile? = null,
    val name: String = "",
    val accent: GroupAccent = GroupAccent.BLUE,
    val currency: String = AppPreferences.DEFAULT_CURRENCY,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val busy: Boolean = false,
    val dialog: ProfileDialog? = null,
    val message: ProfileMessage? = null,
) : MVIBaseState {
    val dirty: Boolean
        get() = saved != null && (name.trim() != saved.name || accent.toAvatar() != saved.avatar)
    val nameValid: Boolean get() = name.isNotBlank()
}

sealed interface ProfileIntent : MVIBaseIntent {
    data class NameChanged(val name: String) : ProfileIntent
    data class AccentChanged(val accent: GroupAccent) : ProfileIntent
    data object Save : ProfileIntent
    data class CurrencyChanged(val code: String) : ProfileIntent
    data class ThemeChanged(val mode: ThemeMode) : ProfileIntent
    data object Link : ProfileIntent
    data object SignOut : ProfileIntent
    data object AskDelete : ProfileIntent
    data object ConfirmDelete : ProfileIntent
    data object Reauthenticate : ProfileIntent
    data object DismissDialog : ProfileIntent
    data object DismissMessage : ProfileIntent
}

/** Account and preferences. Signing out and deleting reset the device: the app returns to onboarding. */
@Suppress("LongParameterList") // One use case per action keeps each one testable on its own.
class ProfileViewModel(
    observeAccount: ObserveAccount,
    private val themes: ThemeSettings,
    private val saveProfile: SaveProfile,
    private val linkGoogle: LinkGoogle,
    private val signOut: SignOut,
    private val deleteAccount: DeleteAccount,
    private val reauthenticate: ReauthenticateGoogle,
) : MVIBaseViewModel<ProfileState, ProfileIntent>() {

    override fun initState() = ProfileState()

    init {
        viewModelScope.launch {
            combine(observeAccount(), themes.themeMode, ::Pair).collect { (account, theme) ->
                val profile = account.profile
                val session = account.session
                updateState { state ->
                    // Keep what the user is typing; refresh it only when nothing was edited.
                    val untouched = !state.dirty
                    state.copy(
                        loaded = true,
                        isAnonymous = session?.isAnonymous ?: true,
                        linkedGoogle = session?.providers?.contains(GOOGLE) == true,
                        canLink = linkGoogle.isAvailable,
                        saved = profile,
                        name = if (untouched) profile?.name.orEmpty() else state.name,
                        accent = if (untouched) avatarAccent(profile?.avatar) else state.accent,
                        currency = profile?.currency ?: state.currency,
                        theme = theme,
                    )
                }
            }
        }
    }

    override fun intentHandler(intent: ProfileIntent): Job = execute {
        when (intent) {
            is ProfileIntent.NameChanged -> updateState { it.copy(name = intent.name.take(MAX_NAME)) }
            is ProfileIntent.AccentChanged -> updateState { it.copy(accent = intent.accent) }
            ProfileIntent.Save -> save()
            is ProfileIntent.CurrencyChanged -> currentState.saved?.let { saveProfile(it.copy(currency = intent.code)) }
            is ProfileIntent.ThemeChanged -> themes.setThemeMode(intent.mode)
            ProfileIntent.Link -> link()
            ProfileIntent.SignOut -> busy { report(signOut()) }
            ProfileIntent.AskDelete -> updateState { it.copy(dialog = ProfileDialog.CONFIRM_DELETE) }
            ProfileIntent.ConfirmDelete -> delete()
            ProfileIntent.Reauthenticate -> reauthenticateAndDelete()
            ProfileIntent.DismissDialog -> updateState { it.copy(dialog = null) }
            ProfileIntent.DismissMessage -> updateState { it.copy(message = null) }
        }
    }

    private suspend fun save() {
        val state = currentState
        val saved = state.saved ?: return
        if (!state.nameValid) return
        saveProfile(saved.copy(name = state.name.trim(), avatar = state.accent.toAvatar()))
        updateState { it.copy(message = ProfileMessage.SAVED) }
    }

    private suspend fun link() = busy {
        when (val outcome = linkGoogle()) {
            GoogleOutcome.Cancelled -> Unit
            is GoogleOutcome.Done -> updateState { it.copy(message = ProfileMessage.LINKED) }
            is GoogleOutcome.Failed -> report(outcome.result)
        }
    }

    private suspend fun delete() = busy {
        updateState { it.copy(dialog = null) }
        when (val result = deleteAccount()) {
            AccountResult.RecentLoginRequired -> updateState { it.copy(dialog = ProfileDialog.REAUTHENTICATE) }
            else -> report(result)
        }
    }

    private suspend fun reauthenticateAndDelete() {
        updateState { it.copy(dialog = null) }
        when (val outcome = reauthenticate()) {
            GoogleOutcome.Cancelled -> Unit
            is GoogleOutcome.Done -> delete()
            is GoogleOutcome.Failed -> report(outcome.result)
        }
    }

    private fun report(result: AccountResult) {
        val message = when (result) {
            AccountResult.Done -> null
            AccountResult.CredentialInUse -> ProfileMessage.CREDENTIAL_IN_USE
            AccountResult.Offline -> ProfileMessage.OFFLINE
            AccountResult.RecentLoginRequired, is AccountResult.Failed -> ProfileMessage.FAILED
        }
        updateState { it.copy(message = message) }
    }

    private suspend fun busy(block: suspend () -> Unit) {
        updateState { it.copy(busy = true, message = null) }
        try {
            block()
        } finally {
            updateState { it.copy(busy = false) }
        }
    }

    private companion object {
        const val MAX_NAME = 40
        const val GOOGLE = "google.com"
    }
}
