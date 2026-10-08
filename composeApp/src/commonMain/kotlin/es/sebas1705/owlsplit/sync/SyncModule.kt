package es.sebas1705.owlsplit.sync

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.apps
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.memoryCacheSettings
import es.sebas1705.owlsplit.core.common.UnexpectedErrors
import es.sebas1705.owlsplit.data.remote.FirestoreRemoteStore
import es.sebas1705.owlsplit.data.repository.RepositoryContext
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.data.repository.sync.RemoteStore
import es.sebas1705.owlsplit.data.repository.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.min
import org.koin.dsl.module

val syncModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { Firebase.auth }
    single {
        // SQLDelight is the local copy: Firestore keeps only a memory cache.
        Firebase.firestore.apply { settings = firestoreSettings { cacheSettings = memoryCacheSettings { } } }
    }
    single<RemoteStore> { FirestoreRemoteStore(get(), get()) }
    // P8 replaces the empty uid with the onboarding flow (first launch needs a session).
    single { RepositoryContext(uid = { get<FirebaseAuth>().currentUser?.uid.orEmpty() }) }
    single { SyncEngine(get(), get(), get(), Dispatchers.IO) }
    single { SyncStarter(lazy { get<FirebaseAuth>() }, lazy { get<SyncEngine>() }, lazy { get<ProfileRepository>() }) }
}

/** Keeps a session (anonymous until P8) and runs the sync engine for whoever is signed in. */
class SyncStarter(
    private val auth: Lazy<FirebaseAuth>,
    private val engine: Lazy<SyncEngine>,
    private val profiles: Lazy<ProfileRepository>,
) {

    /** Null when Firebase is not configured (no google-services.json / GoogleService-Info.plist). */
    fun start(scope: CoroutineScope, platformContext: Any? = null): Job? {
        if (Firebase.apps(platformContext).isEmpty()) return null
        return scope.launch {
            auth.value.authStateChanged.collectLatest { user ->
                if (user == null) {
                    signInAnonymously()
                } else {
                    // Scoped to this user: a new auth state cancels the engine of the previous one.
                    coroutineScope {
                        launch { profiles.value.uploadCurrent() }
                        engine.value.start(this, user.uid)
                    }
                }
            }
        }
    }

    private suspend fun signInAnonymously() {
        var attempt = 0
        var reported = false
        while (true) {
            // Offline on first launch: keep trying; the next auth state restarts the engine.
            val failure = runCatching { auth.value.signInAnonymously() }.exceptionOrNull() ?: return
            // Anything but the network (Authentication off, a broken config) is a bug to see, once.
            if (failure !is FirebaseNetworkException && !reported) {
                reported = true
                UnexpectedErrors.report(failure)
            }
            delay(min(RETRY_BASE_MILLIS shl min(attempt++, MAX_SHIFT), RETRY_MAX_MILLIS))
        }
    }

    private companion object {
        const val RETRY_BASE_MILLIS = 2_000L
        const val RETRY_MAX_MILLIS = 5 * 60 * 1_000L
        const val MAX_SHIFT = 10
    }
}
