package es.sebas1705.owlsplit.data.remote

import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.firestore.DocumentReference
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.QuerySnapshot
import dev.gitlive.firebase.firestore.Source
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.fromMilliseconds
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.repository.sync.FetchResult
import es.sebas1705.owlsplit.data.repository.sync.PushResult
import es.sebas1705.owlsplit.data.repository.sync.RemoteCollection
import es.sebas1705.owlsplit.data.repository.sync.RemoteDoc
import es.sebas1705.owlsplit.data.repository.sync.RemoteStore
import es.sebas1705.owlsplit.data.repository.sync.decodeRemoteDoc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [RemoteStore] on Cloud Firestore (docs/FIREBASE.md). Writes exactly the keys the rules accept,
 * with the server time as `updatedAt`; listeners deliver only confirmed documents.
 */
class FirestoreRemoteStore(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val timeout: Duration = 20.seconds,
) : RemoteStore {

    override suspend fun push(
        type: OutboxType,
        groupId: String,
        entityId: String,
        version: Long,
        payload: String,
    ): PushResult {
        val uid = auth.currentUser?.uid ?: return PushResult.Transient
        val doc = decodeRemoteDoc(type, payload, version, updatedAt = 0, updatedBy = uid)
        val stamp = Stamp(version, uid)
        val ref = ref(type, groupId, entityId)
        return firestoreCall(onDenied = PushResult.Rejected, onError = PushResult.Transient) {
            // merge: keys the client does not own (lastJoinCode) must survive the write.
            when (doc) {
                is RemoteDoc.GroupDoc -> ref.set(GroupDto.serializer(), doc.group.toDto(stamp), merge = true)
                is RemoteDoc.MemberDoc -> ref.set(MemberDto.serializer(), doc.member.toDto(stamp), merge = true)
                is RemoteDoc.ExpenseDoc -> ref.set(ExpenseDto.serializer(), doc.expense.toDto(stamp), merge = true)
                is RemoteDoc.TransferDoc -> ref.set(TransferDto.serializer(), doc.transfer.toDto(stamp), merge = true)
                is RemoteDoc.ChangeLogDoc -> ref.set(ChangeLogDto.serializer(), doc.entry.toDto())
            }
            PushResult.Ok
        }
    }

    override suspend fun fetch(type: OutboxType, groupId: String, entityId: String): FetchResult =
        firestoreCall(onDenied = FetchResult.NoAccess, onError = FetchResult.Transient) {
            // From the server: the cache could hold our own unconfirmed write.
            val snapshot = ref(type, groupId, entityId).get(Source.SERVER)
            when {
                !snapshot.exists -> FetchResult.Missing
                else -> snapshot.toRemote(type, groupId)?.let { FetchResult.Found(it) } ?: FetchResult.Transient
            }
        }

    override fun observeMyGroups(uid: String): Flow<List<RemoteDoc.GroupDoc>> =
        firestore.collection(GROUPS).where { "memberUids" contains uid }
            // Metadata changes too: the first server answer can match the (empty) cached one.
            .snapshots(includeMetadataChanges = true)
            // The engine reads this as the full set of my groups: only complete, confirmed ones.
            .filterNot { it.metadata.isFromCache || it.metadata.hasPendingWrites }
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toRemote(OutboxType.GROUP, it.id) as RemoteDoc.GroupDoc? }
            }
            .resilient()

    override fun observe(groupId: String, collection: RemoteCollection, sinceMillis: Long): Flow<List<RemoteDoc>> =
        firestore.collection("$GROUPS/$groupId/${collection.path}")
            .where { "updatedAt" greaterThanOrEqualTo Timestamp.fromMilliseconds(sinceMillis.toDouble()) }
            .snapshots
            .map { snapshot -> snapshot.confirmedChanges().mapNotNull { it.toRemote(collection.type, groupId) } }
            .resilient()

    private fun ref(type: OutboxType, groupId: String, entityId: String): DocumentReference {
        val group = firestore.collection(GROUPS).document(groupId)
        return when (type) {
            OutboxType.GROUP -> group
            else -> group.collection(RemoteCollection.entries.single { it.type == type }.path).document(entityId)
        }
    }

    /** Runs a Firestore call: PERMISSION_DENIED gives [onDenied]; any other failure or a timeout, [onError]. */
    private suspend fun <T> firestoreCall(onDenied: T, onError: T, block: suspend () -> T): T =
        try {
            // Offline, a write never completes (the SDK queues it): give up and let the outbox retry.
            withTimeoutOrNull(timeout) { block() } ?: onError
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirestoreExceptionCode.PERMISSION_DENIED) onDenied else onError
        }

    /** Transient listener errors are retried with a growing delay; losing access just ends the flow. */
    private fun <T> Flow<T>.resilient(): Flow<T> = this
        .retryWhen { cause, attempt ->
            val retry = !cause.isPermissionDenied()
            if (retry) delay(min(LISTEN_RETRY_BASE_MILLIS shl min(attempt.toInt(), MAX_SHIFT), LISTEN_RETRY_MAX_MILLIS))
            retry
        }
        .catch { if (!it.isPermissionDenied()) throw it }

    private fun Throwable.isPermissionDenied() =
        this is FirebaseFirestoreException && code == FirestoreExceptionCode.PERMISSION_DENIED

    private companion object {
        const val GROUPS = "groups"
        const val LISTEN_RETRY_BASE_MILLIS = 1_000L
        const val LISTEN_RETRY_MAX_MILLIS = 5 * 60 * 1_000L
        const val MAX_SHIFT = 20
    }
}

private val RemoteCollection.path: String
    get() = when (this) {
        RemoteCollection.MEMBERS -> "members"
        RemoteCollection.EXPENSES -> "expenses"
        RemoteCollection.TRANSFERS -> "transfers"
        RemoteCollection.CHANGELOG -> "changelog"
    }

/** Added or modified documents already confirmed by the server (the first snapshot brings them all). */
private fun QuerySnapshot.confirmedChanges(): List<DocumentSnapshot> =
    documentChanges.map { it.document }.filterNot { it.metadata.hasPendingWrites }

/** Null for documents that cannot be read as [type] (malformed, or unconfirmed). */
// Decoding errors are not part of the SDK contract: a malformed document is skipped.
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun DocumentSnapshot.toRemote(type: OutboxType, groupId: String): RemoteDoc? = try {
    when (type) {
        OutboxType.GROUP -> data(GroupDto.serializer()).toModel(id)?.let(RemoteDoc::GroupDoc)
        OutboxType.MEMBER -> data(MemberDto.serializer()).toModel(groupId, id)?.let(RemoteDoc::MemberDoc)
        OutboxType.EXPENSE -> data(ExpenseDto.serializer()).toModel(groupId, id)?.let(RemoteDoc::ExpenseDoc)
        OutboxType.TRANSFER -> data(TransferDto.serializer()).toModel(groupId, id)?.let(RemoteDoc::TransferDoc)
        OutboxType.CHANGE_LOG -> data(ChangeLogDto.serializer()).toRemote(groupId, id)
    }
} catch (e: RuntimeException) {
    null
}
