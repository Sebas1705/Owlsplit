package es.sebas1705.owlsplit.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the home-screen widget or a launcher shortcut asked for. */
enum class Shortcut { ADD_EXPENSE }

/**
 * Shortcuts the platform opened the app with (the Android widget and launcher shortcut). Like
 * [IncomingInvites], it waits here until the tabs show, so a first launch goes through onboarding.
 */
object IncomingShortcuts {
    private val pending = MutableStateFlow<Shortcut?>(null)
    val shortcut: StateFlow<Shortcut?> = pending.asStateFlow()

    fun offer(shortcut: Shortcut) {
        pending.value = shortcut
    }

    fun consume() {
        pending.value = null
    }
}
