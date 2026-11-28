@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
internal actual fun rememberSquarePhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val picked = rememberUpdatedState(onPicked)
    val delegate = remember { SquarePhotoDelegate(scope) { picked.value(it) } }
    return remember(delegate) { delegate::choose }
}

private class SquarePhotoDelegate(
    private val scope: CoroutineScope,
    private val onPicked: (ByteArray?) -> Unit,
) : NSObject(),
    PHPickerViewControllerDelegateProtocol {
    fun choose() {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 1
        val library = PHPickerViewController(configuration)
        library.delegate = this
        topViewController()?.presentViewController(library, animated = true, completion = null)
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
            scope.launch { onPicked(image?.let { withContext(Dispatchers.Default) { squareJpeg(it) } }) }
        }
    }
}

private const val IMAGE_TYPE = "public.image"
private const val JPEG_QUALITY = 0.85

/** The photo upright, cropped to its centred square and drawn at [SQUARE_PHOTO_SIZE], as JPEG. */
private fun squareJpeg(image: UIImage): ByteArray? {
    val (width, height) = image.size.useContents { width to height }
    if (width <= 0 || height <= 0) return null
    val side = SQUARE_PHOTO_SIZE.toDouble()
    val scale = side / minOf(width, height)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(side, side), true, 1.0)
    image.drawInRect(CGRectMake((side - width * scale) / 2, (side - height * scale) / 2, width * scale, height * scale))
    val square = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return square?.let { UIImageJPEGRepresentation(it, JPEG_QUALITY) }?.toByteArray()
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
