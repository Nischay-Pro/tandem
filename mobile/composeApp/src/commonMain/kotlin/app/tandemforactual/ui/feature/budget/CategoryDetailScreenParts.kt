@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.feature.activity.ActivitySearchField
import app.tandemforactual.ui.feature.activity.ActivitySearchFilter
import app.tandemforactual.ui.feature.reports.ReportWindow

internal fun openCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            ActivitySearchFilter(ActivitySearchField.DATE_MONTH, state.selectedMonthId, "Date: ${state.selectedMonth}"),
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openGeneralActivitySearch(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    state.activitySearchFilters = emptyList()
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openRecentActivity(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFilters = emptyList()
    state.activitySearchResultCache = null
    navigation.selectTabRoot(AppRoute.ACTIVITY)
}

internal fun openAccountActivity(
    state: AppState,
    navigation: AppNavigation,
    accountId: String,
    accountName: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchResultCache = null
    state.activitySearchFilters =
        listOf(ActivitySearchFilter(ActivitySearchField.ACCOUNT, accountId.ifBlank { accountName }, "Account: $accountName"))
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

internal fun openReportCategoryActivity(
    state: AppState,
    navigation: AppNavigation,
    categoryId: String,
    categoryName: String,
    window: ReportWindow,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    val dateFilter =
        when {
            window.allDates ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: All dates",
                )
            window.monthCount == 1 ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_MONTH,
                    window.endMonthId,
                    "Date: ${reportMonthLabel(window.endMonthId)}",
                )
            else ->
                ActivitySearchFilter(
                    ActivitySearchField.DATE_RANGE,
                    "${window.startDate}..${window.throughDate}",
                    "Date: ${reportMonthLabel(window.startMonthId)} – ${reportMonthLabel(window.endMonthId)}",
                )
        }
    state.activitySearchFilters =
        listOf(
            ActivitySearchFilter(
                ActivitySearchField.CATEGORY,
                if (state.selectedBudgetId.isBlank()) categoryName else categoryId.ifBlank { categoryName },
                "Category: $categoryName",
            ),
            dateFilter,
        )
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}
