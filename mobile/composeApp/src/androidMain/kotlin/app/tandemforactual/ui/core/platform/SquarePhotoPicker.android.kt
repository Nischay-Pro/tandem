package app.tandemforactual.ui.core.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberSquarePhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked = rememberUpdatedState(onPicked)
    val library =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch { picked.value(withContext(Dispatchers.Default) { runCatching { squareJpeg(context, uri) }.getOrNull() }) }
            }
        }
    return remember(library) { { library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/** The photo turned upright, cropped to its centred square and scaled to [SQUARE_PHOTO_SIZE], as JPEG. */
private fun squareJpeg(
    context: Context,
    uri: Uri,
): ByteArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val shortest = minOf(bounds.outWidth, bounds.outHeight)
    if (shortest <= 0) return null
    var sample = 1
    while (shortest / (sample * 2) >= SQUARE_PHOTO_SIZE) sample *= 2
    val decoded =
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
    val side = minOf(decoded.width, decoded.height)
    val scale = SQUARE_PHOTO_SIZE.toFloat() / side
    val matrix =
        Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation(context, uri))
        }
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true)
    return ByteArrayOutputStream().use { out ->
        square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
}

private fun rotation(
    context: Context,
    uri: Uri,
): Float =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrNull() ?: 0f

private const val JPEG_QUALITY = 85
