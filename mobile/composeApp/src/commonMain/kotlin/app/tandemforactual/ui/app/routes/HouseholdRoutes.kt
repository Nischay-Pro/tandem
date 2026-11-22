package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.household.HouseholdScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

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

/** This feature's entries in the full-screen menu. */
internal val householdMenu: List<MenuEntry> =
    listOf(
        MenuEntry(250) { ctx -> with(ctx) { AppMenuItem(Lucide.Users, "Household", "Share this budget") { open(AppRoute.HOUSEHOLD) } } },
    )
