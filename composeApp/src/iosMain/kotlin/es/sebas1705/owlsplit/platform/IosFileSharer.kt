package es.sebas1705.owlsplit.platform

import es.sebas1705.owlsplit.core.ui.components.FileSharer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

/** Writes the file to the temporary folder and opens the iOS share sheet with it. */
class IosFileSharer : FileSharer {
    @OptIn(ExperimentalForeignApi::class)
    override fun share(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
        val presenter = topViewController() ?: return false
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        if (!data.writeToURL(url, atomically = true)) return false
        val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
        // On iPad the sheet is a popover and needs an anchor.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
        return true
    }
}
