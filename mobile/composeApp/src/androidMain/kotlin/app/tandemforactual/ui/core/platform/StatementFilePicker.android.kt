@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun rememberStatementFilePicker(
    binary: Boolean,
    onPicked: (PickedStatementFile) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onPickedState = rememberUpdatedState(onPicked)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val resolver = context.contentResolver
                scope.launch {
                    val file = withContext(Dispatchers.IO) { readStatementFile(resolver, uri, binary) }
                    withContext(Dispatchers.Main) { onPickedState.value(file) }
                }
            }
        }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    "text/*",
                    "application/json",
                    "application/octet-stream",
                    "application/x-ofx",
                    "application/x-qfx",
                    "application/vnd.intu.qfx",
                ),
            )
        }
    }
}

internal fun readStatementFile(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri,
    binary: Boolean = false,
): PickedStatementFile {
    val name = resolver.displayName(uri) ?: "statement.csv"
    return try {
        val limit = if (binary) MAX_BINARY_FILE_BYTES else MAX_STATEMENT_IMPORT_BYTES
        val bytes =
            resolver.openInputStream(uri)?.use { it.readBoundedStatementBytes(limit) }
                ?: return PickedStatementFile(name, null, "Couldn’t read this file. Choose another statement.")
        if (binary) PickedStatementFile(name, null, bytes = bytes) else PickedStatementFile(name, decodeStatementBytes(bytes))
    } catch (_: StatementFileTooLargeException) {
        PickedStatementFile(name, null, "Choose a statement smaller than 10 MB.")
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        PickedStatementFile(name, null, "Couldn’t read this file. Choose another statement.")
    }
}

private fun InputStream.readBoundedStatementBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) throw StatementFileTooLargeException()
        output.write(buffer, 0, count)
    }
}

private class StatementFileTooLargeException : Exception()
