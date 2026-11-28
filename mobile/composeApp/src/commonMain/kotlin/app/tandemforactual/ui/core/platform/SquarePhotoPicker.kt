package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable

/** The side, in pixels, of the square photo [rememberSquarePhotoPicker] hands back. */
internal const val SQUARE_PHOTO_SIZE = 256

/** Lets the person choose a photo, then hands it back cropped to a centred square as JPEG bytes, or null when unreadable. */
@Composable
internal expect fun rememberSquarePhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit
