package app.tandemforactual.data.store

import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import kotlinx.coroutines.flow.Flow

fun LocalBudgetRepository.observeRecords(
    budgetId: String,
    recordType: String,
): Flow<List<BudgetRecordEntity>> = database.budgetRecordDao().observeVisible(budgetId, recordType).stabilized()

fun LocalBudgetRepository.observeSearchableTransactions(budgetId: String): Flow<List<SearchableBudgetTransaction>> =
    database.budgetRecordDao().observeSearchableTransactions(budgetId).stabilized()

fun LocalBudgetRepository.observeCategoryMonthAggregates(
    budgetId: String,
    fromDate: String?,
    throughDate: String?,
): Flow<List<CategoryMonthAggregate>> =
    database.budgetRecordDao().observeCategoryMonthAggregates(budgetId, fromDate, throughDate).stabilized()

fun LocalBudgetRepository.observeMonthlyIncomeAndSpending(
    budgetId: String,
    fromDate: String?,
    throughDate: String?,
): Flow<List<MonthlyIncomeSpendingAggregate>> =
    database.budgetRecordDao().observeMonthlyIncomeAndSpending(budgetId, fromDate, throughDate).stabilized()

fun LocalBudgetRepository.observeAccountDailyActivity(
    budgetId: String,
    throughDate: String,
): Flow<List<AccountDayAggregate>> = database.budgetRecordDao().observeAccountDailyActivity(budgetId, throughDate).stabilized()

fun LocalBudgetRepository.observeClearedAccountBalance(
    budgetId: String,
    accountId: String,
): Flow<Long> = database.budgetRecordDao().observeClearedAccountBalance(budgetId, accountId).stabilized()

fun LocalBudgetRepository.observeRecordIds(
    budgetId: String,
    recordType: String,
): Flow<List<String>> = database.budgetRecordDao().observeVisibleRecordIds(budgetId, recordType).stabilized()

fun LocalBudgetRepository.observeRecord(
    budgetId: String,
    recordType: String,
    recordId: String,
): Flow<BudgetRecordEntity?> = database.budgetRecordDao().observeVisibleRecord(budgetId, recordType, recordId).stabilized()

fun LocalBudgetRepository.observeRecentRecords(
    budgetId: String,
    recordType: String,
    limit: Int,
): Flow<List<BudgetRecordEntity>> = database.budgetRecordDao().observeRecentVisible(budgetId, recordType, limit).stabilized()

fun LocalBudgetRepository.observeRecentTransactionsForAccount(
    budgetId: String,
    accountId: String,
    limit: Int,
    offset: Int = 0,
): Flow<List<BudgetRecordEntity>> {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(accountId, "accountId")
    require(limit in 1..500) { "limit must be between 1 and 500" }
    require(offset >= 0) { "offset must be non-negative" }
    return database.budgetRecordDao().observeRecentTransactionsForAccount(budgetId, accountId, limit, offset).stabilized()
}

fun LocalBudgetRepository.observeVisibleRecordCount(
    budgetId: String,
    recordType: String,
): Flow<Long> = database.budgetRecordDao().observeVisibleCount(budgetId, recordType).stabilized()

fun LocalBudgetRepository.observePendingWrites(budgetId: String): Flow<List<OutboxOperationEntity>> =
    database.outboxDao().observePending(budgetId)

fun LocalBudgetRepository.observeSyncState(budgetId: String): Flow<BudgetSyncStateEntity?> = database.budgetSyncStateDao().observe(budgetId)
