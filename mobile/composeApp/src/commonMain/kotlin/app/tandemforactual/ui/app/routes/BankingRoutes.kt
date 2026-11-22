package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.banking.BankConnectionsScreen

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
