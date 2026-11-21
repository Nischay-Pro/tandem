package app.tandemforactual.data.database

import android.content.Context
import android.util.AtomicFile
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Holds the two private, backup-excluded files for one direct Actual installation. */
class AndroidDirectBudgetFiles private constructor(
    private val context: Context,
    val installationId: String,
    directory: File,
    private val driver: SQLiteDriver,
) {
    private val actualFile = directory.resolve("actual.sqlite")
    private val cacheFile = directory.resolve("mobile.db")

    fun installActual(bytes: ByteArray): ActualDatabase = installActual { it.write(bytes) }

    @Synchronized
    internal fun installActual(write: (OutputStream) -> Unit): ActualDatabase {
        check(!actualFile.exists()) { "This installation already has an Actual database." }
        val destination = AtomicFile(actualFile)
        val output = destination.startWrite()
        try {
            write(output)
            destination.finishWrite(output)
        } catch (error: Throwable) {
            destination.failWrite(output)
            throw error
        }
        return openActual()
    }

    fun openActual(): ActualDatabase {
        check(actualFile.isFile) { "The local Actual database is missing." }
        return ActualDatabase(driver.open(actualFile.absolutePath))
    }

    fun openMobileCache(): TandemDatabase = openTandemDatabase(tandemDatabaseBuilder(context, cacheFile), driver)

    companion object {
        fun create(
            context: Context,
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): AndroidDirectBudgetFiles {
            val app = context.applicationContext
            val root = app.noBackupFilesDir.resolve("direct-actual")
            check(root.isDirectory || root.mkdirs() || root.isDirectory) { "Could not create the direct budget storage directory." }
            val installationId = UUID.randomUUID().toString()
            val directory = root.resolve(installationId)
            check(directory.mkdir()) { "Could not create a new direct budget installation." }
            return AndroidDirectBudgetFiles(app, installationId, directory, driver)
        }

        fun reopen(
            context: Context,
            installationId: String,
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): AndroidDirectBudgetFiles {
            require(runCatching { UUID.fromString(installationId).toString() }.getOrNull() == installationId) {
                "Invalid direct budget installation ID."
            }
            val app = context.applicationContext
            val directory = app.noBackupFilesDir.resolve("direct-actual").resolve(installationId)
            check(directory.resolve("actual.sqlite").isFile && directory.resolve("mobile.db").isFile) {
                "The direct budget installation is incomplete."
            }
            return AndroidDirectBudgetFiles(app, installationId, directory, driver)
        }
    }
}
