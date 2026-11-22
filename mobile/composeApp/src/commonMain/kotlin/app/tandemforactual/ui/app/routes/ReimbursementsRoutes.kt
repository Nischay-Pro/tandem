package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.reimbursements.ReimbursementsScreen

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
