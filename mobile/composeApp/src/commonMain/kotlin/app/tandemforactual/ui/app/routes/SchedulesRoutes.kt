package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.schedules.UpcomingSchedulesScreen

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
