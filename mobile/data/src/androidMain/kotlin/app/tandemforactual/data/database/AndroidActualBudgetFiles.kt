package app.tandemforactual.data.database

import android.content.Context
import android.util.AtomicFile
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import java.io.File

/** Keeps each Actual budget in `noBackupFilesDir`, written atomically so an interrupted download leaves no partial file. */
class AndroidActualBudgetFiles(
    context: Context,
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
) : ActualBudgetFiles {
    private val root = context.applicationContext.noBackupFilesDir.resolve("actual")

    private fun file(budgetId: String): File = root.resolve(budgetFileName(budgetId))

    override fun open(budgetId: String): ActualDatabase? =
        file(budgetId)
            .takeIf {
                it.isFile
            }?.let { ActualDatabase(driver.open(it.absolutePath)) }

    override fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase = install(budgetId) { it.write(bytes) }

    @Synchronized
    internal fun install(
        budgetId: String,
        write: (java.io.OutputStream) -> Unit,
    ): ActualDatabase {
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "Could not create the budget storage directory." }
        val target = file(budgetId)
        remove(budgetId)
        val destination = AtomicFile(target)
        val output = destination.startWrite()
        try {
            write(output)
            destination.finishWrite(output)
        } catch (error: Throwable) {
            destination.failWrite(output)
            throw error
        }
        return ActualDatabase(driver.open(target.absolutePath))
    }

    override fun snapshot(
        budgetId: String,
        write: (path: String) -> Unit,
    ): ByteArray {
        check(root.isDirectory || root.mkdirs() || root.isDirectory) { "Could not create the budget storage directory." }
        val copy = File(file(budgetId).path + ".snapshot").apply { delete() }
        try {
            write(copy.absolutePath)
            return copy.readBytes()
        } finally {
            copy.delete()
        }
    }

    override fun remove(budgetId: String) {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file(budgetId).path + it).delete() }
    }

    @Synchronized
    override fun retire(
        budgetId: String,
        label: String,
    ) {
        val backup = root.resolve("backups").apply { mkdirs() }.resolve("$budgetId-${budgetFileName(label)}")
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val source = File(file(budgetId).path + suffix)
            if (source.exists()) check(source.renameTo(File(backup.path + suffix))) { "Could not keep a backup of this budget." }
        }
    }
}
