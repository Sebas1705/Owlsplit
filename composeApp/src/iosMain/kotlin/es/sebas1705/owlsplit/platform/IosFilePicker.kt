package es.sebas1705.owlsplit.platform

import es.sebas1705.owlsplit.core.ui.components.FilePicker
import es.sebas1705.owlsplit.core.ui.components.PickedFile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** The iOS document picker, with a copy of the chosen file (no security-scoped access needed). */
class IosFilePicker : FilePicker {
    // UIKit holds its delegate weakly: keep it alive until the person picks or cancels.
    private var delegate: PickerDelegate? = null

    override suspend fun pickText(): PickedFile? {
        val presenter = topViewController() ?: return null
        return suspendCancellableCoroutine { continuation ->
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeCommaSeparatedText, UTTypePlainText),
                asCopy = true,
            )
            val callback = PickerDelegate { file ->
                delegate = null
                continuation.resume(file)
            }
            delegate = callback
            picker.delegate = callback
            presenter.presentViewController(picker, animated = true, completion = null)
        }
    }

    private class PickerDelegate(private val done: (PickedFile?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            done((didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(::read))
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = done(null)

        @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
        private fun read(url: NSURL): PickedFile? {
            val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null) ?: return null
            return PickedFile(url.lastPathComponent.orEmpty(), text)
        }
    }
}
