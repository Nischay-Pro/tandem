@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject

@Composable
internal actual fun rememberReceiptScanner(
    onStarted: () -> Unit,
    onResult: (ReceiptScanResult) -> Unit,
): ReceiptScanner {
    val scope = rememberCoroutineScope()
    val started = rememberUpdatedState(onStarted)
    val result = rememberUpdatedState(onResult)
    val delegate = remember { ReceiptPickerDelegate(scope, { started.value() }) { result.value(it) } }
    return remember(delegate) {
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        ReceiptScanner(
            canTakePhoto = UIImagePickerController.isSourceTypeAvailable(camera),
            takePhoto = { delegate.present(camera) },
            choosePhoto = { delegate.present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary) },
        )
    }
}

private class ReceiptPickerDelegate(
    private val scope: CoroutineScope,
    private val onStarted: () -> Unit,
    private val onResult: (ReceiptScanResult) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    fun present(source: UIImagePickerControllerSourceType) {
        val picker = UIImagePickerController()
        picker.sourceType = source
        picker.delegate = this
        var presenter: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        presenter?.presentViewController(picker, animated = true, completion = null)
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage ?: return
        onStarted()
        scope.launch {
            val lines = withContext(Dispatchers.Default) { recognize(image) }
            onResult(lines?.let { ReceiptScanResult(it) } ?: ReceiptScanResult(null, "Couldn’t read this receipt. Try a sharper photo."))
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun recognize(image: UIImage): List<String>? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0 || height <= 0) return null
    val scale = minOf(1.0, 2_400 / maxOf(width, height))
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width * scale, height * scale), true, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    val upright = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    val cgImage = upright?.CGImage ?: return null
    val request = VNRecognizeTextRequest()
    request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    request.usesLanguageCorrection = true
    val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any>())
    if (!handler.performRequests(listOf(request), null)) return null
    val observations =
        request.results.orEmpty().filterIsInstance<VNRecognizedTextObservation>().mapNotNull { observation ->
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return@mapNotNull null
            val box = observation.boundingBox.useContents { Triple(1 - (origin.y + size.height / 2), size.height, origin.x) }
            text to box
        }
    val rows = mutableListOf<MutableList<Pair<String, Triple<Double, Double, Double>>>>()
    for (item in observations.sortedBy { it.second.first }) {
        val row = rows.lastOrNull()
        val first = row?.first()?.second
        if (first != null && kotlin.math.abs(first.first - item.second.first) < minOf(first.second, item.second.second) / 2) {
            row.add(item)
        } else {
            rows += mutableListOf(item)
        }
    }
    return rows.map { row -> row.sortedBy { it.second.third }.joinToString("  ") { it.first } }
}
