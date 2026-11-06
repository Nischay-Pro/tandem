package app.tandemforactual.data.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection

@Database(
    entities = [BudgetRecordEntity::class, BudgetSyncStateEntity::class, AppPreferenceEntity::class, OutboxOperationEntity::class],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = BudgetRecordDisplayMetadataMigration::class),
    ],
    exportSchema = true,
)
@ConstructedBy(TandemDatabaseConstructor::class)
abstract class TandemDatabase : RoomDatabase() {
    abstract fun budgetRecordDao(): BudgetRecordDao

    abstract fun outboxDao(): OutboxDao

    abstract fun budgetSyncStateDao(): BudgetSyncStateDao

    abstract fun appPreferenceDao(): AppPreferenceDao
}

class BudgetRecordDisplayMetadataMigration : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        connection
            .prepare(
                """
                UPDATE budget_records
                SET displayName = CASE
                    WHEN json_valid(COALESCE(localJson, remoteJson)) THEN
                        CASE
                            WHEN json_type(COALESCE(localJson, remoteJson)) = 'object'
                            THEN NULLIF(json_extract(COALESCE(localJson, remoteJson), '$.name'), '')
                            ELSE NULL
                        END
                    ELSE NULL
                    END,
                    notes = CASE
                    WHEN json_valid(COALESCE(localJson, remoteJson)) THEN
                        CASE
                            WHEN json_type(COALESCE(localJson, remoteJson)) = 'object'
                            THEN NULLIF(json_extract(COALESCE(localJson, remoteJson), '$.notes'), '')
                            ELSE NULL
                        END
                    ELSE NULL
                    END
                """.trimIndent(),
            ).use { statement -> statement.step() }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object TandemDatabaseConstructor : RoomDatabaseConstructor<TandemDatabase> {
    override fun initialize(): TandemDatabase
}
