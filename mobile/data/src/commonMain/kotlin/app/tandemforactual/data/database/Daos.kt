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
    SELECT recordId FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    fun observeVisibleRecordIds(
        budgetId: String,
        recordType: String,
    ): Flow<List<String>>

    @Query(
        """
    SELECT recordId FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    suspend fun findVisibleRecordIds(
        budgetId: String,
        recordType: String,
    ): List<String>

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    LIMIT 1
    """,
    )
    fun observeVisibleRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): Flow<BudgetRecordEntity?>

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
    SELECT recordId, recordDate, accountId, categoryId, payeeId, amountMinor, notes,
           cleared, reconciled, approved, matched, transfer, isParent
    FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction'
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    fun observeSearchableTransactions(budgetId: String): Flow<List<SearchableBudgetTransaction>>

    @Query(
        """
    SELECT substr(tx.recordDate, 1, 7) AS monthId, tx.categoryId, SUM(-tx.amountMinor) AS spendingMinor
    FROM budget_records AS tx
    LEFT JOIN budget_records AS category
      ON category.budgetId = tx.budgetId
      AND category.recordType = 'category'
      AND category.recordId = tx.categoryId
      AND category.localDeleted = 0 AND category.remoteDeleted = 0
      AND (category.localJson IS NOT NULL OR category.remoteJson IS NOT NULL)
    WHERE tx.budgetId = :budgetId AND tx.recordType = 'transaction'
      AND tx.recordDate IS NOT NULL AND length(tx.recordDate) >= 10
      AND tx.categoryId IS NOT NULL AND tx.amountMinor IS NOT NULL AND tx.transfer = 0
      AND tx.localDeleted = 0 AND tx.remoteDeleted = 0
      AND (tx.localJson IS NOT NULL OR tx.remoteJson IS NOT NULL)
      AND tx.isParent = 0
      AND COALESCE(
        CASE WHEN json_valid(COALESCE(category.localJson, category.remoteJson)) = 1
          THEN COALESCE(
            json_extract(COALESCE(category.localJson, category.remoteJson), '$.is_income'),
            json_extract(COALESCE(category.localJson, category.remoteJson), '$.isIncome')
          )
          ELSE 0
        END,
        0
      ) = 0
      AND (:fromDate IS NULL OR tx.recordDate >= :fromDate)
      AND (:throughDate IS NULL OR tx.recordDate <= :throughDate)
    GROUP BY substr(tx.recordDate, 1, 7), tx.categoryId
    ORDER BY monthId ASC, tx.categoryId ASC
    """,
    )
    fun observeCategoryMonthAggregates(
        budgetId: String,
        fromDate: String?,
        throughDate: String?,
    ): Flow<List<CategoryMonthAggregate>>

    @Query(
        """
    SELECT substr(tx.recordDate, 1, 7) AS monthId,
           SUM(CASE
             WHEN COALESCE(
               CASE WHEN json_valid(COALESCE(category.localJson, category.remoteJson)) = 1
                 THEN COALESCE(
                   json_extract(COALESCE(category.localJson, category.remoteJson), '$.is_income'),
                   json_extract(COALESCE(category.localJson, category.remoteJson), '$.isIncome')
                 )
                 ELSE 0
               END,
               0
             ) = 1 THEN tx.amountMinor
             WHEN tx.categoryId IS NULL AND tx.amountMinor > 0 THEN tx.amountMinor
             ELSE 0
           END) AS incomeMinor,
           SUM(CASE
             WHEN tx.categoryId IS NULL AND tx.amountMinor > 0 THEN 0
             WHEN COALESCE(
               CASE WHEN json_valid(COALESCE(category.localJson, category.remoteJson)) = 1
                 THEN COALESCE(
                   json_extract(COALESCE(category.localJson, category.remoteJson), '$.is_income'),
                   json_extract(COALESCE(category.localJson, category.remoteJson), '$.isIncome')
                 )
                 ELSE 0
               END,
               0
             ) = 0 THEN -tx.amountMinor
             ELSE 0
           END) AS spendingMinor
    FROM budget_records AS tx
    LEFT JOIN budget_records AS category
      ON category.budgetId = tx.budgetId
      AND category.recordType = 'category'
      AND category.recordId = tx.categoryId
      AND category.localDeleted = 0 AND category.remoteDeleted = 0
      AND (category.localJson IS NOT NULL OR category.remoteJson IS NOT NULL)
    WHERE tx.budgetId = :budgetId AND tx.recordType = 'transaction'
      AND tx.recordDate IS NOT NULL AND length(tx.recordDate) >= 10
      AND tx.amountMinor IS NOT NULL AND tx.transfer = 0
      AND tx.localDeleted = 0 AND tx.remoteDeleted = 0
      AND (tx.localJson IS NOT NULL OR tx.remoteJson IS NOT NULL)
      AND tx.isParent = 0
      AND (:fromDate IS NULL OR tx.recordDate >= :fromDate)
      AND (:throughDate IS NULL OR tx.recordDate <= :throughDate)
    GROUP BY substr(tx.recordDate, 1, 7)
    ORDER BY monthId ASC
    """,
    )
    fun observeMonthlyIncomeAndSpending(
        budgetId: String,
        fromDate: String?,
        throughDate: String?,
    ): Flow<List<MonthlyIncomeSpendingAggregate>>

    @Query(
        """
    SELECT accountId, recordDate AS date, SUM(amountMinor) AS amountMinor
    FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction'
      AND accountId IS NOT NULL AND recordDate IS NOT NULL AND length(recordDate) >= 10
      AND amountMinor IS NOT NULL
      AND localDeleted = 0 AND remoteDeleted = 0
      AND (localJson IS NOT NULL OR remoteJson IS NOT NULL)
      AND isParent = 0
      AND recordDate <= :throughDate
    GROUP BY accountId, recordDate
    ORDER BY recordDate DESC, accountId ASC
    """,
    )
    fun observeAccountDailyActivity(
        budgetId: String,
        throughDate: String,
    ): Flow<List<AccountDayAggregate>>

    @Query(
        """
    SELECT COALESCE(SUM(amountMinor), 0)
    FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction' AND accountId = :accountId
      AND amountMinor IS NOT NULL AND cleared = 1 AND isParent = 0
      AND localDeleted = 0 AND remoteDeleted = 0
      AND (localJson IS NOT NULL OR remoteJson IS NOT NULL)
    """,
    )
    fun observeClearedAccountBalance(
        budgetId: String,
        accountId: String,
    ): Flow<Long>

    @Query("SELECT recordType, recordId FROM budget_records WHERE budgetId = :budgetId AND remoteJson IS NOT NULL")
    suspend fun findRemoteRecordKeys(budgetId: String): List<RemoteBudgetRecordKey>

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
    fun observeRecentVisible(
        budgetId: String,
        recordType: String,
        limit: Int,
    ): Flow<List<BudgetRecordEntity>>

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction' AND accountId = :accountId
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    LIMIT :limit
    OFFSET :offset
    """,
    )
    fun observeRecentTransactionsForAccount(
        budgetId: String,
        accountId: String,
        limit: Int,
        offset: Int,
    ): Flow<List<BudgetRecordEntity>>

    @Query(
        """
    SELECT COUNT(*) FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    """,
    )
    fun observeVisibleCount(
        budgetId: String,
        recordType: String,
    ): Flow<Long>

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
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction'
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
      AND (
        :hasCursor = 0 OR
        (:cursorDate IS NOT NULL AND
          (recordDate IS NULL OR recordDate < :cursorDate OR
            (recordDate = :cursorDate AND recordId > :cursorId))) OR
        (recordDate IS NULL AND :cursorDate IS NULL AND recordId > :cursorId)
      )
    ORDER BY recordDate DESC, recordId ASC
    LIMIT :limit
    """,
    )
    suspend fun findVisibleTransactionPage(
        budgetId: String,
        cursorDate: String?,
        cursorId: String,
        hasCursor: Boolean,
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

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType AND recordId IN (:recordIds)
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    """,
    )
    suspend fun findVisibleByIds(
        budgetId: String,
        recordType: String,
        recordIds: List<String>,
    ): List<BudgetRecordEntity>

    @Query(
        "SELECT * FROM budget_records WHERE budgetId = :budgetId AND (localJson IS NOT NULL OR localDeleted = 1)",
    )
    suspend fun findLocalOverlays(budgetId: String): List<BudgetRecordEntity>

    @Upsert
    suspend fun put(record: BudgetRecordEntity)

    @Upsert
    suspend fun putAll(records: List<BudgetRecordEntity>)

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
        "UPDATE budget_records SET remoteDeleted = 1, remoteJson = NULL, remoteRevision = NULL WHERE budgetId = :budgetId AND recordType = :recordType AND recordId IN (:recordIds) AND remoteJson IS NOT NULL",
    )
    suspend fun tombstoneRemoteRecords(
        budgetId: String,
        recordType: String,
        recordIds: List<String>,
    ): Int

    @Query(
        "UPDATE budget_records SET remoteDeleted = 1, remoteJson = NULL, remoteRevision = NULL WHERE budgetId = :budgetId AND remoteJson IS NOT NULL AND NOT EXISTS (SELECT 1 FROM budget_snapshot_records AS staged WHERE staged.budgetId = :budgetId AND staged.snapshotKey = :snapshotKey AND staged.recordType = budget_records.recordType AND staged.recordId = budget_records.recordId)",
    )
    suspend fun tombstoneRemoteRecordsMissingFromSnapshot(
        budgetId: String,
        snapshotKey: String,
    ): Int
}

@Dao
interface BudgetSnapshotDao {
    @Query("SELECT * FROM budget_snapshot_progress WHERE budgetId = :budgetId LIMIT 1")
    suspend fun findProgress(budgetId: String): BudgetSnapshotProgressEntity?

    @Upsert
    suspend fun putProgress(progress: BudgetSnapshotProgressEntity)

    @Query("DELETE FROM budget_snapshot_progress WHERE budgetId = :budgetId")
    suspend fun deleteProgress(budgetId: String): Int

    @Upsert
    suspend fun putAll(records: List<BudgetSnapshotRecordEntity>)

    @Query(
        "SELECT * FROM budget_snapshot_records WHERE budgetId = :budgetId AND snapshotKey = :snapshotKey AND (:afterType IS NULL OR recordType > :afterType OR (recordType = :afterType AND recordId > :afterId)) ORDER BY recordType ASC, recordId ASC LIMIT :limit",
    )
    suspend fun findPage(
        budgetId: String,
        snapshotKey: String,
        afterType: String?,
        afterId: String?,
        limit: Int,
    ): List<BudgetSnapshotRecordEntity>

    @Query("DELETE FROM budget_snapshot_records WHERE budgetId = :budgetId")
    suspend fun deleteRecords(budgetId: String): Int
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE operationId = :operationId")
    suspend fun find(operationId: String): OutboxOperationEntity?

    @Query("SELECT * FROM sync_outbox WHERE idempotencyKey = :idempotencyKey")
    suspend fun findByIdempotencyKey(idempotencyKey: String): OutboxOperationEntity?

    @Query("SELECT * FROM sync_outbox WHERE recordType = :recordType ORDER BY createdAtEpochMs ASC, operationId ASC")
    suspend fun findByRecordType(recordType: String): List<OutboxOperationEntity>

    @Query("DELETE FROM sync_outbox WHERE operationId = :operationId AND status = 'queued' AND attemptCount = 0")
    suspend fun deleteQueuedOperation(operationId: String): Int

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

    @Query(
        "SELECT * FROM budget_sync_state WHERE generation != '' AND cursor IS NOT NULL ORDER BY COALESCE(lastSyncedAtEpochMs, 0) DESC, budgetId ASC",
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
