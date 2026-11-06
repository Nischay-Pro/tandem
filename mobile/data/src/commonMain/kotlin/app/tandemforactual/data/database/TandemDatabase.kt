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
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = BudgetRecordDisplayMetadataMigration::class),
        AutoMigration(from = 3, to = 4, spec = BudgetRecordSearchMetadataMigration::class),
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

class BudgetRecordSearchMetadataMigration : AutoMigrationSpec {
    override suspend fun onPostMigrate(connection: SQLiteConnection) {
        val body = "COALESCE(localJson, remoteJson)"
        connection
            .prepare(
                """
                UPDATE budget_records
                SET cleared = CASE
                    WHEN json_valid($body) = 0 THEN NULL
                    WHEN json_extract($body, '$.cleared') = 1 THEN 1
                    WHEN json_extract($body, '$.cleared') = 0 THEN 0
                    WHEN lower(CAST(json_extract($body, '$.cleared') AS TEXT)) IN ('cleared', 'reconciled') THEN 1
                    WHEN lower(CAST(json_extract($body, '$.cleared') AS TEXT)) = 'uncleared' THEN 0
                    ELSE NULL
                END,
                reconciled = CASE
                    WHEN json_valid($body) = 0 THEN NULL
                    WHEN json_extract($body, '$.reconciled') = 1 THEN 1
                    WHEN json_extract($body, '$.reconciled') = 0 THEN 0
                    WHEN lower(CAST(json_extract($body, '$.cleared') AS TEXT)) = 'reconciled' THEN 1
                    ELSE NULL
                END,
                approved = CASE
                    WHEN json_valid($body) = 1 AND json_type($body, '$.approved') IN ('true', 'false')
                    THEN json_extract($body, '$.approved')
                    ELSE NULL
                END,
                matched = CASE
                    WHEN json_valid($body) = 1 AND json_type($body, '$.matched') IN ('true', 'false')
                    THEN json_extract($body, '$.matched')
                    ELSE NULL
                END,
                transfer = CASE
                    WHEN json_valid($body) = 0 THEN NULL
                    WHEN NULLIF(json_extract($body, '$.transfer_id'), '') IS NOT NULL
                      OR NULLIF(json_extract($body, '$.transferId'), '') IS NOT NULL
                      OR json_extract($body, '$.is_transfer') = 1
                      OR json_extract($body, '$.isTransfer') = 1
                    THEN 1
                    ELSE 0
                END
                WHERE recordType = 'transaction'
                """.trimIndent(),
            ).use { statement -> statement.step() }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object TandemDatabaseConstructor : RoomDatabaseConstructor<TandemDatabase> {
    override fun initialize(): TandemDatabase
}
