package app.tandemforactual.data.database

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "budget_records",
    primaryKeys = ["budgetId", "recordType", "recordId"],
    indices = [
        Index(value = ["budgetId", "recordType", "accountId", "recordDate"]),
        Index(value = ["budgetId", "recordType", "categoryId", "recordDate"]),
        Index(
            value = ["budgetId", "recordType", "recordDate", "recordId"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
        ),
        Index(value = ["budgetId", "syncGeneration"]),
    ],
)
data class BudgetRecordEntity(
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val remoteJson: String?,
    val remoteRevision: String?,
    val localJson: String?,
    val localDeleted: Boolean,
    val remoteDeleted: Boolean,
    val syncGeneration: String,
    val recordDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val budgetMonth: String?,
    val displayName: String? = null,
    val notes: String? = null,
    val cleared: Boolean? = null,
    val reconciled: Boolean? = null,
    val approved: Boolean? = null,
    val matched: Boolean? = null,
    val transfer: Boolean? = null,
)

data class SearchableBudgetTransaction(
    val recordId: String,
    val recordDate: String?,
    val accountId: String?,
    val categoryId: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val notes: String?,
    val cleared: Boolean?,
    val reconciled: Boolean?,
    val approved: Boolean?,
    val matched: Boolean?,
    val transfer: Boolean?,
)

data class RemoteBudgetRecordKey(
    val recordType: String,
    val recordId: String,
)

@Entity(tableName = "budget_sync_state")
data class BudgetSyncStateEntity(
    @PrimaryKey val budgetId: String,
    val generation: String,
    val cursor: String?,
    val status: String,
    val lastAttemptAtEpochMs: Long?,
    val lastSyncedAtEpochMs: Long?,
    val lastErrorCode: String?,
)

@Entity(tableName = "app_preferences")
data class AppPreferenceEntity(
    @PrimaryKey val name: String,
    val value: String,
)

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["budgetId", "status", "createdAtEpochMs"]),
        Index(value = ["budgetId", "recordType", "recordId", "status"]),
        Index(value = ["serverOperationId"], unique = true),
    ],
)
data class OutboxOperationEntity(
    @PrimaryKey val operationId: String,
    val idempotencyKey: String,
    val budgetId: String,
    val recordType: String,
    val recordId: String,
    val action: String,
    val baseRevision: String?,
    val baseSnapshotJson: String?,
    val patchJson: String,
    val optimisticJson: String?,
    val optimisticRecordDate: String?,
    val optimisticAccountId: String?,
    val optimisticCategoryId: String?,
    val optimisticPayeeId: String?,
    val optimisticAmountMinor: Long?,
    val optimisticBudgetMonth: String?,
    val status: String,
    val attemptCount: Int,
    val serverOperationId: String?,
    val lastErrorCode: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
