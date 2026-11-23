package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.insights.BillCalendarScreen
import app.tandemforactual.ui.feature.insights.CashFlowScreen
import app.tandemforactual.ui.feature.insights.MonthlyReviewScreen
import app.tandemforactual.ui.feature.insights.SpendingForecastScreen
import app.tandemforactual.ui.feature.insights.SpendingTrendsScreen
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.ChartSpline
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp

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
        AppRoute.BILL_CALENDAR to @Composable { ctx -> with(ctx) { BillCalendarScreen(state, liveSchedules) } },
        AppRoute.SPENDING_FORECAST to
            @Composable { ctx ->
                with(ctx) {
                    SpendingForecastScreen(state, mobileSession, liveBudgetMonth)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val insightsMenu: List<MenuEntry> =
    listOf(
        MenuEntry(130) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.CalendarCheck, "Monthly review", "How this month went") { open(AppRoute.MONTHLY_REVIEW) }
            }
        },
        MenuEntry(140) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.TrendingUp, "Spending trends", "Compared with past months") { open(AppRoute.SPENDING_TRENDS) }
            }
        },
        MenuEntry(150) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Gauge, "Spending forecast", "Where this month lands") { open(AppRoute.SPENDING_FORECAST) }
            }
        },
        MenuEntry(172) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.CalendarDays, "Bill calendar", "Bills and paydays by day") { open(AppRoute.BILL_CALENDAR) }
            }
        },
        MenuEntry(
            170,
        ) { ctx -> with(ctx) { AppMenuItem(Lucide.ChartSpline, "Cash flow", "Your balance ahead") { open(AppRoute.CASH_FLOW) } } },
    )
