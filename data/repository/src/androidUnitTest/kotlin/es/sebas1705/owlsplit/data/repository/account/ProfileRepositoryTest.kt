package es.sebas1705.owlsplit.data.repository.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import es.sebas1705.owlsplit.core.datastore.AppPreferences
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryTest {
    private val fresh = UserProfile("Ana", "accent:BLUE", "EUR")
    private val cloud = UserProfile("Ana", "accent:GREEN", "EUR", PayMethods(bizum = "600111222"))

    /** `users/{uid}` in memory; [exists] null plays the server out of reach. */
    private class Remote(var stored: UserProfile?, var reachable: Boolean = true) : ProfileRemote {
        val saved = mutableListOf<UserProfile>()

        override suspend fun save(session: Session, profile: UserProfile): AccountResult {
            saved += profile
            stored = profile
            return AccountResult.Done
        }

        override suspend fun delete(uid: String) = AccountResult.Done

        override suspend fun load(uid: String) = stored

        override suspend fun exists(uid: String): Boolean? = if (reachable) stored != null else null
    }

    /** Signed in to the Google account; nothing else is used. */
    private class Accounts : AccountService {
        override val session: StateFlow<Session?> = MutableStateFlow(Session("uid-ana", false, listOf("google.com")))

        override fun currentSession() = session.value

        override suspend fun signInAnonymously() = error("unused")

        override suspend fun linkGoogle(idToken: String, accessToken: String?) = error("unused")

        override suspend fun unlinkGoogle() = error("unused")

        override suspend fun signInWithGoogle(idToken: String, accessToken: String?) = error("unused")

        override suspend fun reauthenticateGoogle(idToken: String, accessToken: String?) = error("unused")

        override suspend fun signOut() = error("unused")

        override suspend fun deleteUser() = error("unused")
    }

    private suspend fun TestScope.repository(remote: Remote): ProfileRepository {
        val dir = createTempDirectory().toFile()
        val prefs =
            AppPreferences(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "p.preferences_pb") })
        val repo =
            ProfileRepositoryImpl(prefs, remote, Accounts(), CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.save(fresh)
        advanceUntilIdle()
        remote.saved.clear()
        return repo
    }

    @Test
    fun signingInToAnAccountWithAProfileUploadsNothingSoItCanBeRestored() = runTest {
        val remote = Remote(stored = null)
        val repo = repository(remote)
        // A reinstall with a fresh profile signs in to the account that has the phone number.
        remote.stored = cloud

        repo.uploadIfMissing()

        assertEquals(emptyList(), remote.saved)
        assertEquals(cloud, repo.restore())
    }

    @Test
    fun anAccountWithoutProfileGetsThePhonesOne() = runTest {
        val remote = Remote(stored = null)
        val repo = repository(remote)
        remote.stored = null

        repo.uploadIfMissing()

        assertEquals(listOf(fresh), remote.saved)
    }

    @Test
    fun withTheServerOutOfReachNothingIsUploaded() = runTest {
        val remote = Remote(stored = null)
        val repo = repository(remote)
        remote.stored = cloud
        remote.reachable = false

        assertEquals(AccountResult.Offline, repo.uploadIfMissing())
        assertEquals(emptyList(), remote.saved)
    }
}
