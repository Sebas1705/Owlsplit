package es.sebas1705.owlsplit.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import es.sebas1705.owlsplit.data.local.db.AppDatabase
import es.sebas1705.owlsplit.data.models.ChangeLogEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChangeLogRepositoryImpl(
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
) : ChangeLogRepository {
    override fun observe(groupId: String): Flow<List<ChangeLogEntry>> =
        db.changeLogQueries.selectByGroup(groupId).asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }

    override fun observeRecent(limit: Long): Flow<List<ChangeLogEntry>> =
        db.changeLogQueries.selectRecent(limit).asFlow().mapToList(io).map { rows -> rows.map { it.toModel() } }
}
