package es.sebas1705.owlsplit.data.repository.sync

import es.sebas1705.owlsplit.data.local.db.Outbox
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The upload waiting for its acknowledgement. A listener can deliver our own write before `push`
 * returns; without this it would look like someone else's newer version over a pending edit.
 */
internal class InFlightUpload {
    private class Upload(val entry: Outbox, val version: Long)

    private val current = MutableStateFlow<Upload?>(null)

    /** Marks [entry] as being written as [version] while [block] runs, acknowledgement handling included. */
    suspend fun <T> during(entry: Outbox, version: Long, block: suspend () -> T): T {
        current.value = Upload(entry, version)
        try {
            return block()
        } finally {
            current.value = null
        }
    }

    /** The outbox entry [doc] is the echo of, if any. */
    fun echoed(doc: RemoteDoc): Pair<Outbox, Long>? = current.value
        ?.takeIf {
            it.entry.entity_type == doc.type.name && it.entry.entity_id == doc.id &&
                it.version == doc.version && doc.sameContentAs(it.entry.payload)
        }
        ?.let { it.entry to it.version }
}
