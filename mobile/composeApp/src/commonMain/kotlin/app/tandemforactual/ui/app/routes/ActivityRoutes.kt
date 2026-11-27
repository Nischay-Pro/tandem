package app.tandemforactual.ui.app.routes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.ListDetailPanes
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.app.activityDetailKey
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.activity.ActivityScreen
import app.tandemforactual.ui.feature.activity.ActivitySearchScreen
import app.tandemforactual.ui.feature.transactions.TransactionDetailScreen

/** Screens for the activity routes. */
internal val activityScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.ACTIVITY to
            @Composable { ctx ->
                with(ctx) {
                    ListDetailPanes(
                        detailKey = activityDetailKey(state, liveActivityTransactions),
                        emptyTitle = "Choose a transaction",
                        emptyMessage = "Its details appear here.",
                        list = { detailInPane ->
                            ActivityScreen(
                                state,
                                mobileSession,
                                navigation,
                                liveActivityTransactions,
                                activitySortCache,
                                onActivitySelectionTotalChanged,
                                mobileSession.isSyncing,
                                onSyncNow,
                                detailInPane,
                            )
                        },
                        detail = { TransactionDetailScreen(state, mobileSession, navigation, merchantIconMatcher, showPayee = true) },
                    )
                }
            },
        AppRoute.ACTIVITY_SEARCH to
            @Composable { ctx ->
                with(ctx) {
                    ActivitySearchScreen(
                        state,
                        mobileSession,
                        navigation,
                        merchantIconMatcher,
                        onActivitySelectionTotalChanged,
                        autoFocusSearch = true,
                    )
                }
            },
    )
