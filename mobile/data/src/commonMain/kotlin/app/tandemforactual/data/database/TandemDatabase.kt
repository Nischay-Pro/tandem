package app.tandemforactual.data.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [BudgetRecordEntity::class, BudgetSyncStateEntity::class, AppPreferenceEntity::class, OutboxOperationEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@ConstructedBy(TandemDatabaseConstructor::class)
abstract class TandemDatabase : RoomDatabase() {
    abstract fun budgetRecordDao(): BudgetRecordDao

    abstract fun outboxDao(): OutboxDao

    abstract fun budgetSyncStateDao(): BudgetSyncStateDao

    abstract fun appPreferenceDao(): AppPreferenceDao
}

@Suppress("KotlinNoActualForExpect")
expect object TandemDatabaseConstructor : RoomDatabaseConstructor<TandemDatabase> {
    override fun initialize(): TandemDatabase
}
