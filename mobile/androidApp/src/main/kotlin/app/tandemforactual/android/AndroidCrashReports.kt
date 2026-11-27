package app.tandemforactual.android

import android.content.Context
import android.os.Build
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.crashReportText
import java.io.File
import java.time.Instant

/** Keeps the last few crash reports in app-private storage. */
class AndroidCrashReports(
    context: Context,
) : CrashReports {
    private val directory = File(context.applicationContext.filesDir, "crash-reports")
    private val appVersion =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

    internal fun record(
        threadName: String,
        error: Throwable,
    ) {
        directory.mkdirs()
        val now = Instant.now()
        File(directory, "crash-${now.toEpochMilli()}.txt").writeText(
            crashReportText(
                appVersion = appVersion,
                platform = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
                thread = threadName,
                occurredAt = now.toString(),
                stackTrace = error.stackTraceToString(),
            ),
        )
        files().drop(MAX_REPORTS).forEach(File::delete)
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.name.startsWith("crash-") }?.sortedByDescending { it.name }.orEmpty()

    override fun latest(): String? = files().firstOrNull()?.readText()

    override fun count(): Int = files().size

    override fun clear() {
        files().forEach(File::delete)
    }

    private companion object {
        const val MAX_REPORTS = 5
    }
}
