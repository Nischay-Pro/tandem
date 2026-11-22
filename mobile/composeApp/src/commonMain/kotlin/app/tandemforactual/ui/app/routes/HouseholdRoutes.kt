package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.household.HouseholdScreen

/** Screens for the household routes. */
internal val householdScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.HOUSEHOLD to
            @Composable { ctx ->
                with(ctx) {
                    HouseholdScreen(state, mobileSession, deviceName)
                }
            },
    )
