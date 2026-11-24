@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.platform

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberBackupFolderPicker(onPicked: (folder: String, label: String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val onPickedState = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
                val label = DocumentsContract.getTreeDocumentId(uri).substringAfter(':').ifBlank { "Selected folder" }
                onPickedState.value(uri.toString(), label)
            }
        }
    return remember(launcher) { { launcher.launch(null) } }
}
