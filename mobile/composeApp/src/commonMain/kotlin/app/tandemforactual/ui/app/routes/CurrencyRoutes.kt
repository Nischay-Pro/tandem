package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.currency.ExchangeRatesScreen

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
