package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.checkup.DataHealthScreen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Stethoscope

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

/** This feature's entries in the full-screen menu. */
internal val checkupMenu: List<MenuEntry> =
    listOf(
        MenuEntry(180) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Stethoscope, "Budget checkup", "Find things to fix") { open(AppRoute.DATA_HEALTH) }
            }
        },
    )
