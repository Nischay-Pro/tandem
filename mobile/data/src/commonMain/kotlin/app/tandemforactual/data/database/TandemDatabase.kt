package app.tandemforactual.data.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [BudgetRecordEntity::class, BudgetSyncStateEntity::class, OutboxOperationEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(TandemDatabaseConstructor::class)
abstract class TandemDatabase : RoomDatabase() {
    abstract fun budgetRecordDao(): BudgetRecordDao

    abstract fun outboxDao(): OutboxDao

    abstract fun budgetSyncStateDao(): BudgetSyncStateDao
}

@Suppress("KotlinNoActualForExpect")
expect object TandemDatabaseConstructor : RoomDatabaseConstructor<TandemDatabase> {
    override fun initialize(): TandemDatabase
}
