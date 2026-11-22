package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.planning.DebtPayoffScreen
import app.tandemforactual.ui.feature.planning.GoalTimelineScreen
import app.tandemforactual.ui.feature.planning.WhatIfScreen

/** Screens for the planning routes. */
internal val planningScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.WHAT_IF to
            @Composable { ctx ->
                with(ctx) {
                    WhatIfScreen(state, mobileSession, liveAccounts, liveBudgetMonth, liveTransactions)
                }
            },
        AppRoute.DEBT_PAYOFF to
            @Composable { ctx ->
                with(ctx) {
                    DebtPayoffScreen(state, mobileSession, liveAccounts, liveBudgetMonth)
                }
            },
        AppRoute.GOAL_TIMELINE to
            @Composable { ctx ->
                with(ctx) {
                    GoalTimelineScreen(state, mobileSession, liveAccounts, liveBudgetMonth)
                }
            },
    )
