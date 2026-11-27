package app.tandemforactual.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE budgetId = :budgetId ORDER BY createdAtEpochMs ASC, operationId ASC")
    suspend fun findAllForBudget(budgetId: String): List<OutboxOperationEntity>

    @Query("DELETE FROM sync_outbox WHERE budgetId = :budgetId")
    suspend fun deleteBudget(budgetId: String): Int

    @Query("SELECT * FROM sync_outbox WHERE operationId = :operationId")
    suspend fun find(operationId: String): OutboxOperationEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM sync_outbox WHERE status IN ('queued', 'sending', 'uncertain', 'conflict', 'rejected'))",
    )
    suspend fun hasUnresolvedWrites(): Boolean

    @Query("SELECT * FROM sync_outbox WHERE idempotencyKey = :idempotencyKey")
    suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxOperationEntity?

    @Query("SELECT * FROM sync_outbox WHERE recordType = :recordType ORDER BY createdAtEpochMs ASC, operationId ASC")
    suspend fun findByRecordType(recordType: String): List<OutboxOperationEntity>

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId AND status = 'queued' AND attemptCount = 0")
    suspend fun deleteQueuedOperation(operationId: String): Int

    @Query(
        "DELETE FROM sync_outbox WHERE recordType != 'budget-mutation-metadata' AND status IN ('committed', 'discarded', 'superseded') AND updatedAtEpochMs < :cutoffEpochMs",
    )
    suspend fun deleteExpiredTerminalWrites(cutoffEpochMs: Long): Int

    @Query(
        "SELECT * FROM sync_outbox WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId AND status = 'queued' AND serverOperationId IS NULL ORDER BY createdAtEpochMs DESC LIMIT 1",
    )
    suspend fun findMergeableQueuedForRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): OutboxOperationEntity?

    @Query(
        "SELECT * FROM sync_outbox WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId AND status IN ('queued', 'sending', 'uncertain', 'conflict') ORDER BY createdAtEpochMs ASC LIMIT 1",
    )
    suspend fun findBlockingForRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): OutboxOperationEntity?

    @Query(
        """
    SELECT queued.* FROM sync_outbox AS queued
    WHERE queued.budgetId = :budgetId
      AND queued.recordType = 'transaction'
      AND queued.status = 'queued'
      AND NOT EXISTS (
        SELECT 1 FROM sync_outbox AS blocking
        WHERE blocking.budgetId = queued.budgetId
          AND blocking.recordType = queued.recordType
          AND blocking.recordId = queued.recordId
          AND blocking.operationId != queued.operationId
          AND blocking.status IN ('sending', 'uncertain', 'conflict')
          AND (
            blocking.createdAtEpochMs < queued.createdAtEpochMs
            OR (blocking.createdAtEpochMs = queued.createdAtEpochMs AND blocking.operationId < queued.operationId)
          )
      )
    ORDER BY queued.createdAtEpochMs ASC, queued.operationId ASC
    LIMIT 1
    """,
    )
    suspend fun findNextQueued(budgetId: String): OutboxOperationEntity?

    @Query(
        "SELECT * FROM sync_outbox WHERE budgetId = :budgetId AND status IN ('queued', 'sending', 'uncertain', 'conflict', 'rejected', 'superseded', 'discarded') ORDER BY createdAtEpochMs ASC, operationId ASC",
    )
    suspend fun findPending(budgetId: String): List<OutboxOperationEntity>

    @Query(
        "SELECT * FROM sync_outbox WHERE budgetId = :budgetId AND status IN ('queued', 'sending', 'uncertain', 'conflict', 'rejected', 'superseded', 'discarded') ORDER BY createdAtEpochMs ASC, operationId ASC",
    )
    fun observePending(budgetId: String): Flow<List<OutboxOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: OutboxOperationEntity)

    @Upsert
    suspend fun put(operation: OutboxOperationEntity)

    @Query(
        "UPDATE sync_outbox SET status = 'sending', attemptCount = attemptCount + 1, updatedAtEpochMs = :updatedAtEpochMs WHERE operationId = :operationId AND status = 'queued'",
    )
    suspend fun markSending(
        operationId: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        "UPDATE sync_outbox SET status = 'uncertain', lastErrorCode = 'process-restarted-during-send', updatedAtEpochMs = :updatedAtEpochMs WHERE status = 'sending'",
    )
    suspend fun markInterruptedSendsUncertain(updatedAtEpochMs: Long): Int

    @Query(
        "UPDATE sync_outbox SET status = 'uncertain', lastErrorCode = 'process-restarted-during-send', updatedAtEpochMs = :updatedAtEpochMs WHERE status = 'sending' AND recordType IN (:recordTypes)",
    )
    suspend fun markInterruptedSendsUncertainForRecordTypes(
        recordTypes: List<String>,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        "UPDATE sync_outbox SET status = 'uncertain', lastErrorCode = 'process-restarted-during-send', updatedAtEpochMs = :updatedAtEpochMs WHERE status = 'sending' AND recordType NOT IN (:recordTypes)",
    )
    suspend fun markInterruptedSendsUncertainExceptRecordTypes(
        recordTypes: List<String>,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        "UPDATE sync_outbox SET status = 'conflict', lastErrorCode = 'remote-deleted', updatedAtEpochMs = :updatedAtEpochMs WHERE budgetId = :budgetId AND recordType = 'transaction' AND recordId = :recordId AND action = 'UPDATE' AND status = 'queued' AND attemptCount = 0",
    )
    suspend fun markEditsForDeletedRecord(
        budgetId: String,
        recordId: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        "UPDATE sync_outbox SET status = 'conflict', lastErrorCode = 'remote-deleted', updatedAtEpochMs = :updatedAtEpochMs WHERE budgetId = :budgetId AND recordType = 'transaction' AND action = 'UPDATE' AND status = 'queued' AND attemptCount = 0 AND EXISTS (SELECT 1 FROM budget_records WHERE budget_records.budgetId = sync_outbox.budgetId AND budget_records.recordType = sync_outbox.recordType AND budget_records.recordId = sync_outbox.recordId AND budget_records.remoteDeleted = 1)",
    )
    suspend fun markEditsForDeletedRecords(
        budgetId: String,
        updatedAtEpochMs: Long,
    ): Int
}

@Dao
interface BudgetSyncStateDao {
    @Query("DELETE FROM budget_sync_state WHERE budgetId = :budgetId")
    suspend fun deleteBudget(budgetId: String): Int

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM budget_records)
            OR EXISTS(SELECT 1 FROM budget_snapshot_records)
            OR EXISTS(SELECT 1 FROM budget_snapshot_progress)
            OR EXISTS(SELECT 1 FROM budget_sync_state)
            OR EXISTS(SELECT 1 FROM sync_outbox)
        """,
    )
    suspend fun hasAnyBudgetData(): Boolean

    @Query("SELECT * FROM budget_sync_state WHERE budgetId = :budgetId")
    suspend fun find(budgetId: String): BudgetSyncStateEntity?

    @Query(
        "SELECT * FROM budget_sync_state WHERE generation != '' AND lastSyncedAtEpochMs IS NOT NULL ORDER BY lastSyncedAtEpochMs DESC, budgetId ASC",
    )
    suspend fun findCachedBudgets(): List<BudgetSyncStateEntity>

    @Query("SELECT * FROM budget_sync_state WHERE budgetId = :budgetId")
    fun observe(budgetId: String): Flow<BudgetSyncStateEntity?>

    @Upsert
    suspend fun put(state: BudgetSyncStateEntity)
}

@Dao
interface AppPreferenceDao {
    @Query("SELECT * FROM app_preferences")
    suspend fun readAll(): List<AppPreferenceEntity>

    @Query("DELETE FROM app_preferences")
    suspend fun clearAll()

    @Upsert
    suspend fun putAll(preferences: List<AppPreferenceEntity>)
}
