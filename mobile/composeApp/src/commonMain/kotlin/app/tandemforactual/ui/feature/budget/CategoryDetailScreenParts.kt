@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState

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

internal fun openRecentActivity(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFilters = emptyList()
    state.activitySearchResultCache = null
    navigation.selectTabRoot(AppRoute.ACTIVITY)
}
