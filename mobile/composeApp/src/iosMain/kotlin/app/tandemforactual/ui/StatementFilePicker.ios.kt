@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@Composable
@Suppress("ktlint:standard:function-naming")
internal actual fun rememberStatementFilePicker(onPicked: (PickedStatementFile) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val onPickedState = rememberUpdatedState(onPicked)
    val delegate =
        remember {
            StatementDocumentPickerDelegate(scope) { file -> onPickedState.value(file) }
        }
    return remember(delegate) {
        {
            val picker =
                UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(UTTypeData, UTTypePlainText),
                    asCopy = true,
                )
            picker.delegate = delegate
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            var presenter: UIViewController? = root
            while (presenter?.presentedViewController != null) {
                presenter = presenter.presentedViewController
            }
            presenter?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class StatementDocumentPickerDelegate(
    private val scope: CoroutineScope,
    private val onPicked: (PickedStatementFile) -> Unit,
) : NSObject(),
    UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        val path = url.path ?: return
        val filename = url.lastPathComponent ?: "statement.csv"
        scope.launch {
            val file = withContext(Dispatchers.Default) { readStatementFile(path, filename) }
            withContext(Dispatchers.Main) { onPicked(file) }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

@OptIn(ExperimentalForeignApi::class)
private fun readStatementFile(
    path: String,
    filename: String,
): PickedStatementFile {
    return try {
        val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)
        val fileSize = (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
        if (fileSize != null && fileSize > MAX_STATEMENT_IMPORT_BYTES) {
            return PickedStatementFile(filename, null, "Choose a statement smaller than 10 MB.")
        }
        val data =
            NSFileManager.defaultManager.contentsAtPath(path)
                ?: return PickedStatementFile(filename, null, "Couldn’t read this file. Choose another statement.")
        if (data.length.toLong() > MAX_STATEMENT_IMPORT_BYTES) {
            return PickedStatementFile(filename, null, "Choose a statement smaller than 10 MB.")
        }
        PickedStatementFile(filename, decodeStatementBytes(data.copyBytes()))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        PickedStatementFile(filename, null, "Couldn’t read this file. Choose another statement.")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun platform.Foundation.NSData.copyBytes(): ByteArray {
    val count = length.toInt()
    val source = bytes?.reinterpret<ByteVar>() ?: return ByteArray(0)
    return ByteArray(count) { index -> source[index] }
}
