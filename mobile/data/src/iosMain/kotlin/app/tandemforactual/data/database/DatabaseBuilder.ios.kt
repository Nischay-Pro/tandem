package app.tandemforactual.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
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
