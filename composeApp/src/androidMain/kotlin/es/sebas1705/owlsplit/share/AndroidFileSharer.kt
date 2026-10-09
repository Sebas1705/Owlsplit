package es.sebas1705.owlsplit.share

import android.content.Intent
import androidx.core.content.FileProvider
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.ui.components.FileSharer
import java.io.File

/**
 * Writes the file to the cache (cache/exports, see res/xml/file_paths.xml) and shares it through
 * a FileProvider, so the receiving app gets read access to that file only.
 */
class AndroidFileSharer(private val activities: ResumedActivity) : FileSharer {
    override fun share(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val activity = activities.current ?: return false
        val dir = File(activity.cacheDir, EXPORTS).apply { mkdirs() }
        // One export at a time: older ones are not needed once shared.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, fileName))
        return true
    }

    private companion object {
        const val EXPORTS = "exports"
    }
}
