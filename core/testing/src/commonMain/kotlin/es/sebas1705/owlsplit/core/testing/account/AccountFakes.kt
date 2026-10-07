package es.sebas1705.owlsplit.core.testing.account

import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.repository.sync.SyncControl
import kotlinx.coroutines.flow.MutableStateFlow

/** Firebase Auth in memory. Results can be scripted per operation. */
class FakeAccounts(initial: Session? = ANONYMOUS) : AccountService {
    override val session = MutableStateFlow(initial)
    val calls = mutableListOf<String>()
    var linkResult: AccountResult = AccountResult.Done
    var signInResult: AccountResult = AccountResult.Done
    var deleteResult: AccountResult = AccountResult.Done

    override fun currentSession() = session.value

    override suspend fun signInAnonymously(): AccountResult {
        calls += "signInAnonymously"
        session.value = ANONYMOUS
        return AccountResult.Done
    }

    override suspend fun linkGoogle(idToken: String): AccountResult {
        calls += "linkGoogle $idToken"
        if (linkResult == AccountResult.Done) session.value = session.value?.copy(isAnonymous = false, providers = GOOGLE)
        return linkResult
    }

    override suspend fun signInWithGoogle(idToken: String): AccountResult {
        calls += "signInWithGoogle $idToken"
        if (signInResult == AccountResult.Done) session.value = Session("uid-google", false, GOOGLE)
        return signInResult
    }

    override suspend fun reauthenticateGoogle(idToken: String): AccountResult {
        calls += "reauthenticateGoogle $idToken"
        if (deleteResult == AccountResult.RecentLoginRequired) deleteResult = AccountResult.Done
        return AccountResult.Done
    }

    override suspend fun signOut() {
        calls += "signOut"
        session.value = null
    }

    override suspend fun deleteUser(): AccountResult {
        calls += "deleteUser"
        if (deleteResult == AccountResult.Done) session.value = null
        return deleteResult
    }

    companion object {
        val ANONYMOUS = Session("uid-ana", isAnonymous = true, providers = emptyList())
        val GOOGLE = listOf("google.com")
    }
}

class FakeProfiles(initial: UserProfile? = null, private val remote: UserProfile? = null) : ProfileRepository {
    override val profile = MutableStateFlow(initial)

    override suspend fun save(profile: UserProfile) {
        this.profile.value = profile
    }

    override suspend fun restore(): UserProfile? = remote

    override suspend fun uploadCurrent() = AccountResult.Done
}

class FakeGoogle(
    override val isAvailable: Boolean = true,
    var next: GoogleTokenResult = GoogleTokenResult.Token("google-token"),
) : GoogleCredentials {
    override suspend fun requestIdToken() = next
}

class FakeSync(var online: Boolean = true) : SyncControl {
    override suspend fun flush() = online

    override fun isAccessLost(groupId: String) = false
}
