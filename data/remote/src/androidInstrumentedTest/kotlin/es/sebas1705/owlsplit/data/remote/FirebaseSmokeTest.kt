package es.sebas1705.owlsplit.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Serializable
data class SmokeDoc(val nonce: String)

/**
 * Proves the GitLive SDK works end to end against the real dev project:
 * anonymous sign-in, a write, and a real-time listener that sees it.
 *
 * Firebase options arrive as instrumentation arguments (see scripts/run-firebase-smoke.sh),
 * so no google-services.json is needed in this module.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseSmokeTest {

    private fun arg(name: String): String =
        requireNotNull(InstrumentationRegistry.getArguments().getString(name)) {
            "Missing instrumentation argument '$name' — run scripts/run-firebase-smoke.sh"
        }

    @Test
    fun anonymousAuthWriteAndListen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = arg("firebaseAppId"),
                apiKey = arg("firebaseApiKey"),
                projectId = arg("firebaseProjectId"),
            ),
            name = "smoke-${Random.nextInt(1_000_000)}",
        )
        val auth = Firebase.auth(app)
        val firestore = Firebase.firestore(app)

        val uid = assertNotNull(auth.signInAnonymously().user).uid
        val doc = firestore.collection("smoke").document(uid)
        val nonce = Random.nextLong().toString()

        try {
            doc.set(SmokeDoc(nonce))
            val seen = withTimeout(20_000) {
                doc.snapshots.first { it.exists && it.data<SmokeDoc>().nonce == nonce }
            }
            assertEquals(nonce, seen.data<SmokeDoc>().nonce)
        } finally {
            doc.delete()
            auth.currentUser?.delete()
        }
    }
}
