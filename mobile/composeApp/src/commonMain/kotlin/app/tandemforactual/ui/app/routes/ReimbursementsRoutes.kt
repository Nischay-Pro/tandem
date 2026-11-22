package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.reimbursements.ReimbursementsScreen
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Lucide

/** Screens for the reimbursements routes. */
internal val reimbursementsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.REIMBURSEMENTS to
            @Composable { ctx ->
                with(ctx) {
                    ReimbursementsScreen(state, mobileSession)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val reimbursementsMenu: List<MenuEntry> =
    listOf(
        MenuEntry(190) { ctx ->
            with(ctx) {
                AppMenuItem(
                    Lucide.HandCoins,
                    "Reimbursements",
                    reimbursementsOwedMinor.takeIf { it > 0 }?.let { "${formatMoney(it)} owed" },
                ) {
                    open(AppRoute.REIMBURSEMENTS)
                }
            }
        },
    )
