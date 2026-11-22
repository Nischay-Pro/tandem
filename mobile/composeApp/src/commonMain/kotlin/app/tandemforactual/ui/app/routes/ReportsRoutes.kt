package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.reports.CustomReportScreen
import app.tandemforactual.ui.feature.reports.CustomReportsScreen
import app.tandemforactual.ui.feature.reports.ReportDetailScreen
import app.tandemforactual.ui.feature.reports.ReportKind
import app.tandemforactual.ui.feature.reports.ReportsScreen
import app.tandemforactual.ui.feature.reports.openReportCategoryActivity
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.Lucide

/** Screens for the reports routes. */
internal val reportsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.REPORTS to
            @Composable { ctx ->
                with(ctx) {
                    ReportsScreen(
                        state,
                        mobileSession,
                        liveAccounts,
                        reportsDataCache,
                        onOpenReport = { kind ->
                            state.selectedReportKindName = kind.name
                            navigation.open(AppRoute.REPORT_DETAIL)
                        },
                        onOpenCategoryActivity = { categoryId, categoryName, window ->
                            openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                        },
                        onAddTransaction = { navigation.newTransaction(null) },
                        onChooseBudget = { navigation.open(AppRoute.SWITCH_BUDGET) },
                        onOpenInsight = { navigation.open(it) },
                    )
                }
            },
        AppRoute.REPORT_DETAIL to
            @Composable { ctx ->
                with(ctx) {
                    val kind =
                        ReportKind.entries.firstOrNull { it.name == state.selectedReportKindName }
                            ?: ReportKind.SPENDING_BY_CATEGORY
                    ReportDetailScreen(
                        kind,
                        state,
                        mobileSession,
                        liveAccounts,
                        reportsDataCache,
                        onOpenCategoryActivity = { categoryId, categoryName, window ->
                            openReportCategoryActivity(state, navigation, categoryId, categoryName, window)
                        },
                        onEmptyAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
                    )
                }
            },
        AppRoute.CUSTOM_REPORTS to
            @Composable { ctx ->
                with(ctx) {
                    CustomReportsScreen(state, mobileSession, navigation)
                }
            },
        AppRoute.CUSTOM_REPORT to
            @Composable { ctx ->
                with(ctx) {
                    CustomReportScreen(state, mobileSession, navigation)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val reportsMenu: List<MenuEntry> =
    listOf(
        MenuEntry(
            110,
        ) { ctx -> with(ctx) { if (!advanced) AppMenuItem(Lucide.ChartColumn, "Reports") { open(AppRoute.REPORTS) } else null } },
        MenuEntry(120) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.ChartBar, "Custom reports", "Build and save your own") { open(AppRoute.CUSTOM_REPORTS) }
            }
        },
    )
