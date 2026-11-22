package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.transactions.TransactionDetailScreen
import app.tandemforactual.ui.feature.transactions.TransactionFormScreen

/** Screens for the transactions routes. */
internal val transactionsScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.TRANSACTION_DETAIL to
            @Composable { ctx ->
                with(ctx) {
                    TransactionDetailScreen(state, mobileSession, navigation, merchantIconMatcher)
                }
            },
        AppRoute.NEW_TRANSACTION to
            @Composable { ctx ->
                with(ctx) {
                    TransactionFormScreen(
                        route,
                        state,
                        navigation,
                        mobileSession,
                        liveAccounts,
                        onSaveTransaction,
                        onDeleteTransaction,
                        onCreateTransaction,
                    )
                }
            },
        AppRoute.EDIT_TRANSACTION to
            @Composable { ctx ->
                with(ctx) {
                    TransactionFormScreen(
                        route,
                        state,
                        navigation,
                        mobileSession,
                        liveAccounts,
                        onSaveTransaction,
                        onDeleteTransaction,
                        onCreateTransaction,
                    )
                }
            },
    )
