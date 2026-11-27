package app.tandemforactual.ui.core.navigation

import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.RulesReferenceFilter

/** Opens search on every transaction with this payee or category, across all dates. */
internal fun openActivityFor(
    state: AppState,
    navigation: AppNavigation,
    field: ActivitySearchField,
    id: String,
    label: String,
) {
    state.activitySearchQuery = ""
    state.activitySearchResultCache = null
    state.activitySearchFilters = listOf(ActivitySearchFilter(field, id, label))
    navigation.open(AppRoute.ACTIVITY_SEARCH)
}

/** Opens the rules list showing only the rules that name one of [ids]. */
internal fun openRulesUsing(
    state: AppState,
    navigation: AppNavigation,
    ids: Collection<String>,
    name: String,
) {
    state.ruleEditorRuleId = null
    state.rulesReferenceFilter = RulesReferenceFilter(ids.toSet(), "Using $name")
    navigation.open(AppRoute.BUDGET_RULES)
}
