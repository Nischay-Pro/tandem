package app.tandemforactual.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteException
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun tandemDatabaseBuilder(): RoomDatabase.Builder<TandemDatabase> {
    val applicationSupportDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val supportDirectory = requireNotNull(applicationSupportDirectory)
    val applicationSupportPath = requireNotNull(supportDirectory.path)
    supportDirectory.setResourceValue(
        true,
        forKey = "NSURLIsExcludedFromBackupKey",
        error = null,
    )
    val databasePath = "$applicationSupportPath/tandem.db"
    val fileProtectionAttributes =
        mapOf<Any?, Any?>("NSFileProtectionKey" to "NSFileProtectionCompleteUntilFirstUserAuthentication")
    NSFileManager.defaultManager.setAttributes(
        fileProtectionAttributes,
        ofItemAtPath = applicationSupportPath,
        error = null,
    )
    listOf(databasePath, "$databasePath-wal", "$databasePath-shm")
        .filter(NSFileManager.defaultManager::fileExistsAtPath)
        .forEach { path ->
            NSFileManager.defaultManager.setAttributes(
                fileProtectionAttributes,
                ofItemAtPath = path,
                error = null,
            )
        }
    return Room.databaseBuilder<TandemDatabase>(name = databasePath)
}

/** Path for an encrypted budget database; iOS keeps it unreadable while the device is locked. */
@OptIn(ExperimentalForeignApi::class)
fun encryptedTandemDatabasePath(fileName: String): String {
    val supportDirectory =
        requireNotNull(
            NSFileManager.defaultManager
                .URLForDirectory(
                    directory = NSApplicationSupportDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = true,
                    error = null,
                )?.path,
        )
    return "$supportDirectory/$fileName"
}

@OptIn(ExperimentalForeignApi::class)
fun encryptedTandemDatabaseBuilder(path: String): RoomDatabase.Builder<TandemDatabase> {
    val protection = mapOf<Any?, Any?>("NSFileProtectionKey" to "NSFileProtectionComplete")
    listOf(path, "$path-wal", "$path-shm")
        .filter(NSFileManager.defaultManager::fileExistsAtPath)
        .forEach { NSFileManager.defaultManager.setAttributes(protection, ofItemAtPath = it, error = null) }
    return Room.databaseBuilder<TandemDatabase>(name = path)
}

@OptIn(ExperimentalForeignApi::class)
fun deleteEncryptedTandemDatabase(path: String) {
    listOf(path, "$path-wal", "$path-shm", "$path-journal")
        .filter(NSFileManager.defaultManager::fileExistsAtPath)
        .forEach { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
}

/** Opens an existing encrypted budget, or returns null when [rawKey] doesn't unlock it. */
fun unlockEncryptedTandemDatabase(
    path: String,
    rawKey: String,
): TandemDatabase? {
    val driver = SqlCipherDriver(rawKey)
    val unlocks =
        try {
            driver.open(path).use { it.execSQL("SELECT count(*) FROM sqlite_master") }
            true
        } catch (_: SQLiteException) {
            false
        }
    return if (unlocks) openTandemDatabase(encryptedTandemDatabaseBuilder(path), driver) else null
}

fun createEncryptedTandemDatabase(
    path: String,
    rawKey: String,
): TandemDatabase {
    deleteEncryptedTandemDatabase(path)
    return openTandemDatabase(encryptedTandemDatabaseBuilder(path), SqlCipherDriver(rawKey))
}
