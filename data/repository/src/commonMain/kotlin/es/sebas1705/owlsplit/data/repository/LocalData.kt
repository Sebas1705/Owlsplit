package es.sebas1705.owlsplit.data.repository

import es.sebas1705.owlsplit.data.local.db.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The local database as a whole. */
class LocalData(private val db: AppDatabase, private val io: CoroutineDispatcher) {
    /** Removes every local row: sign out and account deletion. */
    suspend fun wipe() = withContext(io) { db.maintenanceQueries.wipe() }
}
