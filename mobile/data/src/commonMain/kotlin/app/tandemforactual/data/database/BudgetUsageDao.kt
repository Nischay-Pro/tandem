package app.tandemforactual.data.database

import androidx.room3.Dao
import androidx.room3.Query

private const val USAGE_VISIBLE = """localDeleted = 0
      AND (localJson IS NOT NULL OR (remoteJson IS NOT NULL AND remoteDeleted = 0))"""

private const val USAGE_BODY = "COALESCE(localJson, remoteJson)"

private const val USAGE_LEAF_TRANSACTIONS = """FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'transaction' AND isParent = 0
      AND $USAGE_VISIBLE"""

private const val USAGE_RULE_ITEM_MATCH = """AS item,
        json_each(CASE WHEN item.type = 'object' THEN item.value ELSE '{}' END, '$.value') AS target
      WHERE (CASE WHEN item.type = 'object' THEN json_extract(item.value, '$.field') END) IN (:fields)
        AND target.value IN (:ids)"""

/** How many transactions use one payee. */
data class PayeeTransactionCount(
    val payeeId: String,
    val transactions: Long,
)

@Dao
interface BudgetUsageDao {
    @Query("SELECT payeeId, COUNT(*) AS transactions $USAGE_LEAF_TRANSACTIONS AND payeeId IS NOT NULL GROUP BY payeeId")
    suspend fun countTransactionsByPayee(budgetId: String): List<PayeeTransactionCount>

    @Query("SELECT COUNT(*) $USAGE_LEAF_TRANSACTIONS AND payeeId = :payeeId")
    suspend fun countPayeeTransactions(
        budgetId: String,
        payeeId: String,
    ): Long

    @Query("SELECT COUNT(*) $USAGE_LEAF_TRANSACTIONS AND accountId = :accountId")
    suspend fun countAccountTransactions(
        budgetId: String,
        accountId: String,
    ): Long

    @Query("SELECT COUNT(*) $USAGE_LEAF_TRANSACTIONS AND categoryId IN (:categoryIds)")
    suspend fun countCategoryTransactions(
        budgetId: String,
        categoryIds: List<String>,
    ): Long

    @Query(
        """
    SELECT recordId FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'category' AND $USAGE_VISIBLE
      AND (CASE WHEN json_valid($USAGE_BODY) = 1
        THEN COALESCE(json_extract($USAGE_BODY, '$.group'), json_extract($USAGE_BODY, '$.group_id'))
      END) = :groupId
    ORDER BY recordId ASC
    """,
    )
    suspend fun findGroupCategoryIds(
        budgetId: String,
        groupId: String,
    ): List<String>

    @Query(
        """
    SELECT COUNT(*) FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'rule' AND $USAGE_VISIBLE
      AND (CASE WHEN json_valid($USAGE_BODY) = 1 THEN
        COALESCE(json_extract($USAGE_BODY, '$.tombstone'), 0) = 0 AND EXISTS (
          SELECT 1 FROM json_each($USAGE_BODY, '$.conditions') $USAGE_RULE_ITEM_MATCH
          UNION ALL
          SELECT 1 FROM json_each($USAGE_BODY, '$.actions') $USAGE_RULE_ITEM_MATCH
        )
      ELSE 0 END) = 1
    """,
    )
    suspend fun countRulesReferencing(
        budgetId: String,
        fields: List<String>,
        ids: List<String>,
    ): Long

    @Query(
        """
    SELECT COUNT(*) FROM budget_records
    WHERE budgetId = :budgetId AND recordType = 'schedule' AND $USAGE_VISIBLE
      AND (CASE WHEN json_valid($USAGE_BODY) = 1 THEN json_extract($USAGE_BODY, '$.rule') END) = :ruleId
    """,
    )
    suspend fun countRuleSchedules(
        budgetId: String,
        ruleId: String,
    ): Long
}
