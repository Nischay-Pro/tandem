package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.home.HomeLayoutScreen
import app.tandemforactual.ui.feature.home.HomeScreen
import app.tandemforactual.ui.feature.home.PersonalizeScreen

/** Screens for the home routes. */
internal val homeScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.PERSONALIZE to
            @Composable { ctx ->
                with(ctx) {
                    PersonalizeScreen(state, navigation)
                }
            },
        AppRoute.HOME_LAYOUT to
            @Composable { ctx ->
                with(ctx) {
                    HomeLayoutScreen(state, navigation)
                }
            },
        AppRoute.HOME to
            @Composable { ctx ->
                with(ctx) {
                    HomeScreen(
                        state,
                        mobileSession,
                        navigation,
                        liveAccounts,
                        liveBudgetMonth,
                        liveSchedules,
                        liveActivityTransactions,
                        onSyncNow,
                    )
                }
            },
    )
