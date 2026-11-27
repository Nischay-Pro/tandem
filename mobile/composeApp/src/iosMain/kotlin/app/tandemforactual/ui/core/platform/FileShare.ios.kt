@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

@Composable
internal actual fun rememberFileSharer(): (name: String, mimeType: String, contents: ByteArray) -> Unit =
    remember {
        { name, _, contents ->
            val path = NSTemporaryDirectory() + name
            val data =
                if (contents.isEmpty()) {
                    NSData()
                } else {
                    contents.usePinned { NSData.create(bytes = it.addressOf(0), length = contents.size.toULong()) }
                }
            data.writeToFile(path, atomically = true)
            presentShareSheet(listOf(NSURL.fileURLWithPath(path)))
        }
    }

/**
 * Shows the system share sheet from the topmost screen. On iPad it's a popover, which needs an anchor or UIKit
 * throws, so it points at the middle of that screen.
 */
internal fun presentShareSheet(items: List<Any>) {
    val presenter = topViewController() ?: return
    val view = presenter.view
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    sheet.popoverPresentationController?.let { popover ->
        popover.sourceView = view
        popover.sourceRect = view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
        popover.permittedArrowDirections = 0u
    }
    presenter.presentViewController(sheet, animated = true, completion = null)
}
