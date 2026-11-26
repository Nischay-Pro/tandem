package app.tandemforactual.android

import android.content.Context
import android.content.Intent
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.database.tandemDatabaseBuilder
import java.util.concurrent.atomic.AtomicBoolean

/** State that lives as long as the process, so recreating the Activity never closes the database under running work. */
internal object TandemProcess {
    @Volatile
    private var database: TandemDatabase? = null
    private val crashHandlerInstalled = AtomicBoolean(false)

    fun database(context: Context): TandemDatabase =
        database ?: synchronized(this) {
            database ?: openTandemDatabase(tandemDatabaseBuilder(context.applicationContext)).also { database = it }
        }

    /** Wraps the default handler once per process, however many times the Activity is created. */
    fun installCrashHandler(record: (threadName: String, error: Throwable) -> Unit): Boolean {
        if (!crashHandlerInstalled.compareAndSet(false, true)) return false
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
        return true
    }
}

/** A launch intent is acted on once: not again when the Activity is restored or reopened from Recents. */
internal fun isFreshLaunchIntent(
    restored: Boolean,
    flags: Int,
): Boolean = !restored && flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
