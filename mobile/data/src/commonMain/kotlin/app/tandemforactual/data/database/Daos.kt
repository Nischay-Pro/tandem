package app.tandemforactual.data.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

private const val REPORT_LIVE = """localDeleted = 0 AND remoteDeleted = 0
          AND (localJson IS NOT NULL OR remoteJson IS NOT NULL)"""

private const val REPORT_INCOME_CATEGORIES = """SELECT recordId FROM budget_records
        WHERE budgetId = :budgetId AND recordType = 'category' AND $REPORT_LIVE
          AND COALESCE(
            CASE WHEN json_valid(COALESCE(localJson, remoteJson)) = 1
              THEN COALESCE(json_extract(COALESCE(localJson, remoteJson), '$.is_income'), json_extract(COALESCE(localJson, remoteJson), '$.isIncome'))
              ELSE 0
            END,
            0
          ) = 1"""

private const val REPORT_OFF_BUDGET_ACCOUNTS = """SELECT recordId FROM budget_records
        WHERE budgetId = :budgetId AND recordType = 'account' AND $REPORT_LIVE
          AND COALESCE(
            CASE WHEN json_valid(COALESCE(localJson, remoteJson)) = 1
              THEN COALESCE(json_extract(COALESCE(localJson, remoteJson), '$.offbudget'), json_extract(COALESCE(localJson, remoteJson), '$.offBudget'))
              ELSE 0
            END,
            0
          ) = 1"""

private const val REPORT_CATEGORY_IS_INCOME = "(tx.categoryId IN ($REPORT_INCOME_CATEGORIES))"

private const val REPORT_TRANSACTIONS = """FROM budget_records AS tx
    WHERE tx.budgetId = :budgetId AND tx.recordType = 'transaction'
      AND tx.recordDate IS NOT NULL AND length(tx.recordDate) >= 10
      AND tx.amountMinor IS NOT NULL
      AND tx.localDeleted = 0 AND tx.remoteDeleted = 0
      AND (tx.localJson IS NOT NULL OR tx.remoteJson IS NOT NULL)
      AND tx.isParent = 0
      AND (tx.accountId IS NULL OR tx.accountId NOT IN ($REPORT_OFF_BUDGET_ACCOUNTS))
      AND (:fromDate IS NULL OR tx.recordDate >= :fromDate)
      AND (:throughDate IS NULL OR tx.recordDate <= :throughDate)"""

@Dao
interface BudgetRecordDao {
    @Query("DELETE FROM budget_records WHERE budgetId = :budgetId")
    suspend fun deleteBudget(budgetId: String): Int

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
    $REPORT_TRANSACTIONS
      AND tx.categoryId IS NOT NULL AND $REPORT_CATEGORY_IS_INCOME = 0
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
             WHEN tx.categoryId IS NOT NULL AND $REPORT_CATEGORY_IS_INCOME = 1 THEN tx.amountMinor
             WHEN tx.categoryId IS NULL AND COALESCE(tx.transfer, 0) = 0 AND tx.amountMinor > 0 THEN tx.amountMinor
             ELSE 0
           END) AS incomeMinor,
           SUM(CASE
             WHEN tx.categoryId IS NOT NULL AND $REPORT_CATEGORY_IS_INCOME = 0 THEN -tx.amountMinor
             ELSE 0
           END) AS spendingMinor
    $REPORT_TRANSACTIONS
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
    WHERE budgetId = :budgetId AND recordType = 'transaction'
      AND categoryId IS NULL AND isParent = 0 AND (transfer IS NULL OR transfer = 0)
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    fun observeUncategorizedTransactions(budgetId: String): Flow<List<BudgetRecordEntity>>

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
    """,
    )
    suspend fun findAllVisible(
        budgetId: String,
        recordType: String,
    ): List<BudgetRecordEntity>

    @Query(
        """
    SELECT * FROM budget_records
    WHERE budgetId = :budgetId AND recordType = :recordType
      AND recordDate >= :fromDate
      AND (:throughDate IS NULL OR recordDate <= :throughDate)
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    """,
    )
    suspend fun findVisibleFrom(
        budgetId: String,
        recordType: String,
        fromDate: String,
        throughDate: String?,
    ): List<BudgetRecordEntity>

    @Query(
        """
    SELECT categoryId FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction' AND payeeId = :payeeId
      AND categoryId IS NOT NULL AND isParent = 0
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    ORDER BY recordDate DESC, recordId ASC
    LIMIT 1
    """,
    )
    suspend fun findLatestCategoryForPayee(
        budgetId: String,
        payeeId: String,
    ): String?

    @Query(
        """
    SELECT payeeId FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction' AND payeeId IS NOT NULL
      AND localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))
    GROUP BY payeeId
    ORDER BY MAX(recordDate) DESC, payeeId ASC
    LIMIT :limit
    """,
    )
    suspend fun findRecentPayeeIds(
        budgetId: String,
        limit: Int,
    ): List<String>

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

    @Query(
        "DELETE FROM budget_records WHERE budgetId = :budgetId AND recordType = :recordType AND recordId = :recordId AND localJson IS NOT NULL",
    )
    suspend fun deleteLocalOverlay(
        budgetId: String,
        recordType: String,
        recordId: String,
    ): Int

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
