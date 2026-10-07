package es.sebas1705.owlsplit.account

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.domain.usecases.account.GoogleCredentials
import java.lang.ref.WeakReference

/**
 * Google ID token through Credential Manager. The Web client id comes from google-services.json
 * (`default_web_client_id`, present once Google is enabled in Firebase Authentication).
 */
class AndroidGoogleCredentials(context: Context, private val activities: ResumedActivity) : GoogleCredentials {
    private val webClientId: String? = context.resources
        .getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }
        ?.let(context::getString)

    override val isAvailable: Boolean get() = webClientId != null

    @Suppress("SwallowedException") // Cancelling the Google sheet is a result, not an error.
    override suspend fun requestIdToken(): GoogleTokenResult {
        val clientId = webClientId ?: return GoogleTokenResult.Failed("Google sign-in is not configured")
        val activity = activities.current ?: return GoogleTokenResult.Failed("No activity in front")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleTokenResult.Failed("Unexpected credential ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleTokenResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Failed(e.message ?: e.type)
        } catch (e: GoogleIdTokenParsingException) {
            GoogleTokenResult.Failed(e.message.orEmpty())
        }
    }
}

/** The activity in front, for APIs that show their own UI (Credential Manager). */
class ResumedActivity : Application.ActivityLifecycleCallbacks {
    private var ref = WeakReference<Activity>(null)
    val current: Activity? get() = ref.get()

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (ref.get() === activity) ref = WeakReference(null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
