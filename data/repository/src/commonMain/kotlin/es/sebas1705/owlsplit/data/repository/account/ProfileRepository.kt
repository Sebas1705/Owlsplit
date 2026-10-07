package es.sebas1705.owlsplit.data.repository.account

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.StoredProfile
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The profile lives on the device (the UI never waits for the network) and is copied to `users/{uid}`. */
class ProfileRepository(
    private val prefs: AppPreferences,
    private val remote: ProfileRemote,
    private val accounts: AccountService,
) {
    /** Null until onboarding is finished. */
    val profile: Flow<UserProfile?> = prefs.profile.map { it?.let { p -> UserProfile(p.name, p.avatar, p.currency) } }

    suspend fun save(profile: UserProfile) {
        prefs.setProfile(StoredProfile(profile.name, profile.avatar, profile.currency))
        uploadCurrent()
    }

    /** Copies the local profile to the server, if there is one and a session (also after signing in). */
    suspend fun uploadCurrent(): AccountResult {
        val session = accounts.currentSession() ?: return AccountResult.Offline
        val profile = profile.first() ?: return AccountResult.Done
        return remote.save(session, profile)
    }
}
