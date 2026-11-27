@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.app.PaneHeader
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.app.SupportingPane
import app.tandemforactual.ui.app.closingPane
import app.tandemforactual.ui.app.openingInPane
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.feature.budget.BudgetPreferencesScreen
import app.tandemforactual.ui.feature.budget.BudgetScreen
import app.tandemforactual.ui.feature.budget.CategoryDetailScreen
import app.tandemforactual.ui.feature.budget.CategoryGroupScreen
import app.tandemforactual.ui.feature.budget.SyncPreferencesScreen

/** Screens for the budget routes. */
internal val budgetScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.BUDGET to @Composable { ctx -> BudgetWithCategoryPane(ctx) },
        AppRoute.CATEGORY_GROUP to
            @Composable { ctx ->
                with(ctx) {
                    CategoryGroupScreen(state, navigation, liveBudgetMonth, mobileSession)
                }
            },
        AppRoute.CATEGORY_DETAIL to
            @Composable { ctx ->
                with(ctx) {
                    CategoryDetailScreen(state, navigation, liveBudgetMonth, mobileSession)
                }
            },
        AppRoute.BUDGET_PREFERENCES to
            @Composable { ctx ->
                with(ctx) {
                    BudgetPreferencesScreen(state, mobileSession)
                }
            },
        AppRoute.SYNC_PREFERENCES to
            @Composable { ctx ->
                with(ctx) {
                    SyncPreferencesScreen(
                        state,
                        mobileSession,
                        navigation,
                        backgroundSyncSupported,
                    )
                }
            },
    )

/** The budget month, with a category's detail beside it when the window has room for both. */
@Composable
private fun BudgetWithCategoryPane(ctx: ScreenContext) {
    with(ctx) {
        var categoryOpen by rememberSaveable(state.selectedBudgetId) { mutableStateOf(false) }
        val close: () -> Unit = { categoryOpen = false }
        SupportingPane(
            paneKey = state.selectedCategoryId.takeIf { categoryOpen && it.isNotBlank() },
            main = { paneFits ->
                TandemBackHandler(enabled = paneFits && categoryOpen, onBack = close, onBackProgress = { _, _ -> })
                BudgetScreen(
                    state,
                    if (paneFits) navigation.openingInPane(setOf(AppRoute.CATEGORY_DETAIL)) { categoryOpen = true } else navigation,
                    mobileSession,
                    liveTransactions,
                    liveSchedules,
                    liveBudgetMonth,
                    onSyncNow,
                    onBudgetGroupReorderModeChange,
                )
            },
            pane = {
                Column(Modifier.fillMaxSize()) {
                    PaneHeader(categoryName(liveBudgetMonth, state.selectedCategoryId), close)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CategoryDetailScreen(state, navigation.closingPane(close), liveBudgetMonth, mobileSession)
                    }
                }
            },
        )
    }
}

private fun categoryName(
    month: LiveBudgetMonthSummary?,
    categoryId: String,
): String =
    month
        ?.groups
        .orEmpty()
        .asSequence()
        .flatMap { it.categories.asSequence() }
        .firstOrNull { it.id == categoryId }
        ?.name ?: "Category"
