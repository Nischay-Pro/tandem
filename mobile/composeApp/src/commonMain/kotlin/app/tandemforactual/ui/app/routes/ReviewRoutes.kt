package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.review.TransactionReviewScreen

/** Screens for reviewing new transactions. */
internal val reviewScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.REVIEW_TRANSACTIONS to
            @Composable { ctx ->
                with(ctx) {
                    TransactionReviewScreen(state, navigation, mobileSession, liveTransactions, liveBudgetMonth)
                }
            },
    )
