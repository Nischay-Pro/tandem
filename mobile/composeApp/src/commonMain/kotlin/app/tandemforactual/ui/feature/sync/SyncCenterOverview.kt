@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.navigation.openNeedsReview
import app.tandemforactual.ui.core.session.SyncRecovery
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.syncRecovery
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.budgetDisplayNameForHeader

/** Overview card wired to the session: recovery, sync, pending changes and the first item needing review. */
@Composable
internal fun SyncCenterOverview(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    work: SyncCenterWork,
    onSyncNow: () -> Unit,
    onDownloadAgain: () -> Unit,
) {
    SyncOverviewCard(
        budgetName = budgetDisplayNameForHeader(state.selectedBudget),
        attentionCount = work.attentionCount,
        waitingCount = work.waitingCount,
        isSyncing = mobileSession.isSyncing,
        isServerReachable = mobileSession.isServerReachable,
        syncError = mobileSession.syncError,
        syncRecovery = mobileSession.syncRecovery,
        onRecover = {
            when (mobileSession.syncRecovery) {
                SyncRecovery.SIGN_IN -> navigation.open(AppRoute.SIGN_IN)
                SyncRecovery.CHECK_SERVER -> navigation.open(AppRoute.SERVER_ADDRESS)
                SyncRecovery.CHOOSE_BUDGET -> navigation.open(AppRoute.SWITCH_BUDGET)
                SyncRecovery.RETRY -> onSyncNow()
                SyncRecovery.DOWNLOAD_AGAIN -> onDownloadAgain()
            }
        },
        lastSyncedAtEpochMs = mobileSession.localSyncState?.lastSyncedAtEpochMs,
        nowEpochMilliseconds = mobileSession.nowEpochMillis(),
        onSyncNow = onSyncNow.takeIf { !mobileSession.isSyncing },
        onPendingClick = { navigation.open(AppRoute.PENDING_CHANGES) },
        onReviewClick = {
            if (!openNeedsReview(
                    state,
                    navigation,
                    work.transactionsNeedingReview.firstOrNull(),
                    work.schedulesNeedingReview.firstOrNull(),
                    work.budgetMutationReviews.firstOrNull(),
                )
            ) {
                navigation.open(AppRoute.CONFLICT_INBOX)
            }
        },
    )
}
