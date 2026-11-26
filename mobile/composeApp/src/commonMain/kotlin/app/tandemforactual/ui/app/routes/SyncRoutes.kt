package app.tandemforactual.ui.app.routes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.app.AppMenuItem
import app.tandemforactual.ui.app.MenuEntry
import app.tandemforactual.ui.app.ScreenContext
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.feature.sync.BudgetMutationReviewScreen
import app.tandemforactual.ui.feature.sync.ChangeHistoryScreen
import app.tandemforactual.ui.feature.sync.ConflictInboxScreen
import app.tandemforactual.ui.feature.sync.ConflictReviewScreen
import app.tandemforactual.ui.feature.sync.PendingChangesScreen
import app.tandemforactual.ui.feature.sync.RejectedWriteScreen
import app.tandemforactual.ui.feature.sync.SyncCenterScreen
import app.tandemforactual.ui.feature.sync.UncertainWriteScreen
import com.composables.icons.lucide.CloudSync
import com.composables.icons.lucide.GitMerge
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide

/** Screens for the sync routes. */
internal val syncScreens: Map<AppRoute, @Composable (ScreenContext) -> Unit> =
    mapOf(
        AppRoute.SYNC_CENTER to
            @Composable { ctx ->
                with(ctx) {
                    SyncCenterScreen(
                        state,
                        mobileSession,
                        navigation,
                        liveTransactions,
                        liveBudgetMonth,
                        liveSchedules,
                        onSyncNow,
                    )
                }
            },
        AppRoute.PENDING_CHANGES to
            @Composable { ctx ->
                with(ctx) {
                    PendingChangesScreen(
                        state,
                        mobileSession,
                        navigation,
                        liveTransactions,
                        liveBudgetMonth,
                        liveSchedules,
                        onSyncNow,
                    )
                }
            },
        AppRoute.CONFLICT_INBOX to
            @Composable { ctx ->
                with(ctx) {
                    ConflictInboxScreen(state, mobileSession, navigation, liveTransactions, merchantIconMatcher)
                }
            },
        AppRoute.CONFLICT_REVIEW to
            @Composable { ctx ->
                with(ctx) {
                    ConflictReviewScreen(state, mobileSession, navigation, onResolveConflict, liveTransactions, merchantIconMatcher)
                }
            },
        AppRoute.UNCERTAIN_WRITE to
            @Composable { ctx ->
                with(ctx) {
                    val selectedWrite =
                        mobileSession.pendingWrites
                            .firstOrNull { it.operationId == state.selectedConflictOperationId }
                    if (selectedWrite?.recordType == "budget-mutation") {
                        BudgetMutationReviewScreen(state, mobileSession, navigation, onDiscardWrite)
                    } else if (selectedWrite?.status == "rejected") {
                        RejectedWriteScreen(state, mobileSession, navigation, onDiscardWrite, liveTransactions)
                    } else {
                        UncertainWriteScreen(
                            state,
                            mobileSession,
                            navigation,
                            onCheckUncertainWriteStatus,
                            onRetryUncertainWrite,
                            onDiscardWrite,
                            liveTransactions,
                        )
                    }
                }
            },
        AppRoute.CHANGE_HISTORY to
            @Composable { ctx ->
                with(ctx) {
                    ChangeHistoryScreen(state, mobileSession)
                }
            },
    )

/** This feature's entries in the full-screen menu. */
internal val syncMenu: List<MenuEntry> =
    listOf(
        MenuEntry(30) { ctx ->
            with(ctx) {
                if (conflictCount > 0) {
                    AppMenuItem(
                        Lucide.GitMerge,
                        "Review conflicts",
                        "$conflictCount to review",
                        hintIsAlert = true,
                    ) { open(AppRoute.CONFLICT_INBOX) }
                } else {
                    null
                }
            }
        },
        MenuEntry(40) { ctx ->
            with(ctx) {
                AppMenuItem(Lucide.CloudSync, "Sync Center", "Sync, bank links and fixes") { open(AppRoute.SYNC_CENTER) }
            }
        },
        MenuEntry(
            240,
        ) { ctx -> with(ctx) { AppMenuItem(Lucide.History, "Change history", "Undo any edit") { open(AppRoute.CHANGE_HISTORY) } } },
    )
