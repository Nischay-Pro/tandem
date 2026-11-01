package org.actualbudget.companion.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun companionDatabaseBuilder(context: Context): RoomDatabase.Builder<CompanionDatabase> {
    val appContext = context.applicationContext
    val databaseFile = appContext.getDatabasePath("actual-budget-companion.db")
    return Room.databaseBuilder<CompanionDatabase>(
        context = appContext,
        name = databaseFile.absolutePath,
    )
}
