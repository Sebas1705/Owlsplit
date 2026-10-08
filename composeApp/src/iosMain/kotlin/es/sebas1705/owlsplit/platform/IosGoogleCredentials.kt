package es.sebas1705.owlsplit.platform

import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google sign-in, implemented in Swift with the Google Sign-In SDK (iosApp/iosApp/GoogleSignIn.swift):
 * its API is Swift-first, so the app hands an implementation of this interface to
 * [es.sebas1705.owlsplit.MainViewController].
 */
interface GoogleSignInBridge {
    /** False until Google is enabled in Firebase and the app has its URL scheme. */
    val isConfigured: Boolean

    /** Exactly one call of [onResult]: tokens, or no token and an error (null error: cancelled). */
    fun requestTokens(onResult: (idToken: String?, accessToken: String?, error: String?) -> Unit)
}

class IosGoogleCredentials(private val bridge: GoogleSignInBridge?) : GoogleCredentials {
    override val isAvailable: Boolean get() = bridge?.isConfigured == true

    override suspend fun requestIdToken(): GoogleTokenResult {
        val bridge = bridge ?: return GoogleTokenResult.Failed("Google sign-in is not configured")
        return suspendCancellableCoroutine { cont ->
            bridge.requestTokens { idToken, accessToken, error ->
                val result = when {
                    idToken != null -> GoogleTokenResult.Token(idToken, accessToken)
                    error == null -> GoogleTokenResult.Cancelled
                    else -> GoogleTokenResult.Failed(error)
                }
                if (cont.isActive) cont.resume(result)
            }
        }
    }
}
