@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun rememberStatementFilePicker(onPicked: (PickedStatementFile) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onPickedState = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val resolver = context.contentResolver
                scope.launch {
                    val file = withContext(Dispatchers.IO) { readStatementFile(resolver, uri) }
                    withContext(Dispatchers.Main) { onPickedState.value(file) }
                }
            }
        }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf("text/*", "application/octet-stream", "application/x-ofx", "application/x-qfx", "application/vnd.intu.qfx"),
            )
        }
    }
}

private fun readStatementFile(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
): PickedStatementFile {
    val name =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "statement.csv"
    return try {
        val bytes =
            resolver.openInputStream(uri)?.use(InputStream::readBoundedStatementBytes)
                ?: return PickedStatementFile(name, null, "Couldn’t read this file. Choose another statement.")
        PickedStatementFile(name, decodeStatementBytes(bytes))
    } catch (_: StatementFileTooLargeException) {
        PickedStatementFile(name, null, "Choose a statement smaller than 10 MB.")
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        PickedStatementFile(name, null, "Couldn’t read this file. Choose another statement.")
    }
}

private fun InputStream.readBoundedStatementBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > MAX_STATEMENT_IMPORT_BYTES) throw StatementFileTooLargeException()
        output.write(buffer, 0, count)
    }
}

private class StatementFileTooLargeException : Exception()
