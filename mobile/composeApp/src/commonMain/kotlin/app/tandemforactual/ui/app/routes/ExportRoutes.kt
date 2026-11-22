package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.export.ExportScreen

/** Screens for the export routes. */
internal val exportScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.EXPORT to
            @Composable { ctx ->
                with(ctx) {
                    ExportScreen(state, mobileSession)
                }
            },
    )
