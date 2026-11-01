package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.Group
import es.sebas1705.owlsplit.data.models.OutboxType
import es.sebas1705.owlsplit.data.repository.json
import kotlin.math.min
import kotlin.random.Random

/** One upload pass over the outbox (docs/SYNC.md, "Upload"). */
internal class OutboxProcessor(
    private val local: SyncLocalStore,
    private val applier: RemoteApplier,
    private val remote: RemoteStore,
    private val random: Random,
    private val inFlight: InFlightUpload = InFlightUpload(),
) {
    /** [ignoreSchedule]: also retry entries still waiting for their backoff (the user is waiting). */
    suspend fun processReady(ignoreSchedule: Boolean = false) {
        val now = local.now()
        val blocked = HashSet<String>()
        // FIFO per group: an entry still waiting for its retry holds back the later ones of its group.
        for (entry in local.all()) {
            when {
                entry.group_id in blocked -> Unit
                !ignoreSchedule && entry.next_attempt_at > now -> blocked += entry.group_id
                local.isAccessLost(entry.group_id) -> local.scheduleRetry(entry, now + RECHECK_MILLIS, "access lost")
                !process(entry) -> blocked += entry.group_id
            }
        }
    }

    /** Returns false when the entry stays pending (the rest of its group must wait). */
    private suspend fun process(entry: Outbox): Boolean {
        val type = OutboxType.valueOf(entry.entity_type)
        val version = if (type == OutboxType.CHANGE_LOG) 1L else entry.base_version + 1
        return inFlight.during(entry, version) { upload(entry, type, version) }
    }

    private suspend fun upload(entry: Outbox, type: OutboxType, version: Long): Boolean =
        when (remote.push(type, entry.group_id, entry.entity_id, version, entry.payload)) {
            PushResult.Ok -> {
                local.onPushed(entry, version)
                true
            }
            PushResult.Transient -> {
                // The SDK may keep the write queued and send it later, ahead of a newer edit of this document.
                if (type != OutboxType.CHANGE_LOG) local.sent.remember(entry)
                retry(entry, "transient")
                false
            }
            PushResult.Rejected -> onRejected(entry, type, version)
        }

    private suspend fun onRejected(entry: Outbox, type: OutboxType, version: Long): Boolean =
        when (val found = remote.fetch(type, entry.group_id, entry.entity_id)) {
            FetchResult.NoAccess -> {
                local.setAccessLost(entry.group_id, lost = true)
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "access lost")
                false
            }
            FetchResult.Transient, FetchResult.Unreadable -> {
                retry(entry, "transient")
                false
            }
            FetchResult.Missing -> {
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
                false
            }
            is FetchResult.Found -> when {
                // Our own write whose acknowledgement was lost.
                found.doc.version >= version && found.doc.sameContentAs(entry.payload) -> {
                    local.onPushed(entry, found.doc.version)
                    true
                }
                // An earlier write of ours, queued by the SDK, landed before this one: chain on top of it.
                found.doc.version == version && local.sent.contains(entry, found.doc) -> {
                    local.onEarlierWriteLanded(entry, found.doc.version)?.let { process(it) } ?: true
                }
                found.doc.version >= version -> {
                    applier.resolveConflict(entry, found.doc)
                    true
                }
                // A group edit made offline and then leaving it, compacted into one write the rules reject.
                found.doc is RemoteDoc.GroupDoc -> uploadEditThenLeave(entry, found.doc, version)
                // Rejected for another reason (validation): a bug, keep it visible in last_error.
                else -> {
                    local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
                    false
                }
            }
        }

    /**
     * The rules accept a leave only if it changes nothing but `memberUids`, yet compaction folded the edits
     * made before it (rename, archive...) into the same write. Upload those first, then the leave on top.
     */
    private suspend fun uploadEditThenLeave(entry: Outbox, server: RemoteDoc.GroupDoc, version: Long): Boolean {
        val mine = decodeRemoteDoc(OutboxType.GROUP, entry.payload, version, updatedAt = 0, updatedBy = "")
        val group = (mine as RemoteDoc.GroupDoc).group
        val edit = group.copy(memberUids = server.group.memberUids)
        val leaves = group.memberUids != server.group.memberUids
        if (!leaves || edit.copy(meta = server.group.meta) == server.group) return rejectedForGood(entry)
        val payload = json.encodeToString(Group.serializer(), edit)
        return when (remote.push(OutboxType.GROUP, entry.group_id, entry.entity_id, version, payload)) {
            PushResult.Ok -> {
                local.onEarlierWriteLanded(entry, version)?.let { process(it) } ?: true
            }
            PushResult.Transient -> {
                local.sent.remember(entry, payload)
                retry(entry, "transient")
                false
            }
            PushResult.Rejected -> rejectedForGood(entry)
        }
    }

    private fun rejectedForGood(entry: Outbox): Boolean {
        local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
        return false
    }

    private fun retry(entry: Outbox, error: String) {
        val exponential = min(BASE_DELAY_MILLIS shl min(entry.attempts.toInt(), MAX_SHIFT), MAX_DELAY_MILLIS)
        local.scheduleRetry(entry, local.now() + exponential + random.nextLong(JITTER_MILLIS), error)
    }

    private companion object {
        const val BASE_DELAY_MILLIS = 1_000L
        const val MAX_DELAY_MILLIS = 15 * 60 * 1_000L
        const val MAX_SHIFT = 20
        const val JITTER_MILLIS = 1_000L
        const val RECHECK_MILLIS = 60 * 60 * 1_000L
    }
}
