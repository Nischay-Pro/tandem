package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.residence.PeriodEditScreen
import app.tandemforactual.ui.feature.residence.RelocationScreen
import app.tandemforactual.ui.feature.residence.ResidenceScreen
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide

/** Screens for the residence routes. */
internal val residenceScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.RESIDENCE to @Composable { ctx -> with(ctx) { ResidenceScreen(state, mobileSession, navigation) } },
        AppRoute.RESIDENCE_PERIOD to @Composable { ctx -> with(ctx) { PeriodEditScreen(state, mobileSession, navigation) } },
        AppRoute.RELOCATION to @Composable { ctx -> with(ctx) { RelocationScreen(state, mobileSession, navigation) } },
    )

/** This feature's entries in the full-screen menu. */
internal val residenceMenu: List<MenuEntry> =
    listOf(
        MenuEntry(205) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Globe, "Where you’ve lived", "Countries, moves and their plans") { open(AppRoute.RESIDENCE) }
            }
        },
    )
