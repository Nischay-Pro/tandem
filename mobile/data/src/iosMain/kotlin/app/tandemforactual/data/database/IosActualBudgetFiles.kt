package app.tandemforactual.data.database

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.tandemforactual.data.actual.ActualDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.rename

/** Keeps each Actual budget in Application Support with iOS file protection and backup exclusion. */
@OptIn(ExperimentalForeignApi::class)
class IosActualBudgetFiles(
    private val driver: SQLiteDriver = BundledSQLiteDriver(),
) : ActualBudgetFiles {
    private val lock = NSLock()
    private val root by lazy {
        val path = encryptedTandemDatabasePath("actual")
        val protection = mapOf<Any?, Any?>("NSFileProtectionKey" to "NSFileProtectionCompleteUntilFirstUserAuthentication")
        check(
            NSFileManager.defaultManager.createDirectoryAtPath(path, true, protection, null),
        ) { "Could not create the budget storage directory." }
        check(NSURL.fileURLWithPath(path).setResourceValue(true, forKey = "NSURLIsExcludedFromBackupKey", error = null)) {
            "Could not exclude budget storage from backup."
        }
        path
    }

    private fun path(budgetId: String) = "$root/${budgetFileName(budgetId)}"

    override fun open(budgetId: String): ActualDatabase? =
        path(budgetId).takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }?.let { ActualDatabase(driver.open(it)) }

    override fun install(
        budgetId: String,
        bytes: ByteArray,
    ): ActualDatabase {
        require(bytes.isNotEmpty()) { "The downloaded Actual database is empty." }
        lock.lock()
        try {
            val target = path(budgetId)
            val staging = "$target.new"
            try {
                val output = checkNotNull(fopen(staging, "wb")) { "Could not create the downloaded Actual database." }
                try {
                    val length = bytes.size.toULong()
                    val written = bytes.usePinned { fwrite(it.addressOf(0), 1uL, length, output) }
                    check(
                        written == length && fflush(output) == 0 && fsync(fileno(output)) == 0,
                    ) { "Could not save the downloaded Actual database." }
                } finally {
                    fclose(output)
                }
                remove(budgetId)
                check(rename(staging, target) == 0) { "Could not install the downloaded Actual database." }
            } finally {
                if (NSFileManager.defaultManager.fileExistsAtPath(
                        staging,
                    )
                ) {
                    NSFileManager.defaultManager.removeItemAtPath(staging, error = null)
                }
            }
            return ActualDatabase(driver.open(target))
        } finally {
            lock.unlock()
        }
    }

    override fun remove(budgetId: String) {
        listOf("", "-wal", "-shm", "-journal").forEach {
            val file = path(budgetId) + it
            if (NSFileManager.defaultManager.fileExistsAtPath(file)) NSFileManager.defaultManager.removeItemAtPath(file, error = null)
        }
    }
}
