package app.tandemforactual.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun openTandemDatabase(builder: RoomDatabase.Builder<TandemDatabase>): TandemDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()
