@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.platform

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import android.view.DragAndDropPermissions
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalContext
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DROP_TINT_ALPHA = 0.1f

@Composable
internal actual fun rememberFileDropTarget(
    onStatement: ((PickedStatementFile) -> Unit)?,
    onImage: ((String) -> Unit)?,
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statement by rememberUpdatedState(onStatement)
    val image by rememberUpdatedState(onImage)
    val tint = tandemPalette.accent
    var hovering by remember { mutableStateOf(false) }
    val permissions = remember { DropPermissions() }
    DisposableEffect(permissions) { onDispose { permissions.release() } }
    val target =
        remember(context) {
            object : DragAndDropTarget {
                override fun onEntered(event: DragAndDropEvent) {
                    hovering = true
                }

                override fun onExited(event: DragAndDropEvent) {
                    hovering = false
                }

                override fun onEnded(event: DragAndDropEvent) {
                    hovering = false
                }

                override fun onDrop(event: DragAndDropEvent): Boolean {
                    hovering = false
                    val drag = event.toAndroidDragEvent()
                    val uri =
                        drag.clipData
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.uri ?: return false
                    permissions.hold(context.findActivity()?.requestDragAndDropPermissions(drag))
                    val resolver = context.contentResolver
                    scope.launch {
                        val kind = withContext(Dispatchers.IO) { droppedFileKind(resolver.getType(uri), resolver.displayName(uri)) }
                        when (kind) {
                            DroppedFileKind.IMAGE -> image?.invoke(uri.toString())
                            DroppedFileKind.STATEMENT ->
                                statement?.let { onDropped -> onDropped(withContext(Dispatchers.IO) { readStatementFile(resolver, uri) }) }
                            null -> Unit
                        }
                    }
                    return true
                }
            }
        }
    if (onStatement == null && onImage == null) return Modifier
    return Modifier
        .dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().any { mayAcceptDrop(it, statements = statement != null, images = image != null) }
            },
            target = target,
        ).drawWithContent {
            drawContent()
            if (hovering) drawRect(tint, alpha = DROP_TINT_ALPHA)
        }
}

/** Read access to the last dropped file, kept until the next drop so the receipt reader can still open it. */
private class DropPermissions {
    private var current: DragAndDropPermissions? = null

    fun hold(permissions: DragAndDropPermissions?) {
        current?.release()
        current = permissions
    }

    fun release() = hold(null)
}

internal fun ContentResolver.displayName(uri: Uri): String? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
