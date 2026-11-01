package org.actualbudget.companion.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun openCompanionDatabase(builder: RoomDatabase.Builder<CompanionDatabase>): CompanionDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()
