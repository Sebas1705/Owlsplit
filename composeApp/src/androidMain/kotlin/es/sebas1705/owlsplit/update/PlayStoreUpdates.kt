package es.sebas1705.owlsplit.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdate
import es.sebas1705.owlsplit.domain.usecases.update.StoreUpdates
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Google Play In-App Updates, flexible flow: Play downloads the update in the background while the app
 * keeps working, and [complete] restarts the app to install it. Only for installs from Play.
 */
class PlayStoreUpdates(context: Context, private val activities: ResumedActivity) : StoreUpdates {
    private val manager = AppUpdateManagerFactory.create(context)

    override val update: Flow<StoreUpdate?> = callbackFlow {
        // What Play knows now (a download may have finished while the app was closed), then its progress.
        val info = info()
        val code = info?.availableVersionCode()?.toLong()
        send(info?.toStoreUpdate())
        val listener = InstallStateUpdatedListener { state ->
            val status = state.installStatus()
            if (code != null && status in REPORTED) trySend(fromStatus(status, code))
        }
        manager.registerListener(listener)
        awaitClose { manager.unregisterListener(listener) }
    }

    override suspend fun start() {
        val activity = activities.current ?: return
        val info = info()?.takeIf { it.flexibleAvailable() } ?: return
        manager.startUpdateFlowForResult(
            info,
            activity,
            AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
            REQUEST_CODE
        )
    }

    override suspend fun complete() {
        manager.completeUpdate()
    }

    /** Null when Play cannot answer (no Play Store, no network, not a Play install). */
    private suspend fun info(): AppUpdateInfo? = runCatching { manager.requestAppUpdateInfo() }.getOrNull()

    private companion object {
        /** The result is not needed: the download's progress arrives through the listener. */
        const val REQUEST_CODE = 0x0715

        fun AppUpdateInfo.flexibleAvailable() =
            updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

        fun AppUpdateInfo.toStoreUpdate(): StoreUpdate? {
            val code = availableVersionCode().toLong()
            return when (installStatus()) {
                InstallStatus.DOWNLOADED -> StoreUpdate.Downloaded(code)
                InstallStatus.PENDING, InstallStatus.DOWNLOADING -> StoreUpdate.Downloading(code)
                else -> StoreUpdate.Available(code).takeIf { flexibleAvailable() }
            }
        }

        /** The download statuses that change what Home shows. */
        val REPORTED = setOf(
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            InstallStatus.DOWNLOADED,
            InstallStatus.CANCELED,
            InstallStatus.FAILED,
            InstallStatus.INSTALLING,
            InstallStatus.INSTALLED,
        )

        /** What a [REPORTED] status means for update [code]. */
        fun fromStatus(status: Int, code: Long): StoreUpdate? = when (status) {
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> StoreUpdate.Downloading(code)
            InstallStatus.DOWNLOADED -> StoreUpdate.Downloaded(code)
            // The user cancelled it or it failed: offer it again.
            InstallStatus.CANCELED, InstallStatus.FAILED -> StoreUpdate.Available(code)
            else -> null
        }
    }
}
