package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.checkup.DataHealthScreen

/** Screens for the checkup routes. */
internal val checkupScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.DATA_HEALTH to
            @Composable { ctx ->
                with(ctx) {
                    DataHealthScreen(state, navigation, mobileSession, liveTransactions)
                }
            },
    )
