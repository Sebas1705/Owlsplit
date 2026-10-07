package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.Outbox
import es.sebas1705.owlsplit.data.models.OutboxType
import kotlin.math.min
import kotlin.random.Random

/** One upload pass over the outbox (docs/SYNC.md, "Subida"). */
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
                retry(entry, "transient")
                false
            }
            PushResult.Rejected -> onRejected(entry, type, version)
        }

    private suspend fun onRejected(entry: Outbox, type: OutboxType, version: Long): Boolean =
        when (val found = remote.fetch(type, entry.group_id, entry.entity_id)) {
            FetchResult.NoAccess -> {
                local.markAccessLost(entry.group_id)
                local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "access lost")
                false
            }
            FetchResult.Transient -> {
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
                found.doc.version >= version -> {
                    applier.resolveConflict(entry, found.doc)
                    true
                }
                // Rejected for another reason (validation): a bug, keep it visible in last_error.
                else -> {
                    local.scheduleRetry(entry, local.now() + RECHECK_MILLIS, "rejected")
                    false
                }
            }
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
