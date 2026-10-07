package es.sebas1705.owlsplit.data.repository.account

import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.core.datastore.StoredProfile
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The user's profile: on the device (the UI never waits for the network) and copied to `users/{uid}`. */
interface ProfileRepository {
    /** Null until onboarding is finished. */
    val profile: Flow<UserProfile?>

    suspend fun save(profile: UserProfile)

    /** The profile saved on the server for the signed-in account (after signing in on a new device). */
    suspend fun restore(): UserProfile?

    /** Copies the local profile to the server, if there is one and a session (also after signing in). */
    suspend fun uploadCurrent(): AccountResult
}

class ProfileRepositoryImpl(
    private val prefs: AppPreferences,
    private val remote: ProfileRemote,
    private val accounts: AccountService,
    /** Uploads outlive the screen that asked for them. */
    private val scope: CoroutineScope,
) : ProfileRepository {
    override val profile: Flow<UserProfile?> =
        prefs.profile.map { it?.let { p -> UserProfile(p.name, p.avatar, p.currency) } }

    override suspend fun save(profile: UserProfile) {
        prefs.setProfile(StoredProfile(profile.name, profile.avatar, profile.currency))
        scope.launch { uploadCurrent() }
    }

    override suspend fun restore(): UserProfile? = accounts.currentSession()?.let { remote.load(it.uid) }

    override suspend fun uploadCurrent(): AccountResult {
        val session = accounts.currentSession() ?: return AccountResult.Offline
        val profile = profile.first() ?: return AccountResult.Done
        return remote.save(session, profile)
    }
}
