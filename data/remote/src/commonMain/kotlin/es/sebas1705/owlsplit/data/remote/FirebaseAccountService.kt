package es.sebas1705.owlsplit.data.remote

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseAuthRecentLoginRequiredException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.account.AccountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [AccountService] on Firebase Auth (docs/FIREBASE.md, "Autenticación"). */
class FirebaseAccountService(private val auth: FirebaseAuth) : AccountService {

    override val session: Flow<Session?> = auth.authStateChanged.map { it?.toSession() }

    override fun currentSession(): Session? = auth.currentUser?.toSession()

    override suspend fun signInAnonymously() = call { auth.signInAnonymously() }

    override suspend fun linkGoogle(idToken: String) = call {
        val user = requireNotNull(auth.currentUser) { "No session to link" }
        user.linkWithCredential(GoogleAuthProvider.credential(idToken, null))
        // The listener only fires on sign-in/out: reload so currentSession() shows the provider.
        user.reload()
    }

    override suspend fun signInWithGoogle(idToken: String) =
        call { auth.signInWithCredential(GoogleAuthProvider.credential(idToken, null)) }

    override suspend fun reauthenticateGoogle(idToken: String) =
        call { requireNotNull(auth.currentUser).reauthenticate(GoogleAuthProvider.credential(idToken, null)) }

    override suspend fun signOut() = auth.signOut()

    override suspend fun deleteUser() = call { auth.currentUser?.delete() }

    // Every SDK failure becomes a result the UI can show.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun call(block: suspend () -> Any?): AccountResult = try {
        block()
        AccountResult.Done
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthUserCollisionException) {
        AccountResult.CredentialInUse
    } catch (e: FirebaseAuthRecentLoginRequiredException) {
        AccountResult.RecentLoginRequired
    } catch (e: FirebaseNetworkException) {
        AccountResult.Offline
    } catch (e: Exception) {
        AccountResult.Failed(e.message ?: e::class.simpleName.orEmpty())
    }
}

private fun FirebaseUser.toSession() = Session(
    uid = uid,
    isAnonymous = isAnonymous,
    providers = providerData.map { it.providerId }.filter { it != FIREBASE_PROVIDER },
)

private const val FIREBASE_PROVIDER = "firebase"
