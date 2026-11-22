package app.tandemforactual.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
internal actual fun rememberFileSharer(): (name: String, mimeType: String, contents: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { name, mimeType, contents ->
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            folder.listFiles()?.forEach(File::delete)
            val file = File(folder, name).apply { writeText(contents) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.receipts", file)
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(mimeType)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
