@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.tandemforactual.ui.feature.transactions.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import app.tandemforactual.ui.core.platform.topViewController
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIModalPresentationFullScreen
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.darwin.NSObject

@Composable
internal actual fun rememberReceiptScanner(
    onStarted: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    onResult: (ReceiptScanResult) -> Unit,
): ReceiptScanner {
    val scope = rememberCoroutineScope()
    val started = rememberUpdatedState(onStarted)
    val result = rememberUpdatedState(onResult)
    val delegate = remember { ReceiptPickerDelegate(scope, { started.value(null) }) { result.value(it) } }
    return remember(delegate) {
        ReceiptScanner(
            canTakePhoto = UIImagePickerController.isSourceTypeAvailable(CAMERA),
            takePhoto = delegate::takePhoto,
            choosePhoto = delegate::choosePhoto,
        )
    }
}

private class ReceiptPickerDelegate(
    private val scope: CoroutineScope,
    private val onStarted: () -> Unit,
    private val onResult: (ReceiptScanResult) -> Unit,
) : NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol,
    PHPickerViewControllerDelegateProtocol {
    fun takePhoto() {
        val camera = UIImagePickerController()
        camera.sourceType = CAMERA
        camera.modalPresentationStyle = UIModalPresentationFullScreen
        camera.delegate = this
        topViewController()?.presentViewController(camera, animated = true, completion = null)
    }

    fun choosePhoto() {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 1
        val library = PHPickerViewController(configuration)
        library.delegate = this
        topViewController()?.presentViewController(library, animated = true, completion = null)
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        (didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)?.let(::read)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }

    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider ?: return
        if (!provider.hasItemConformingToTypeIdentifier(IMAGE_TYPE)) return
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
            val image = data?.let { UIImage.imageWithData(it) }
            scope.launch {
                if (image != null) {
                    read(image)
                } else {
                    onResult(ReceiptScanResult(null, "Couldn’t open this photo. Try another one."))
                }
            }
        }
    }

    private fun read(image: UIImage) {
        onStarted()
        scope.launch {
            val lines = withContext(Dispatchers.Default) { recognize(image) }
            onResult(lines?.let { ReceiptScanResult(it) } ?: ReceiptScanResult(null, "Couldn’t read this receipt. Try a sharper photo."))
        }
    }
}

private const val IMAGE_TYPE = "public.image"

private val CAMERA = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera

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
