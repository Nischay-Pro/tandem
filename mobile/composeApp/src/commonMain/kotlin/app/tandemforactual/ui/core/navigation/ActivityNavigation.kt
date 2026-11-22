package app.tandemforactual.ui.core.navigation

import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState

internal fun openGeneralActivitySearch(
    state: AppState,
    navigation: AppNavigation,
) {
    state.activitySearchQuery = ""
    state.activitySearchFocusRequest += 1
    state.activitySearchFilters = emptyList()
    navigation.open(AppRoute.ACTIVITY_SEARCH)
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
