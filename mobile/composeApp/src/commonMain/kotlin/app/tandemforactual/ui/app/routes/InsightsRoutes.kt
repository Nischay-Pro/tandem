package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.insights.CashFlowScreen
import app.tandemforactual.ui.feature.insights.MonthlyReviewScreen
import app.tandemforactual.ui.feature.insights.SpendingForecastScreen
import app.tandemforactual.ui.feature.insights.SpendingTrendsScreen

/** Screens for the insights routes. */
internal val insightsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.CASH_FLOW to
            @Composable { ctx ->
                with(ctx) {
                    CashFlowScreen(state, liveAccounts, liveSchedules)
                }
            },
        AppRoute.MONTHLY_REVIEW to
            @Composable { ctx ->
                with(ctx) {
                    MonthlyReviewScreen(state, liveBudgetMonth, liveTransactions)
                }
            },
        AppRoute.SPENDING_TRENDS to
            @Composable { ctx ->
                with(ctx) {
                    SpendingTrendsScreen(state, mobileSession)
                }
            },
        AppRoute.SPENDING_FORECAST to
            @Composable { ctx ->
                with(ctx) {
                    SpendingForecastScreen(state, mobileSession, liveBudgetMonth)
                }
            },
    )
