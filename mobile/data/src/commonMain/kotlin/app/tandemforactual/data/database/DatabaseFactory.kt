package app.tandemforactual.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

fun openTandemDatabase(
    builder: RoomDatabase.Builder<TandemDatabase>,
    driver: SQLiteDriver = BundledSQLiteDriver(),
): TandemDatabase =
    builder
        .setDriver(driver)
        .build()

fun TandemDatabase.closeDatabase() = close()
