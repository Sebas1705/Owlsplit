package es.sebas1705.owlsplit.data.repository.account

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import kotlinx.coroutines.flow.Flow

/** Firebase Auth as the app needs it. Implemented in `data:remote`. */
interface AccountService {
    /** The current session; null while signed out (the app signs in anonymously right away). */
    val session: Flow<Session?>

    fun currentSession(): Session?

    suspend fun signInAnonymously(): AccountResult

    /** Links Google to the current (anonymous) user: same uid, same data. */
    suspend fun linkGoogle(idToken: String, accessToken: String? = null): AccountResult

    /**
     * Removes Google from the current user: same uid and data, but no longer recoverable through it
     * (the session reads as anonymous again, and another Google can be linked).
     */
    suspend fun unlinkGoogle(): AccountResult

    /** Replaces the current session with an existing Google account (fresh install). */
    suspend fun signInWithGoogle(idToken: String, accessToken: String? = null): AccountResult

    suspend fun reauthenticateGoogle(idToken: String, accessToken: String? = null): AccountResult

    suspend fun signOut()

    /** Deletes the Firebase user. */
    suspend fun deleteUser(): AccountResult
}

/** `users/{uid}`. Implemented in `data:remote`. */
interface ProfileRemote {
    suspend fun save(session: Session, profile: UserProfile): AccountResult

    suspend fun delete(uid: String): AccountResult

    /** The profile stored for [uid] (signing in on a new device); null if none or unreachable. */
    suspend fun load(uid: String): UserProfile?

    /** Whether [uid] has a profile stored; null when the server cannot be reached to tell. */
    suspend fun exists(uid: String): Boolean?
}
