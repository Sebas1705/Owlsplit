package es.sebas1705.owlsplit.data.remote

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.code
import es.sebas1705.owlsplit.data.models.AccountResult
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.InvitePreview
import es.sebas1705.owlsplit.data.repository.invite.InviteService
import es.sebas1705.owlsplit.data.models.PreviewResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
internal data class InviteDto(
    val groupId: String,
    val groupName: String,
    val groupIcon: String,
    val memberCount: Long,
    val createdBy: String,
    val createdAt: Long,
)

/** `invites/{code}` and the join write on `groups/{id}` (docs/FIREBASE.md, rules `join()`). */
class FirestoreInviteService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val clock: Clock = Clock.System,
    private val timeout: Duration = 20.seconds,
) : InviteService {

    override suspend fun publish(group: Group, memberCount: Int): AccountResult = call {
        val uid = auth.currentUser?.uid ?: return@call AccountResult.Offline
        val dto =
            InviteDto(group.id, group.name, group.icon, memberCount.toLong(), uid, clock.now().toEpochMilliseconds())
        firestore.collection(INVITES).document(group.inviteCode).set(InviteDto.serializer(), dto)
        AccountResult.Done
    }

    override suspend fun withdraw(code: String): AccountResult = call {
        firestore.collection(INVITES).document(code).delete()
        AccountResult.Done
    }

    override suspend fun preview(code: String): PreviewResult = try {
        withTimeoutOrNull(timeout) {
            val snapshot = firestore.collection(INVITES).document(code).get(Source.SERVER)
            if (!snapshot.exists) {
                PreviewResult.NotFound
            } else {
                val dto = snapshot.data(InviteDto.serializer())
                PreviewResult.Found(InvitePreview(code, dto.groupId, dto.groupName, dto.groupIcon, dto.memberCount.toInt()))
            }
        } ?: PreviewResult.Offline
    } catch (e: FirebaseFirestoreException) {
        if (e.code == FirestoreExceptionCode.PERMISSION_DENIED) PreviewResult.NotFound else PreviewResult.Offline
    }

    override suspend fun join(preview: InvitePreview): AccountResult = call {
        val uid = auth.currentUser?.uid ?: return@call AccountResult.Offline
        // The joiner cannot read the group: field transforms only, checked by the rules against the code.
        firestore.collection(GROUPS).document(preview.groupId).update(
            "memberUids" to FieldValue.arrayUnion(uid),
            "lastJoinCode" to preview.code,
            "version" to FieldValue.increment(1),
            "updatedAt" to FieldValue.serverTimestamp,
            "updatedBy" to uid,
        )
        AccountResult.Done
    }

    /** Offline writes never complete (the SDK queues them): report it instead of waiting. */
    private suspend fun call(block: suspend () -> AccountResult): AccountResult = try {
        withTimeoutOrNull(timeout) { block() } ?: AccountResult.Offline
    } catch (e: FirebaseFirestoreException) {
        AccountResult.Failed(e.code.name)
    }

    private companion object {
        const val INVITES = "invites"
        const val GROUPS = "groups"
    }
}
