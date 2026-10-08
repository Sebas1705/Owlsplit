package es.sebas1705.owlsplit.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Invite links the platform opened the app with (Android App Links; iOS Universal Links in P11).
 * The link waits here until the tabs show, so a first launch goes through onboarding first.
 */
object IncomingInvites {
    private val pending = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = pending.asStateFlow()

    fun offer(url: String) {
        pending.value = url
    }

    fun consume() {
        pending.value = null
    }
}
