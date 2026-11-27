package app.tandemforactual.data.database

import androidx.room3.Room
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.openTandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.observeAccountDailyActivity
import app.tandemforactual.data.store.observeCategoryMonthAggregates
import app.tandemforactual.data.store.observeMonthlyIncomeAndSpending
import app.tandemforactual.data.store.observeRecordIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReportAggregateQueryTest {
    @Test
    fun refundsReduceExpenseSpendingAndIncomeCategoriesStayOutOfSpending() =
        runBlocking {
            val database =
                openTandemDatabase(
                    Room
                        .inMemoryDatabaseBuilder<TandemDatabase>()
                        .setQueryCoroutineContext(Dispatchers.IO),
                )
            try {
                database.budgetRecordDao().putAll(
                    listOf(
                        category("groceries", isIncome = false),
                        category("salary", isIncome = true),
                        transaction("purchase", "2026-01-03", "checking", "groceries", -10_000),
                        transaction("partial-refund", "2026-01-07", "checking", "groceries", 3_000),
                        transaction("second-refund", "2026-01-08", "checking", "groceries", 2_000),
                        transaction("paycheck", "2026-01-10", "checking", "salary", 50_000),
                        transaction("uncategorized-income", "2026-01-12", "checking", null, 20_000),
                    ),
                )

                assertEquals(
                    listOf(CategoryMonthAggregate("2026-01", "groceries", 5_000)),
                    database.budgetRecordDao().observeCategoryMonthAggregates(BUDGET, "2026-01-01", "2026-01-31").first(),
                )
                assertEquals(
                    listOf(MonthlyIncomeSpendingAggregate("2026-01", 70_000, 5_000)),
                    database.budgetRecordDao().observeMonthlyIncomeAndSpending(BUDGET, "2026-01-01", "2026-01-31").first(),
                )
                val repository = LocalBudgetRepository(database) { 100L }
                assertEquals(
                    database.budgetRecordDao().observeCategoryMonthAggregates(BUDGET, "2026-01-01", "2026-01-31").first(),
                    repository.observeCategoryMonthAggregates(BUDGET, "2026-01-01", "2026-01-31").first(),
                )
                assertEquals(
                    database.budgetRecordDao().observeMonthlyIncomeAndSpending(BUDGET, "2026-01-01", "2026-01-31").first(),
                    repository.observeMonthlyIncomeAndSpending(BUDGET, "2026-01-01", "2026-01-31").first(),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun aggregatesCountOnBudgetAccountsOnlyAndAgreeOnSpending() =
        runBlocking {
            val database =
                openTandemDatabase(
                    Room
                        .inMemoryDatabaseBuilder<TandemDatabase>()
                        .setQueryCoroutineContext(Dispatchers.IO),
                )
            try {
                database.budgetRecordDao().putAll(
                    listOf(
                        category("groceries", isIncome = false),
                        category("savings", isIncome = false),
                        category("salary", isIncome = true),
                        account("checking", """{"offbudget":false}"""),
                        account("brokerage", """{"offbudget":true}"""),
                        account("house", """{"offBudget":true}"""),
                        account("broken", "not-json"),
                        transaction("purchase", "2026-02-02", "checking", "groceries", -10_000),
                        transaction("paycheck", "2026-02-03", "checking", "salary", 60_000),
                        transaction("to-brokerage", "2026-02-04", "checking", "savings", -25_000, transfer = true),
                        transaction("from-checking", "2026-02-04", "brokerage", null, 25_000, transfer = true),
                        transaction("dividend", "2026-02-05", "brokerage", "salary", 4_000),
                        transaction("brokerage-fee", "2026-02-06", "brokerage", "groceries", -1_500),
                        transaction("house-upkeep", "2026-02-07", "house", "groceries", -9_000),
                        transaction("uncategorized-spend", "2026-02-08", "checking", null, -7_000),
                        transaction("to-savings", "2026-02-09", "checking", null, -3_000, transfer = true),
                        transaction("from-checking-on-budget", "2026-02-09", "savings-account", null, 3_000, transfer = true),
                        transaction("broken-account", "2026-02-10", "broken", "groceries", -500),
                        transaction("no-account", "2026-02-11", null, "groceries", -200),
                    ),
                )

                val categories = database.budgetRecordDao().observeCategoryMonthAggregates(BUDGET, null, null).first()
                assertEquals(
                    listOf(
                        CategoryMonthAggregate("2026-02", "groceries", 10_700),
                        CategoryMonthAggregate("2026-02", "savings", 25_000),
                    ),
                    categories,
                )
                assertEquals(
                    listOf(MonthlyIncomeSpendingAggregate("2026-02", 60_000, categories.sumOf { it.spendingMinor })),
                    database.budgetRecordDao().observeMonthlyIncomeAndSpending(BUDGET, null, null).first(),
                )
            } finally {
                database.close()
            }
        }

    @Test
    fun categoryMonthMonthlyAndAccountDayAggregatesExcludeTransfersAndDeletedRows() =
        runBlocking {
            val database =
                openTandemDatabase(
                    Room
                        .inMemoryDatabaseBuilder<TandemDatabase>()
                        .setQueryCoroutineContext(Dispatchers.IO),
                )
            try {
                database.budgetRecordDao().putAll(
                    listOf(
                        transaction("groceries-a", "2026-05-01", "checking", "groceries", -10_000),
                        transaction("groceries-b", "2026-05-02", "checking", "groceries", -2_500),
                        transaction("income", "2026-05-05", "checking", null, 500_000),
                        transaction("transfer", "2026-05-06", "checking", null, -4_000, transfer = true),
                        transaction(
                            "may-split-parent",
                            "2026-05-09",
                            "checking",
                            null,
                            -10_000,
                            isParent = true,
                            json = """{"is_parent":true}""",
                        ),
                        transaction(
                            "may-split-groceries",
                            "2026-05-09",
                            "checking",
                            "groceries",
                            -6_000,
                            json = """{"is_child":true,"parent_id":"may-split-parent"}""",
                        ),
                        transaction(
                            "may-split-supplies",
                            "2026-05-09",
                            "checking",
                            "supplies",
                            -4_000,
                            json = """{"is_child":true,"parent_id":"may-split-parent"}""",
                        ),
                        transaction("june-supplies", "2026-06-03", "checking", "supplies", -3_000),
                        transaction(
                            "june-split-parent",
                            "2026-06-04",
                            "checking",
                            null,
                            -1_000,
                            isParent = true,
                            json = """{"isParent":true}""",
                        ),
                        transaction(
                            "june-split-groceries",
                            "2026-06-04",
                            "checking",
                            "groceries",
                            -700,
                            json = """{"isChild":true,"parentId":"june-split-parent"}""",
                        ),
                        transaction(
                            "june-split-supplies",
                            "2026-06-04",
                            "checking",
                            "supplies",
                            -300,
                            json = """{"isChild":true,"parentId":"june-split-parent"}""",
                        ),
                        transaction("locally-deleted", "2026-05-07", "checking", "groceries", -8_000, localDeleted = true),
                        transaction("remotely-deleted", "2026-05-08", "checking", "groceries", -9_000, remoteDeleted = true),
                        transaction("other-budget", "2026-05-01", "checking", "groceries", -900_000, budgetId = "other"),
                    ),
                )

                assertEquals(
                    listOf(
                        CategoryMonthAggregate("2026-05", "groceries", 18_500),
                        CategoryMonthAggregate("2026-05", "supplies", 4_000),
                        CategoryMonthAggregate("2026-06", "groceries", 700),
                        CategoryMonthAggregate("2026-06", "supplies", 3_300),
                    ),
                    database.budgetRecordDao().observeCategoryMonthAggregates(BUDGET, "2026-05-01", "2026-06-30").first(),
                )
                assertEquals(
                    listOf(
                        MonthlyIncomeSpendingAggregate("2026-05", 500_000, 22_500),
                        MonthlyIncomeSpendingAggregate("2026-06", 0, 4_000),
                    ),
                    database.budgetRecordDao().observeMonthlyIncomeAndSpending(BUDGET, "2026-05-01", "2026-06-30").first(),
                )
                assertEquals(
                    listOf(
                        AccountDayAggregate("checking", "2026-06-04", -1_000),
                        AccountDayAggregate("checking", "2026-06-03", -3_000),
                        AccountDayAggregate("checking", "2026-05-09", -10_000),
                        AccountDayAggregate("checking", "2026-05-06", -4_000),
                        AccountDayAggregate("checking", "2026-05-05", 500_000),
                        AccountDayAggregate("checking", "2026-05-02", -2_500),
                        AccountDayAggregate("checking", "2026-05-01", -10_000),
                    ),
                    database.budgetRecordDao().observeAccountDailyActivity(BUDGET, "2026-06-30").first(),
                )
                val repository = LocalBudgetRepository(database) { 100L }
                assertEquals(
                    database.budgetRecordDao().observeAccountDailyActivity(BUDGET, "2026-06-30").first(),
                    repository.observeAccountDailyActivity(BUDGET, "2026-06-30").first(),
                )
                assertEquals(
                    database.budgetRecordDao().observeVisibleRecordIds(BUDGET, "transaction").first(),
                    repository.observeRecordIds(BUDGET, "transaction").first(),
                )
            } finally {
                database.close()
            }
        }

    private fun transaction(
        recordId: String,
        date: String,
        accountId: String?,
        categoryId: String?,
        amountMinor: Long,
        transfer: Boolean = false,
        localDeleted: Boolean = false,
        remoteDeleted: Boolean = false,
        budgetId: String = BUDGET,
        isParent: Boolean = false,
        json: String = "{}",
    ) = BudgetRecordEntity(
        budgetId = budgetId,
        recordType = "transaction",
        recordId = recordId,
        remoteJson = if (localDeleted) null else json,
        remoteRevision = "revision-$recordId",
        localJson = null,
        localDeleted = localDeleted,
        remoteDeleted = remoteDeleted,
        syncGeneration = "generation",
        recordDate = date,
        accountId = accountId,
        categoryId = categoryId,
        payeeId = null,
        amountMinor = amountMinor,
        budgetMonth = date.take(7),
        transfer = transfer,
        isParent = isParent,
    )

    private fun account(
        recordId: String,
        json: String,
    ) = BudgetRecordEntity(
        budgetId = BUDGET,
        recordType = "account",
        recordId = recordId,
        remoteJson = json,
        remoteRevision = "revision-$recordId",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "generation",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
    )

    private fun category(
        recordId: String,
        isIncome: Boolean,
    ) = BudgetRecordEntity(
        budgetId = BUDGET,
        recordType = "category",
        recordId = recordId,
        remoteJson = """{"name":"$recordId","is_income":$isIncome}""",
        remoteRevision = "revision-$recordId",
        localJson = null,
        localDeleted = false,
        remoteDeleted = false,
        syncGeneration = "generation",
        recordDate = null,
        accountId = null,
        categoryId = null,
        payeeId = null,
        amountMinor = null,
        budgetMonth = null,
    )

    private companion object {
        const val BUDGET = "household"
    }
}
