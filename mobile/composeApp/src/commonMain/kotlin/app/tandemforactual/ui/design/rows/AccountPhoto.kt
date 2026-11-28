@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)

package app.tandemforactual.ui.design.rows

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Where an account's photo is kept: per budget, since account ids are only unique within one. */
internal fun accountPhotoKey(
    budgetId: String,
    accountId: String,
) = "$budgetId/$accountId"

internal fun encodeAccountPhoto(jpeg: ByteArray): String = Base64.encode(jpeg)

/** The saved photo as an image, or null when it can't be read. */
internal fun decodeAccountPhoto(encoded: String): ImageBitmap? = runCatching { Base64.decode(encoded).decodeToImageBitmap() }.getOrNull()

/** An account photo filling its icon tile, which clips it to the tile's corners. */
@Composable
internal fun AccountPhotoFill(photo: ImageBitmap) {
    Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
}
