@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.recordName
import kotlinx.coroutines.flow.flowOf

internal data class ReportDataBundle(
    val categories: List<CategorySpendingPoint>,
    val monthly: List<MonthlyMoneyPoint>,
    val incomeAndSpending: List<MonthlyMoneyPoint>,
    val budgetedSpent: List<BudgetedSpentPoint>,
    val netWorth: List<NetWorthPoint>,
    val displaySettings: BudgetDisplaySettings?,
)

internal class ReportsDataCache {
    private val reports = mutableMapOf<String, ReportDataBundle>()

    fun get(key: String): ReportDataBundle? = reports[key]

    fun put(
        key: String,
        value: ReportDataBundle,
    ) {
        reports[key] = value
        if (reports.size > 24) reports.remove(reports.keys.first())
    }
}

internal data class ReportChartSelection(
    val index: Int,
    val income: Boolean = false,
)

@Composable
internal fun rememberReportData(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    window: ReportWindow,
    netWorthWindow: ReportWindow,
    reportsDataCache: ReportsDataCache,
): ReportDataBundle {
    val budgetId = state.selectedBudgetId
    val categoryNames =
        remember(mobileSession.categoryRecords) {
            mobileSession.categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
        }
    val categoryFlow =
        remember(mobileSession.reportViewModels, budgetId, window, categoryNames) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.spendingByCategory.observe(budgetId, window, categoryNames)
            } else {
                flowOf(emptyList())
            }
        }
    val monthlyFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.spendingOverTime.observe(budgetId, window)
            } else {
                flowOf(emptyList())
            }
        }
    val incomeAndSpendingFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.incomeAndSpending.observe(budgetId, window)
            } else {
                flowOf(emptyList())
            }
        }
    val budgetedFlow =
        remember(mobileSession.reportViewModels, budgetId, window, categoryNames) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.budgetedAndSpent.observe(budgetId, window, categoryNames)
            } else {
                flowOf(emptyList())
            }
        }
    val today = platformTodayIsoDate()
    val netWorthFlow =
        remember(mobileSession.reportViewModels, budgetId, netWorthWindow, today, accounts) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.netWorth.observe(budgetId, netWorthWindow.monthIds, today, accounts)
            } else {
                flowOf(emptyList())
            }
        }
    val cacheKey =
        "$budgetId|${window.startDate}|${window.throughDate}|${window.allDates}|" +
            "${netWorthWindow.monthIds}|${categoryNames.hashCode()}|${accounts.hashCode()}"
    val cachedData = reportsDataCache.get(cacheKey)
    val categoryInitial = emptyList<CategorySpendingPoint>()
    val monthlyInitial = emptyList<MonthlyMoneyPoint>()
    val incomeAndSpendingInitial = emptyList<MonthlyMoneyPoint>()
    val budgetedInitial = emptyList<BudgetedSpentPoint>()
    val netWorthInitial = emptyList<NetWorthPoint>()
    val categoriesBySpend by categoryFlow.collectAsState(cachedData?.categories ?: categoryInitial)
    val monthly by monthlyFlow.collectAsState(cachedData?.monthly ?: monthlyInitial)
    val incomeAndSpending by incomeAndSpendingFlow.collectAsState(cachedData?.incomeAndSpending ?: incomeAndSpendingInitial)
    val budgetedSpent by budgetedFlow.collectAsState(cachedData?.budgetedSpent ?: budgetedInitial)
    val netWorth by netWorthFlow.collectAsState(cachedData?.netWorth ?: netWorthInitial)
    val data = ReportDataBundle(categoriesBySpend, monthly, incomeAndSpending, budgetedSpent, netWorth, state.budgetDisplaySettings)
    SideEffect {
        if (reportsDataCache.get(cacheKey) != data) reportsDataCache.put(cacheKey, data)
    }
    return data
}
