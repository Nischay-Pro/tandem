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
    val databasePath = requireNotNull(applicationSupportDirectory?.path) + "/tandem.db"
    return Room.databaseBuilder<TandemDatabase>(name = databasePath)
}
