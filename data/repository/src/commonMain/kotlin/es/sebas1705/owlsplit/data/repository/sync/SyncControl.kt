package es.sebas1705.owlsplit.data.repository.sync

import kotlinx.coroutines.flow.Flow

/** What the rest of the app needs from the sync engine. */
interface SyncControl {
    /** Writes waiting to be uploaded. */
    val pendingCount: Flow<Long>

    /** Uploads everything that can be uploaded now; false when something is left (offline). */
    suspend fun flush(): Boolean

    fun isAccessLost(groupId: String): Boolean

    /**
     * The group is on screen: catch up with what no live listener covers (an archived group, the
     * personal fields of members). Returns at once; the engine does it in the background, throttled.
     */
    fun refreshGroup(groupId: String) = Unit
}
