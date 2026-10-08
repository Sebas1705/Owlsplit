package es.sebas1705.owlsplit.share

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import es.sebas1705.owlsplit.account.ResumedActivity
import es.sebas1705.owlsplit.core.ui.components.QrScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google code scanner (Play services): its own camera screen, no camera permission for the app.
 * The scanning module is downloaded on first use; until it is there, the first scan may fail.
 */
class AndroidQrScanner(context: Context, private val activities: ResumedActivity) : QrScanner {
    private val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()

    init {
        val scanner = GmsBarcodeScanning.getClient(context, options)
        ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
    }

    override val isAvailable: Boolean = true

    override suspend fun scan(): String? {
        val activity = activities.current ?: return null
        return suspendCancellableCoroutine { cont ->
            GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener { cont.resume(it.rawValue) }
                .addOnCanceledListener { cont.resume(null) }
                .addOnFailureListener { cont.resume(null) }
        }
    }
}
