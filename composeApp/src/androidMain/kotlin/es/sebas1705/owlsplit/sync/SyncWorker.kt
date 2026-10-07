package es.sebas1705.owlsplit.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import es.sebas1705.owlsplit.data.repository.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Uploads pending writes once there is network, even if the app was closed meanwhile. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val engine: SyncEngine by inject()

    override suspend fun doWork(): Result {
        engine.pushNow()
        return if (engine.pendingCount.first() == 0L) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "sync-push"

        /** Enqueues an upload whenever the outbox goes from empty to pending. */
        fun scheduleOnPending(context: Context, engine: SyncEngine, scope: CoroutineScope) = scope.launch {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            engine.pendingCount.map { it > 0 }.distinctUntilChanged().filter { it }.collect {
                WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
