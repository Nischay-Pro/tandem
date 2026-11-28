package app.tandemforactual.ui.app

import android.content.Context
import android.net.Uri
import app.tandemforactual.ui.core.platform.DroppedFileKind
import app.tandemforactual.ui.core.platform.displayName
import app.tandemforactual.ui.core.platform.droppedFileKind
import app.tandemforactual.ui.core.platform.readStatementFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Takes a file shared into Tandem: a photo opens a new transaction filled from the receipt, and a statement opens
 * Accounts to choose where it's imported. Returns false for files Tandem doesn't read.
 */
fun receiveSharedFile(
    context: Context,
    uri: Uri,
    mimeType: String?,
    scope: CoroutineScope,
): Boolean {
    val resolver = context.contentResolver
    return when (droppedFileKind(mimeType ?: resolver.getType(uri), resolver.displayName(uri))) {
        DroppedFileKind.IMAGE -> {
            QuickActionEvents.shareReceiptPhoto(uri.toString())
            true
        }

        DroppedFileKind.STATEMENT -> {
            scope.launch { QuickActionEvents.shareStatement(withContext(Dispatchers.IO) { readStatementFile(resolver, uri) }) }
            true
        }

        null -> {
            false
        }
    }
}
