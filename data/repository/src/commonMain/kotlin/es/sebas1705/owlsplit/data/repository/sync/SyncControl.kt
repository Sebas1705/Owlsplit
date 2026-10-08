package es.sebas1705.owlsplit.data.repository.sync

/** What account operations need from the sync engine. */
interface SyncControl {
    /** Uploads everything that can be uploaded now; false when something is left (offline). */
    suspend fun flush(): Boolean

    fun isAccessLost(groupId: String): Boolean
}
