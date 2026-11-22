@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package app.tandemforactual.ui.core.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

@Composable
internal actual fun rememberFileSharer(): (name: String, mimeType: String, contents: String) -> Unit =
    remember {
        { name, _, contents ->
            val path = NSTemporaryDirectory() + name
            NSString.create(string = contents).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            val sheet = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
            var presenter: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
            presenter?.presentViewController(sheet, animated = true, completion = null)
        }
    }
