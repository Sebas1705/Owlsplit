package es.sebas1705.owlsplit.platform

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/** The view controller on top, to present system sheets (share, camera) from Compose. */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION") // The app has a single window; keyWindow is the simplest way to it.
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: return top
}
