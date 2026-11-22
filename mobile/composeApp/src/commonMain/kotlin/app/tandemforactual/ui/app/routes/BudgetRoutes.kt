package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.budget.BudgetPreferencesScreen
import app.tandemforactual.ui.feature.budget.BudgetScreen
import app.tandemforactual.ui.feature.budget.CategoryDetailScreen
import app.tandemforactual.ui.feature.budget.CategoryGroupScreen
import app.tandemforactual.ui.feature.budget.SyncPreferencesScreen

/** Screens for the budget routes. */
internal val budgetScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.BUDGET to
            @Composable { ctx ->
                with(ctx) {
                    BudgetScreen(
                        state,
                        navigation,
                        mobileSession,
                        liveTransactions,
                        liveSchedules,
                        liveBudgetMonth,
                        onSyncNow,
                        onBudgetGroupReorderModeChange,
                    )
                }
            },
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
