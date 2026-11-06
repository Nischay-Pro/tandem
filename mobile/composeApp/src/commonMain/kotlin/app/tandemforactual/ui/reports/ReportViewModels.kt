package app.tandemforactual.ui.reports

import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.ui.model.LiveAccountSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class ReportsViewModels(
    repository: LocalBudgetRepository,
) {
    val spendingByCategory = SpendingByCategoryReportViewModel(repository)
    val spendingOverTime = SpendingOverTimeReportViewModel(repository)
    val incomeAndSpending = IncomeAndSpendingReportViewModel(repository)
    val budgetedAndSpent = BudgetedAndSpentReportViewModel(repository)
    val netWorth = NetWorthReportViewModel(repository)
}

internal class SpendingByCategoryReportViewModel(
    private val repository: LocalBudgetRepository,
) {
    fun observe(
        budgetId: String,
        window: ReportWindow,
        categoryNames: Map<String, String>,
    ): Flow<List<CategorySpendingPoint>> =
        repository
            .observeCategoryMonthAggregates(budgetId, window.startDate, window.throughDate)
            .map { categorySpendingPoints(it, categoryNames) }
}

internal class SpendingOverTimeReportViewModel(
    private val repository: LocalBudgetRepository,
) {
    fun observe(
        budgetId: String,
        window: ReportWindow,
    ): Flow<List<MonthlyMoneyPoint>> =
        repository
            .observeMonthlyIncomeAndSpending(budgetId, window.startDate, window.throughDate)
            .map { monthlyMoneyPoints(it, window) }
}

internal class IncomeAndSpendingReportViewModel(
    private val repository: LocalBudgetRepository,
) {
    fun observe(
        budgetId: String,
        window: ReportWindow,
    ): Flow<List<MonthlyMoneyPoint>> =
        repository
            .observeMonthlyIncomeAndSpending(budgetId, window.startDate, window.throughDate)
            .map { monthlyMoneyPoints(it, window) }
}

internal class BudgetedAndSpentReportViewModel(
    private val repository: LocalBudgetRepository,
) {
    fun observe(
        budgetId: String,
        window: ReportWindow,
        categoryNames: Map<String, String>,
    ): Flow<List<BudgetedSpentPoint>> =
        combine(
            repository.observeRecords(budgetId, "budget-month"),
            repository.observeCategoryMonthAggregates(budgetId, window.startDate, window.throughDate),
        ) { monthRecords, categoryMonthAmounts ->
            budgetedSpentPoints(monthRecords, categoryMonthAmounts, window, categoryNames)
        }
}

internal class NetWorthReportViewModel(
    private val repository: LocalBudgetRepository,
) {
    fun observe(
        budgetId: String,
        monthIds: List<String>,
        todayIsoDate: String,
        accounts: List<LiveAccountSummary>,
    ): Flow<List<NetWorthPoint>> =
        repository
            .observeAccountDailyActivity(budgetId, todayIsoDate)
            .map { netWorthPoints(it, accounts, monthIds, todayIsoDate) }
}
