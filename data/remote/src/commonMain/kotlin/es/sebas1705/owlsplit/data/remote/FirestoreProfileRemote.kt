package es.sebas1705.owlsplit.data.remote

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.Timestamp
import es.sebas1705.owlsplit.data.models.PayMethods
import es.sebas1705.owlsplit.data.models.Session
import es.sebas1705.owlsplit.data.models.UserProfile
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.repository.account.ProfileRemote
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class UserDto(
    val displayName: String,
    val avatar: String,
    val preferredCurrency: String,
    val linkedProviders: List<String>,
    val updatedAt: BaseTimestamp,
    val payMethods: PayMethods,
)

/** `users/{uid}`: only its owner reads and writes it. */
class FirestoreProfileRemote(
    private val firestore: FirebaseFirestore,
    private val timeout: Duration = 20.seconds,
) : ProfileRemote {

    override suspend fun save(session: Session, profile: UserProfile): AccountResult = call {
        val dto = UserDto(
            profile.name,
            profile.avatar,
            profile.currency,
            session.providers,
            Timestamp.ServerTimestamp,
            profile.payMethods,
        )
        firestore.collection(USERS).document(session.uid).set(UserDto.serializer(), dto, merge = true)
    }

    override suspend fun delete(uid: String): AccountResult = call {
        firestore.collection(USERS).document(uid).delete()
    }

    @Suppress("SwallowedException") // Unreachable or denied: there is simply no profile to restore.
    override suspend fun load(uid: String): UserProfile? = try {
        withTimeoutOrNull(timeout) {
            val snapshot = firestore.collection(USERS).document(uid).get()
            if (!snapshot.exists) return@withTimeoutOrNull null
            val dto = snapshot.data(UserReadDto.serializer())
            UserProfile(dto.displayName, dto.avatar, dto.preferredCurrency, dto.payMethods)
        }
    } catch (e: FirebaseFirestoreException) {
        null
    }

    @Suppress("SwallowedException") // Unreachable or denied: unknown, so nothing gets overwritten.
    override suspend fun exists(uid: String): Boolean? = try {
        withTimeoutOrNull(timeout) { firestore.collection(USERS).document(uid).get().exists }
    } catch (e: FirebaseFirestoreException) {
        null
    }

    /** Offline a write never completes (the SDK queues it): report it instead of waiting. */
    private suspend fun call(block: suspend () -> Unit): AccountResult = try {
        withTimeoutOrNull(timeout) { block() }?.let { AccountResult.Done } ?: AccountResult.Offline
    } catch (e: FirebaseFirestoreException) {
        AccountResult.Failed(e.message.orEmpty())
    }

    private companion object {
        const val USERS = "users"
    }
}

@Serializable
internal data class UserReadDto(
    val displayName: String,
    val avatar: String = "",
    val preferredCurrency: String = "EUR",
    val payMethods: PayMethods = PayMethods(),
)
