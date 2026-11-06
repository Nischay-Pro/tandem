package app.tandemforactual.data.database

import androidx.room3.Room
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.openTandemDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportAggregateQueryTest {
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
                        transaction("june-supplies", "2026-06-03", "checking", "supplies", -3_000),
                        transaction("locally-deleted", "2026-05-07", "checking", "groceries", -8_000, localDeleted = true),
                        transaction("remotely-deleted", "2026-05-08", "checking", "groceries", -9_000, remoteDeleted = true),
                        transaction("other-budget", "2026-05-01", "checking", "groceries", -900_000, budgetId = "other"),
                    ),
                )

                assertEquals(
                    listOf(
                        CategoryMonthAggregate("2026-05", "groceries", 12_500),
                        CategoryMonthAggregate("2026-06", "supplies", 3_000),
                    ),
                    database.budgetRecordDao().observeCategoryMonthAggregates(BUDGET, "2026-05-01", "2026-06-30").first(),
                )
                assertEquals(
                    listOf(
                        MonthlyIncomeSpendingAggregate("2026-05", 500_000, 12_500),
                        MonthlyIncomeSpendingAggregate("2026-06", 0, 3_000),
                    ),
                    database.budgetRecordDao().observeMonthlyIncomeAndSpending(BUDGET, "2026-05-01", "2026-06-30").first(),
                )
                assertEquals(
                    listOf(
                        AccountDayAggregate("checking", "2026-06-03", -3_000),
                        AccountDayAggregate("checking", "2026-05-06", -4_000),
                        AccountDayAggregate("checking", "2026-05-05", 500_000),
                        AccountDayAggregate("checking", "2026-05-02", -2_500),
                        AccountDayAggregate("checking", "2026-05-01", -10_000),
                    ),
                    database.budgetRecordDao().observeAccountDailyActivity(BUDGET, "2026-06-30").first(),
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
    ) = BudgetRecordEntity(
        budgetId = budgetId,
        recordType = "transaction",
        recordId = recordId,
        remoteJson = if (localDeleted) null else "{}",
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
    )

    private companion object {
        const val BUDGET = "household"
    }
}
