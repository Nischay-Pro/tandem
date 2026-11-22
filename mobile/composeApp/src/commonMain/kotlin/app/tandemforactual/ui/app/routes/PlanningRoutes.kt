package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.planning.DebtPayoffScreen
import app.tandemforactual.ui.feature.planning.GoalTimelineScreen
import app.tandemforactual.ui.feature.planning.WhatIfScreen
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingDown

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

/** This feature's entries in the full-screen menu. */
internal val planningMenu: List<MenuEntry> =
    listOf(
        MenuEntry(160) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.FlaskConical, "What if", "Try income and spending changes") { open(AppRoute.WHAT_IF) }
            }
        },
        MenuEntry(210) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Flag, "Goal timeline", "When each goal is reached") { open(AppRoute.GOAL_TIMELINE) }
            }
        },
        MenuEntry(220) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.TrendingDown, "Debt payoff", "Snowball or avalanche") { open(AppRoute.DEBT_PAYOFF) }
            }
        },
    )
