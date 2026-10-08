package es.sebas1705.owlsplit.platform

import es.sebas1705.owlsplit.core.ui.components.TextSharer
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

/** The iOS share sheet. */
class IosTextSharer : TextSharer {
    override fun share(text: String): Boolean {
        val presenter = topViewController() ?: return false
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // On iPad the sheet is a popover and needs an anchor.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
        return true
    }
}
