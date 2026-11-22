package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.about.AboutScreen
import app.tandemforactual.ui.feature.about.LocalDataScreen
import app.tandemforactual.ui.feature.about.MerchantIconLicensesScreen

/** Screens for the about routes. */
internal val aboutScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.LOCAL_DATA to
            @Composable { ctx ->
                with(ctx) {
                    LocalDataScreen(state, mobileSession, navigation)
                }
            },
        AppRoute.ABOUT to
            @Composable { ctx ->
                with(ctx) {
                    AboutScreen(merchantIconMatcherLoadError, navigation)
                }
            },
        AppRoute.MERCHANT_ICON_LICENSES to
            @Composable { ctx ->
                with(ctx) {
                    MerchantIconLicensesScreen(merchantIconCatalog, merchantIconCatalogLoadError)
                }
            },
    )
