package org.actualbudget.companion.data.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun companionDatabaseBuilder(): RoomDatabase.Builder<CompanionDatabase> {
    val applicationSupportDirectory =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val databasePath = requireNotNull(applicationSupportDirectory?.path) + "/actual-budget-companion.db"
    return Room.databaseBuilder<CompanionDatabase>(name = databasePath)
}
