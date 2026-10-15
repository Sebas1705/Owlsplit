package es.sebas1705.owlsplit.domain.usecases.account

import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.GoogleTokenResult
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.repository.account.AccountService
import es.sebas1705.owlsplit.data.repository.MemberRepository
import es.sebas1705.owlsplit.data.repository.account.ProfileRepository
import es.sebas1705.owlsplit.domain.usecases.pay.PayLinks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Asks the platform for a Google ID token (Credential Manager on Android). Implemented by the app. */
interface GoogleCredentials {
    /** False where Google sign-in is not set up (iOS until P11, or no OAuth client configured). */
    val isAvailable: Boolean

    /** Never throws: cancelling and failures are results. */
    suspend fun requestIdToken(): GoogleTokenResult
}

/** Result of a flow that starts by asking Google for a token. */
sealed interface GoogleOutcome {
    /** The user closed the Google sheet: nothing to report. */
    data object Cancelled : GoogleOutcome

    /** Done; after signing in on a new device, [restored] is the profile saved for that account. */
    data class Done(val restored: UserProfile? = null) : GoogleOutcome

    data class Failed(val result: AccountResult) : GoogleOutcome
}

internal suspend fun GoogleCredentials.withToken(block: suspend (String, String?) -> AccountResult): GoogleOutcome =
    when (val token = requestIdToken()) {
        GoogleTokenResult.Cancelled -> GoogleOutcome.Cancelled
        is GoogleTokenResult.Failed -> GoogleOutcome.Failed(AccountResult.Failed(token.reason))
        is GoogleTokenResult.Token -> block(token.idToken, token.accessToken).let {
            if (it == AccountResult.Done) GoogleOutcome.Done() else GoogleOutcome.Failed(it)
        }
    }

data class AccountState(val session: Session?, val profile: UserProfile?)

/** Session and profile together, for the screens that show the account. */
class ObserveAccount(private val accounts: AccountService, private val profiles: ProfileRepository) {
    operator fun invoke(): Flow<AccountState> = combine(accounts.session, profiles.profile, ::AccountState)
}

/** Saves the profile; how I want to be paid is cleaned up and copied to my member in every group. */
/** Keeps "linked to Google" on my members in step with my session (run when the app opens). */
class SyncMyLinked(private val accounts: AccountService, private val members: MemberRepository) {
    suspend operator fun invoke() {
        val session = accounts.currentSession() ?: return
        members.publishMyLinked("google.com" in session.providers)
    }
}

class SaveProfile(private val profiles: ProfileRepository, private val members: MemberRepository) {
    suspend operator fun invoke(profile: UserProfile) {
        val pay = PayLinks.normalise(profile.payMethods)
        profiles.save(profile.copy(payMethods = pay))
        members.publishMyPayMethods(pay.orNull())
    }
}

/**
 * Ends onboarding: waits for the first session (the app signs in anonymously on its own; only the
 * very first launch can be offline, then [onWaiting] is called) and saves the profile. Nothing is
 * ever created without an owner.
 */
class CompleteOnboarding(private val accounts: AccountService, private val profiles: ProfileRepository) {
    suspend operator fun invoke(profile: UserProfile, onWaiting: () -> Unit) {
        withTimeoutOrNull(NETWORK_HINT_MILLIS) { accounts.session.filterNotNull().first() }
            ?: run {
                onWaiting()
                accounts.session.filterNotNull().first()
            }
        profiles.save(profile)
    }

    private companion object {
        const val NETWORK_HINT_MILLIS = 3_000L
    }
}

/** "I already have an account" on a fresh install: replaces the session and restores the profile. */
class SignInWithGoogle(
    private val google: GoogleCredentials,
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
) {
    val isAvailable: Boolean get() = google.isAvailable

    suspend operator fun invoke(): GoogleOutcome = when (val outcome = google.withToken(accounts::signInWithGoogle)) {
        is GoogleOutcome.Done -> GoogleOutcome.Done(profiles.restore())
        else -> outcome
    }
}

/**
 * Links Google to the anonymous account: same uid, same data. If that Google account already has
 * data (a reinstall), it signs in to it instead with the same Google sign-in ([switch]): one sign-in,
 * no question, and what the cloud has wins.
 */
class LinkGoogle(
    private val google: GoogleCredentials,
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
    /** Null in tests that do not need it: then an existing Google account is only reported. */
    private val switch: SwitchToGoogle? = null,
) {
    val isAvailable: Boolean get() = google.isAvailable

    /**
     * Nothing to sync afterwards: what the cloud has comes down and replaces this phone's profile
     * (with nothing there, this phone's goes up to users/{uid}, which is not the sync queue). The
     * "account with Google" badge on my members is caught up by [SyncMyLinked] when Home opens.
     */
    suspend operator fun invoke(): GoogleOutcome {
        // One Google at a time: an account that already has it is not linked again (unlink first).
        if (accounts.currentSession()?.providers?.contains(GOOGLE_PROVIDER) == true) return GoogleOutcome.Done()
        var switched: Switched? = null
        val outcome = google.withToken { idToken, accessToken ->
            val linked = accounts.linkGoogle(idToken, accessToken)
            if (linked != AccountResult.CredentialInUse || switch == null) return@withToken linked
            switch.signIn(idToken, accessToken).also { switched = it }.result
        }
        if (outcome !is GoogleOutcome.Done) return outcome
        switched?.let { return GoogleOutcome.Done(it.restored) }
        val cloud = profiles.restore()
        if (cloud != null) profiles.save(cloud) else profiles.uploadCurrent()
        return GoogleOutcome.Done(cloud)
    }
}

/**
 * Removes Google from the account: one Google at a time, and only after unlinking can another be
 * linked. The groups stay and nothing goes up to the sync queue.
 */
class UnlinkGoogle(
    private val accounts: AccountService,
    private val profiles: ProfileRepository,
) {
    suspend operator fun invoke(): AccountResult = accounts.unlinkGoogle().also {
        // users/{uid} lists the providers: no Google any more.
        if (it == AccountResult.Done) profiles.uploadCurrent()
    }
}

/** Fresh Google sign-in that deleting a linked account may ask for. */
class ReauthenticateGoogle(private val google: GoogleCredentials, private val accounts: AccountService) {
    suspend operator fun invoke(): GoogleOutcome = google.withToken(accounts::reauthenticateGoogle)
}

private const val GOOGLE_PROVIDER = "google.com"
