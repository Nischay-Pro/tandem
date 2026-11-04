package app.tandemforactual.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import java.io.File

fun tandemDatabaseBuilder(context: Context): RoomDatabase.Builder<TandemDatabase> =
    tandemDatabaseBuilder(context, context.applicationContext.getDatabasePath("tandem.db"))

internal fun tandemDatabaseBuilder(
    context: Context,
    databaseFile: File,
): RoomDatabase.Builder<TandemDatabase> {
    val appContext = context.applicationContext
    return Room
        .databaseBuilder<TandemDatabase>(
            context = appContext,
            name = databaseFile.absolutePath,
        ).enableMultiInstanceInvalidation()
}
