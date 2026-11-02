package app.tandemforactual.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun tandemDatabaseBuilder(context: Context): RoomDatabase.Builder<TandemDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath("tandem.db")
    return Room.databaseBuilder<TandemDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
}
