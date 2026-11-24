package app.tandemforactual.ui.core.platform

import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.MAX_CRASH_REPORTS
import app.tandemforactual.ui.core.session.crashReportText
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice

/** Keeps the last few Kotlin crash reports in Application Support. */
@OptIn(ExperimentalForeignApi::class)
class IosCrashReports : CrashReports {
    private val directory: String =
        NSFileManager.defaultManager
            .URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
            ?.path
            .orEmpty() + "/crash-reports"

    @OptIn(ExperimentalNativeApi::class)
    fun install() {
        setUnhandledExceptionHook { error -> runCatching { record(error) } }
    }

    private fun record(error: Throwable) {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
        val millis = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val text =
            crashReportText(
                appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown",
                platform = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
                device = UIDevice.currentDevice.model,
                thread = "main",
                occurredAt = millis.toString(),
                stackTrace = error.stackTraceToString(),
            )
        NSString.create(string = text).writeToFile("$directory/crash-$millis.txt", true, NSUTF8StringEncoding, null)
        files().drop(MAX_CRASH_REPORTS).forEach { NSFileManager.defaultManager.removeItemAtPath("$directory/$it", null) }
    }

    private fun files(): List<String> =
        NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(directory, null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.startsWith("crash-") }
            .sortedDescending()

    override fun latest(): String? =
        files().firstOrNull()?.let {
            NSString.stringWithContentsOfFile("$directory/$it", NSUTF8StringEncoding, null)
        }

    override fun count(): Int = files().size

    override fun clear() {
        files().forEach { NSFileManager.defaultManager.removeItemAtPath("$directory/$it", null) }
    }
}

internal fun shareTextOnIos(text: String) {
    val controller = UIActivityViewController(listOf(text), null)
    UIApplication.sharedApplication.keyWindow
        ?.rootViewController
        ?.presentViewController(controller, true, null)
}
