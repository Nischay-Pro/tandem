package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.banking.BankConnectionsScreen
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide

/** Screens for the banking routes. */
internal val bankingScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.BANK_CONNECTIONS to
            @Composable { ctx ->
                with(ctx) {
                    BankConnectionsScreen(state, mobileSession, liveAccounts)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val bankingMenu: List<MenuEntry> =
    listOf(
        MenuEntry(60) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Landmark, "Bank connections", "Link and sync") { open(AppRoute.BANK_CONNECTIONS) }
            }
        },
    )
