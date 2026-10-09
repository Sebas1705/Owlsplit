package es.sebas1705.owlsplit.share

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.ui.components.FilePicker
import es.sebas1705.owlsplit.core.ui.components.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The system document picker (Storage Access Framework). The launcher is registered on the
 * activity's result registry for this one pick and removed afterwards.
 */
class AndroidFilePicker(private val activities: ResumedActivity) : FilePicker {
    private var picks = 0

    override suspend fun pickText(): PickedFile? {
        val activity = activities.current as? ComponentActivity ?: return null
        val uri = suspendCancellableCoroutine<Uri?> { continuation ->
            var launcher: ActivityResultLauncher<Array<String>>? = null
            launcher = activity.activityResultRegistry.register("pick-text-${picks++}", ActivityResultContracts.OpenDocument()) { picked ->
                launcher?.unregister()
                continuation.resume(picked)
            }
            continuation.invokeOnCancellation { launcher?.unregister() }
            launcher.launch(MIME_TYPES)
        } ?: return null
        return withContext(Dispatchers.IO) { read(activity, uri) }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Any unreadable file is "nothing picked".
    private fun read(activity: ComponentActivity, uri: Uri): PickedFile? = try {
        val resolver = activity.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > MAX_BYTES) null else PickedFile(name, bytes.decodeToString())
    } catch (e: Exception) {
        null
    }

    private companion object {
        // CSV files come with many types depending on the app that saved them.
        val MIME_TYPES = arrayOf(
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "application/csv",
            "application/vnd.ms-excel",
            "application/octet-stream",
        )
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}
