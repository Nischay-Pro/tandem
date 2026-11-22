package app.tandemforactual.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal actual fun rememberReceiptScanner(
    onStarted: (androidx.compose.ui.graphics.ImageBitmap?) -> Unit,
    onResult: (ReceiptScanResult) -> Unit,
): ReceiptScanner {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val started = rememberUpdatedState(onStarted)
    val result = rememberUpdatedState(onResult)
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val read: (Uri) -> Unit = { uri ->
        started.value(null)
        scope.launch {
            val photo = withContext(Dispatchers.Default) { runCatching { loadUpright(context, uri) }.getOrNull() }
            if (photo == null) {
                result.value(ReceiptScanResult(null, "Couldn’t open this photo. Choose another one."))
                return@launch
            }
            started.value(photo.asImageBitmap())
            result.value(readReceipt(context, photo))
        }
    }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            photoUri?.takeIf { saved }?.let { read(Uri.parse(it)) }
        }
    val library =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(read) }
    val canTakePhoto = remember(context) { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }
    return remember(camera, library, canTakePhoto) {
        ReceiptScanner(
            canTakePhoto = canTakePhoto,
            takePhoto = {
                val folder = File(context.cacheDir, "receipts").apply { mkdirs() }
                folder.listFiles()?.forEach(File::delete)
                val file = File(folder, "receipt-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.receipts", file)
                photoUri = uri.toString()
                runCatching { camera.launch(uri) }.onFailure {
                    result.value(ReceiptScanResult(null, "No camera app is available. Choose a photo instead."))
                }
            },
            choosePhoto = {
                library.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
}

private suspend fun readReceipt(
    context: Context,
    photo: Bitmap,
): ReceiptScanResult =
    withContext(Dispatchers.Default) {
        try {
            ReceiptReader(context).use { reader -> ReceiptScanResult(reader.read(photo)) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: UnsatisfiedLinkError) {
            ReceiptScanResult(null, "Receipt reading isn’t available on this device.")
        } catch (_: Exception) {
            ReceiptScanResult(null, "Couldn’t read this receipt. Try a sharper photo.")
        }
    }

/** Decodes the photo turned upright and no larger than the reader needs. */
private fun loadUpright(
    context: Context,
    uri: Uri,
): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_RECEIPT_EDGE) sample *= 2
    val decoded =
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
    val rotation =
        runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
    val scale = minOf(1f, MAX_RECEIPT_EDGE.toFloat() / maxOf(decoded.width, decoded.height))
    if (rotation == 0f && scale == 1f) return decoded
    val matrix =
        Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (upright !== decoded) decoded.recycle()
    return upright
}

private const val MAX_RECEIPT_EDGE = 2_000
