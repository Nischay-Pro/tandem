package app.tandemforactual.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetRecordDao {
    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    fun observeVisible(
        budgetId: String,
        recordType: String,
    ): Flow<List<BudgetRecordEntity>>

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND (:accountId IS NULL OR accountId = :accountId)
      AND (:categoryId IS NULL OR categoryId = :categoryId)
      AND (:payeeId IS NULL OR payeeId = :payeeId)
      AND (:fromDate IS NULL OR recordDate >= :fromDate)
      AND (:throughDate IS NULL OR recordDate <= :throughDate)
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    suspend fun findVisible(
        budgetId: String,
        recordType: String,
        accountId: String?,
        categoryId: String?,
        payeeId: String?,
        fromDate: String?,
        throughDate: String?,
    ): List<BudgetRecordEntity>

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    LIMIT :limit
    """,
    )
    suspend fun findRecentVisible(
        budgetId: String,
        recordType: String,
        limit: Int,
    ): List<BudgetRecordEntity>

    @Query(
        """
    SELECT COUNT(*) FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    """,
    )
    suspend fun countVisible(
        budgetId: String,
        recordType: String,
    ): Long

    @Query(
        "SELECT * FROM budget_records WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId",
    )
    suspend fun find(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): BudgetRecordEntity?

    @Upsert
    suspend fun put(record: BudgetRecordEntity)

    @Query(
        "UPDATE budget_records SET remoteDeleted = 1, remoteJson = NULL, remoteRevision = :revision, syncGeneration = :generation WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId",
    )
    suspend fun applyTombstone(
        budgetId: String,
        recordType: String,
        recordId: String,
        revision: String?,
        generation: String,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTombstone(record: BudgetRecordEntity)

    @Query(
        "UPDATE budget_records SET localJson = NULL, localDeleted = 0 WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId",
    )
    suspend fun clearLocalOverlay(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): Int

    @Query(
        "UPDATE budget_records SET remoteDeleted = 1, remoteJson = NULL, remoteRevision = NULL WHERE budgetId = :budgetId AND remoteJson IS NOT NULL",
    )
    suspend fun tombstoneAllRemoteRecords(budgetId: String)
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE operationId = :operationId")
    suspend fun find(operationId: String): OutboxOperationEntity?

    @Query("SELECT * FROM sync_outbox WHERE idempotencyKey = :idempotencyKey")
    suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxOperationEntity?

    @Query(
        "SELECT * FROM sync_outbox WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId AND status = 'queued' AND attemptCount = 0 ORDER BY createdAtEpochMs DESC LIMIT 1",
    )
    suspend fun findUnsentForRecord(
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: OutboxOperationEntity)

    @Upsert
    suspend fun put(operation: OutboxOperationEntity)

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId AND status = 'queued' AND attemptCount = 0")
    suspend fun deleteUnsent(operationId: String): Int

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
    @Query("SELECT * FROM budget_sync_state WHERE budgetId = :budgetId")
    suspend fun find(budgetId: String): BudgetSyncStateEntity?

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
