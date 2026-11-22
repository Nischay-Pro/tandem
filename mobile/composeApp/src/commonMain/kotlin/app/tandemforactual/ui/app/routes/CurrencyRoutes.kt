package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.currency.ExchangeRatesScreen
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide

/** Screens for the currency routes. */
internal val currencyScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.EXCHANGE_RATES to
            @Composable { ctx ->
                with(ctx) {
                    ExchangeRatesScreen(state, mobileSession, liveAccounts, navigation)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val currencyMenu: List<MenuEntry> =
    listOf(
        MenuEntry(260) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.Coins, "Currencies", "Accounts in other currencies") { open(AppRoute.EXCHANGE_RATES) }
            }
        },
    )
