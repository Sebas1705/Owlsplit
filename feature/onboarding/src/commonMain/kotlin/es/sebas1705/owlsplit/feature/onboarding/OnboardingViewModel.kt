package es.sebas1705.owlsplit.feature.onboarding

import es.sebas1705.owlsplit.core.common.mvi.MVIBaseIntent
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseState
import es.sebas1705.owlsplit.core.common.mvi.MVIBaseViewModel
import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.ui.theme.GroupAccent
import es.sebas1705.owlsplit.core.ui.theme.avatarAccent
import es.sebas1705.owlsplit.core.ui.theme.toAvatar
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.domain.usecases.account.CompleteOnboarding
import es.sebas1705.owlsplit.domain.usecases.account.GoogleOutcome
import es.sebas1705.owlsplit.domain.usecases.account.SignInWithGoogle
import kotlinx.coroutines.Job

enum class OnboardingStep { WELCOME, PROFILE, START }

/** What the user wants to do first; P9/P10 open the matching screen. */
enum class StartChoice { CREATE_GROUP, JOIN_GROUP, LATER }

enum class OnboardingMessage { GOOGLE_FAILED, OFFLINE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val accent: GroupAccent = GroupAccent.BLUE,
    val googleAvailable: Boolean = false,
    val busy: Boolean = false,
    /** Finishing waits for the first session, which needs the network once. */
    val waitingForNetwork: Boolean = false,
    val message: OnboardingMessage? = null,
    val finished: StartChoice? = null,
) : MVIBaseState

sealed interface OnboardingIntent : MVIBaseIntent {
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent
    data class NameChanged(val name: String) : OnboardingIntent
    data class AccentChanged(val accent: GroupAccent) : OnboardingIntent
    data object UseExistingAccount : OnboardingIntent
    data object DismissMessage : OnboardingIntent

    /** Ends onboarding; [fallbackName] (localised "Me") is used when the name was left empty. */
    data class Finish(val choice: StartChoice, val fallbackName: String) : OnboardingIntent
}

/** Welcome → name and avatar → first group. Every step can be skipped. */
class OnboardingViewModel(
    private val completeOnboarding: CompleteOnboarding,
    private val signInWithGoogle: SignInWithGoogle,
) : MVIBaseViewModel<OnboardingState, OnboardingIntent>() {

    override fun initState() = OnboardingState(googleAvailable = signInWithGoogle.isAvailable)

    override fun intentHandler(intent: OnboardingIntent): Job = execute {
        when (intent) {
            OnboardingIntent.Next -> updateState { it.copy(step = it.step.next()) }
            OnboardingIntent.Back -> updateState { it.copy(step = it.step.previous()) }
            is OnboardingIntent.NameChanged -> updateState { it.copy(name = intent.name.take(MAX_NAME)) }
            is OnboardingIntent.AccentChanged -> updateState { it.copy(accent = intent.accent) }
            OnboardingIntent.UseExistingAccount -> useExistingAccount()
            OnboardingIntent.DismissMessage -> updateState { it.copy(message = null) }
            is OnboardingIntent.Finish -> finish(intent.choice, intent.fallbackName)
        }
    }

    private suspend fun useExistingAccount() {
        updateState { it.copy(busy = true, message = null) }
        val outcome = signInWithGoogle()
        updateState { state ->
            when (outcome) {
                GoogleOutcome.Cancelled -> state.copy(busy = false)
                is GoogleOutcome.Done -> state.copy(
                    busy = false,
                    step = OnboardingStep.PROFILE,
                    name = outcome.restored?.name ?: state.name,
                    accent = outcome.restored?.let { avatarAccent(it.avatar) } ?: state.accent,
                )
                is GoogleOutcome.Failed -> state.copy(
                    busy = false,
                    message = if (outcome.result == AccountResult.Offline) {
                        OnboardingMessage.OFFLINE
                    } else {
                        OnboardingMessage.GOOGLE_FAILED
                    },
                )
            }
        }
    }

    private suspend fun finish(choice: StartChoice, fallbackName: String) {
        if (currentState.busy) return
        updateState { it.copy(busy = true) }
        val name = currentState.name.trim().ifEmpty { fallbackName }
        val profile = UserProfile(name, currentState.accent.toAvatar(), AppPreferences.DEFAULT_CURRENCY)
        completeOnboarding(profile, onWaiting = { updateState { it.copy(waitingForNetwork = true) } })
        updateState { it.copy(busy = false, waitingForNetwork = false, finished = choice) }
    }

    private companion object {
        const val MAX_NAME = 40
    }
}

private fun OnboardingStep.next() = OnboardingStep.entries.getOrElse(ordinal + 1) { this }

private fun OnboardingStep.previous() = OnboardingStep.entries.getOrElse(ordinal - 1) { this }
