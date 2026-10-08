package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.repository.GroupRepository
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.account.ProfileRemote
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Wipes this device back to a fresh install: local database and preferences. */
fun interface DeviceReset {
    suspend fun reset()
}

/**
 * Deletes the account (docs/FIREBASE.md, "Borrado de cuenta"): my members become ghosts and I leave
 * every group, then the profile and the user go, then this device is reset. Every step can be
 * repeated, so after [AccountResult.Offline] or [AccountResult.RecentLoginRequired] (sign in with
 * Google again, then retry) it continues where it stopped.
 */
class DeleteAccount(
    private val groups: GroupRepository,
    private val sync: SyncControl,
    private val accounts: AccountService,
    private val profiles: ProfileRemote,
    private val device: DeviceReset,
) {
    suspend operator fun invoke(): AccountResult {
        val session = accounts.currentSession() ?: return AccountResult.Failed("No session")
        // Pending writes first: once out of a group they could no longer be uploaded.
        if (!sync.flush()) return AccountResult.Offline
        groups.observeGroups().first()
            .filter { session.uid in it.memberUids && !sync.isAccessLost(it.id) }
            .forEach { groups.leave(it.id) }
        if (!sync.flush()) return AccountResult.Offline
        profiles.delete(session.uid).let { if (it != AccountResult.Done) return it }
        accounts.deleteUser().let { if (it != AccountResult.Done) return it }
        device.reset()
        return AccountResult.Done
    }
}

/** Signing out is only offered to linked accounts: an anonymous one could never come back. */
class SignOut(
    private val sync: SyncControl,
    private val accounts: AccountService,
    private val device: DeviceReset,
) {
    suspend operator fun invoke(): AccountResult {
        val session = accounts.currentSession() ?: return AccountResult.Done
        if (session.isAnonymous) return AccountResult.Failed("Anonymous accounts cannot sign out")
        // What is not uploaded would be lost with the local database.
        if (!sync.flush()) return AccountResult.Offline
        accounts.signOut()
        device.reset()
        return AccountResult.Done
    }
}

/**
 * "If you lose your phone, you lose your groups": suggested to anonymous users once they have a
 * group (the spec also says "or the fifth expense", but expenses need a group), until dismissed.
 */
object ShouldSuggestLinking {
    operator fun invoke(session: Session?, groupCount: Int, dismissed: Boolean): Boolean =
        session != null && session.isAnonymous && !dismissed && groupCount >= 1
}

/** Writes still waiting to reach the server (the sync chip: "Sincronizado" or "N cambios pendientes"). */
class ObservePendingChanges(private val sync: SyncControl) {
    operator fun invoke(): Flow<Long> = sync.pendingCount
}
