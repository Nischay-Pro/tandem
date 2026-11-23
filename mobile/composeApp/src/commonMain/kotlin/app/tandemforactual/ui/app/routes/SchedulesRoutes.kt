package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.schedules.UpcomingSchedulesScreen
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Lucide

/** Screens for the schedules routes. */
internal val schedulesScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.SCHEDULES to
            @Composable { ctx ->
                with(ctx) {
                    UpcomingSchedulesScreen(
                        state,
                        mobileSession,
                        liveSchedules,
                        liveAccounts,
                        mobileSession.payeeRecords,
                    )
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val schedulesMenu: List<MenuEntry> =
    listOf(
        MenuEntry(80) { ctx ->
            with(ctx) {
                AppMenuItem(
                    Lucide.CalendarClock,
                    "Schedules",
                    scheduleCount.takeIf { it > 0 }?.let { "${formatInteger(it)} upcoming" },
                ) { open(AppRoute.SCHEDULES) }
            }
        },
    )
