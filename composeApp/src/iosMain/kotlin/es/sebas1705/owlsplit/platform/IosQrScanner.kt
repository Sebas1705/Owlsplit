package es.sebas1705.owlsplit.platform

import es.sebas1705.owlsplit.core.ui.components.QrScanner
import es.sebas1705.owlsplit.resources.Res
import es.sebas1705.owlsplit.resources.close
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getString
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * Reads a QR code with the camera (AVFoundation): a full-screen camera with a close button.
 * Needs NSCameraUsageDescription in Info.plist; a denied permission reads as "nothing scanned".
 */
@OptIn(ExperimentalForeignApi::class)
class IosQrScanner : QrScanner {
    override val isAvailable: Boolean
        get() = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) != null

    override suspend fun scan(): String? {
        if (!cameraAllowed()) return null
        val presenter = topViewController() ?: return null
        val closeLabel = getString(Res.string.close)
        return suspendCancellableCoroutine { cont ->
            var done = false
            val scanner = ScannerScreen(closeLabel) { result ->
                if (!done) {
                    done = true
                    cont.resume(result)
                }
            }
            presenter.presentViewController(scanner.controller, animated = true, completion = null)
            cont.invokeOnCancellation { scanner.close(null) }
        }
    }

    private suspend fun cameraAllowed(): Boolean =
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { cont ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) { cont.resume(granted) }
                }
            }
            else -> false
        }
}

/** The camera screen: a session with a QR metadata output and a preview layer. */
@OptIn(ExperimentalForeignApi::class)
private class ScannerScreen(closeLabel: String, private val onResult: (String?) -> Unit) {
    val controller = UIViewController(nibName = null, bundle = null)
    private val session = AVCaptureSession()
    private val delegate = MetadataDelegate { code -> close(code) }

    init {
        controller.modalPresentationStyle = UIModalPresentationFullScreen
        controller.view.backgroundColor = UIColor.blackColor
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        val input = camera?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        if (input != null && session.canAddInput(input)) session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (session.canAddOutput(output)) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }
        val preview = AVCaptureVideoPreviewLayer(session = session)
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        preview.frame = UIScreen.mainScreen.bounds
        controller.view.layer.addSublayer(preview)

        val closeButton = UIButton.buttonWithType(UIButtonTypeSystem)
        closeButton.setTitle(closeLabel, forState = UIControlStateNormal)
        closeButton.setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
        closeButton.addAction(
            UIAction.actionWithHandler { close(null) },
            forControlEvents = UIControlEventTouchUpInside
        )
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(closeButton)
        closeButton.leadingAnchor.constraintEqualToAnchor(controller.view.safeAreaLayoutGuide.leadingAnchor, constant = 16.0).active = true
        closeButton.topAnchor.constraintEqualToAnchor(controller.view.safeAreaLayoutGuide.topAnchor, constant = 8.0).active = true

        // startRunning blocks: off the main thread.
        dispatch_async(dispatch_get_global_queue(0, 0u)) { session.startRunning() }
    }

    fun close(result: String?) {
        dispatch_async(dispatch_get_global_queue(0, 0u)) { session.stopRunning() }
        controller.dismissViewControllerAnimated(true) { onResult(result) }
    }
}

private class MetadataDelegate(private val onCode: (String) -> Unit) :
    NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var delivered = false

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        if (delivered) return
        val code = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        val text = code.stringValue ?: return
        delivered = true
        onCode(text)
    }
}
