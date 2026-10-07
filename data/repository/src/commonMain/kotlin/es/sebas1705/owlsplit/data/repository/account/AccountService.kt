package es.sebas1705.owlsplit.data.repository.account

import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.flow.Flow

sealed interface AccountResult {
    data object Done : AccountResult

    /** The credential already belongs to another account (linking). */
    data object CredentialInUse : AccountResult

    /** The provider asks for a fresh sign-in before a sensitive operation (deleting the account). */
    data object RecentLoginRequired : AccountResult

    /** No connection, or the server could not be reached. */
    data object Offline : AccountResult

    data class Failed(val reason: String) : AccountResult
}

/** Firebase Auth as the app needs it. Implemented in `data:remote`. */
interface AccountService {
    /** The current session; null while signed out (the app signs in anonymously right away). */
    val session: Flow<Session?>

    fun currentSession(): Session?

    suspend fun signInAnonymously(): AccountResult

    /** Links Google to the current (anonymous) user: same uid, same data. */
    suspend fun linkGoogle(idToken: String): AccountResult

    /** Replaces the current session with an existing Google account (fresh install). */
    suspend fun signInWithGoogle(idToken: String): AccountResult

    suspend fun reauthenticateGoogle(idToken: String): AccountResult

    suspend fun signOut()

    /** Deletes the Firebase user. */
    suspend fun deleteUser(): AccountResult
}

/** `users/{uid}`. Implemented in `data:remote`. */
interface ProfileRemote {
    suspend fun save(session: Session, profile: UserProfile): AccountResult

    suspend fun delete(uid: String): AccountResult
}
