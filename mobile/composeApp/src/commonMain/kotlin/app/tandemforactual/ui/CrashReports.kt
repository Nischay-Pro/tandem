package app.tandemforactual.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Crash reports kept on this device; nothing is sent unless the person shares one. */
interface CrashReports {
    fun latest(): String?

    fun count(): Int

    fun clear()
}

internal val LocalCrashReports = staticCompositionLocalOf<CrashReports?> { null }

internal val LocalShareText = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal const val MAX_CRASH_REPORTS = 5

/** Plain-text report: versions, device and stack trace only — no budget data is ever added. */
fun crashReportText(
    appVersion: String,
    platform: String,
    device: String,
    thread: String,
    occurredAt: String,
    stackTrace: String,
): String =
    buildString {
        appendLine("Tandem crash report")
        appendLine("App version: $appVersion")
        appendLine("Platform: $platform")
        appendLine("Device: $device")
        appendLine("Thread: $thread")
        appendLine("Time: $occurredAt")
        appendLine()
        append(stackTrace.take(MAX_STACK_TRACE_CHARS))
    }

private const val MAX_STACK_TRACE_CHARS = 20_000
