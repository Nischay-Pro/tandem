package app.tandemforactual.data.database

import androidx.room3.Room
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
import kotlin.uuid.Uuid

/** Holds one iOS direct budget in Application Support with iOS file protection and backup exclusion. */
@OptIn(ExperimentalForeignApi::class)
class IosDirectBudgetFiles private constructor(
    override val installationId: String,
    directory: String,
    private val driver: SQLiteDriver,
) : DirectBudgetFiles {
    private val actualPath = "$directory/actual.sqlite"
    private val cachePath = "$directory/mobile.db"
    private val installLock = NSLock()

    override fun installActual(bytes: ByteArray): ActualDatabase {
        require(bytes.isNotEmpty()) { "The downloaded Actual database is empty." }
        installLock.lock()
        try {
            check(!NSFileManager.defaultManager.fileExistsAtPath(actualPath)) { "This installation already has an Actual database." }
            writeActualFile(bytes)
            return openActual()
        } finally {
            installLock.unlock()
        }
    }

    private fun writeActualFile(bytes: ByteArray) {
        val staging = "$actualPath.new"
        try {
            val output = checkNotNull(fopen(staging, "wb")) { "Could not create the downloaded Actual database." }
            try {
                val length = bytes.size.toULong()
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1uL, length, output) }
                check(written == length && fflush(output) == 0 && fsync(fileno(output)) == 0) {
                    "Could not save the downloaded Actual database."
                }
            } finally {
                fclose(output)
            }
            check(rename(staging, actualPath) == 0) { "Could not install the downloaded Actual database." }
        } finally {
            val files = NSFileManager.defaultManager
            if (files.fileExistsAtPath(staging)) files.removeItemAtPath(staging, error = null)
        }
    }

    override fun openActual(): ActualDatabase {
        check(NSFileManager.defaultManager.fileExistsAtPath(actualPath)) { "The local Actual database is missing." }
        return ActualDatabase(driver.open(actualPath))
    }

    override fun openMobileCache(): TandemDatabase = openTandemDatabase(Room.databaseBuilder<TandemDatabase>(name = cachePath), driver)

    companion object {
        fun create(driver: SQLiteDriver = BundledSQLiteDriver()): IosDirectBudgetFiles {
            val files = NSFileManager.defaultManager
            val root = encryptedTandemDatabasePath("direct-actual")
            val protection = mapOf<Any?, Any?>("NSFileProtectionKey" to "NSFileProtectionCompleteUntilFirstUserAuthentication")
            check(files.createDirectoryAtPath(root, true, protection, null)) { "Could not create the direct budget storage directory." }
            check(NSURL.fileURLWithPath(root).setResourceValue(true, forKey = "NSURLIsExcludedFromBackupKey", error = null)) {
                "Could not exclude direct budget storage from backup."
            }
            val installationId = Uuid.random().toString()
            val directory = "$root/$installationId"
            check(files.createDirectoryAtPath(directory, false, protection, null)) { "Could not create a new direct budget installation." }
            return IosDirectBudgetFiles(installationId, directory, driver)
        }

        fun reopen(
            installationId: String,
            driver: SQLiteDriver = BundledSQLiteDriver(),
        ): IosDirectBudgetFiles {
            require(runCatching { Uuid.parse(installationId).toString() }.getOrNull() == installationId) {
                "Invalid direct budget installation ID."
            }
            val directory = encryptedTandemDatabasePath("direct-actual/$installationId")
            val files = NSFileManager.defaultManager
            check(files.fileExistsAtPath("$directory/actual.sqlite") && files.fileExistsAtPath("$directory/mobile.db")) {
                "The direct budget installation is incomplete."
            }
            return IosDirectBudgetFiles(installationId, directory, driver)
        }
    }
}
