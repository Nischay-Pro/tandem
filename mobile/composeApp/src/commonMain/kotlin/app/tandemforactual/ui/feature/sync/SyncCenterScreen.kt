@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.session.BudgetSyncOverview
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetSyncOverview
import app.tandemforactual.ui.core.session.downloadBudgetAgain
import app.tandemforactual.ui.core.session.retryFailedWork
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.PlanningSyncStatus
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.SyncNoticeCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.launch

@Composable
internal fun SyncCenterScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    liveSchedules: List<LiveScheduleSummary>,
    onSyncNow: () -> Unit,
) {
    if (state.selectedBudgetId.isBlank()) {
        PageColumn {
            EmptyStateCard(
                "Choose a budget",
                "Open an Actual budget to see changes waiting to sync.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
            )
        }
        return
    }
    val work = syncCenterWork(state, mobileSession)
    val attentionCount = work.attentionCount
    val scope = rememberCoroutineScope()
    val vault = LocalEncryptedBudgetVault.current
    val retryable =
        work.transactionsNeedingReview.count { it.status == "uncertain" } + mobileSession.banking.bankLinks.count { it.failure != null } +
            (if (mobileSession.planning.syncStatus == PlanningSyncStatus.WAITING) 1 else 0)
    val conflictCount = work.transactionsNeedingReview.count { it.status == "conflict" }
    var retrying by remember { mutableStateOf(false) }
    val budgetOverview by rememberBudgetSyncOverview(state, mobileSession)
    SyncCenterLinkedAccountsEffect(state, mobileSession)
    PageLazyColumn {
        serverUpdateNoticeItems(mobileSession)
        item(key = "sync-center-overview") {
            SyncCenterOverview(state, mobileSession, navigation, work, onSyncNow) {
                scope.launch {
                    mobileSession.downloadBudgetAgain(state.selectedBudgetId, vault)
                    navigation.reset(AppRoute.SWITCH_BUDGET)
                }
            }
        }
        if (attentionCount > 0) {
            item(key = "sync-center-review-section") {
                SyncNeedsReviewSection(
                    work,
                    attentionCount,
                    state,
                    mobileSession,
                    navigation,
                    liveTransactions,
                    liveBudgetMonth,
                    liveSchedules,
                )
            }
        }
        if (retryable > 0 || conflictCount > 0) {
            item(key = "sync-center-actions") {
                SyncCenterActions(
                    retryableCount = retryable,
                    conflictCount = conflictCount,
                    busy = retrying || mobileSession.isSyncing,
                    onRetry = {
                        retrying = true
                        scope.launch { retryFailedSyncWork(state, mobileSession) { retrying = false } }
                    },
                    onResolve = { navigation.open(AppRoute.CONFLICT_INBOX) },
                )
            }
        }
        syncCenterDetailItems(budgetOverview, mobileSession, navigation, state)
    }
}

/** Budgets, bank links, services, device history and the bottom spacer below the sync actions. */
private fun LazyListScope.syncCenterDetailItems(
    budgetOverview: List<BudgetSyncOverview>,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    state: AppState,
) {
    if (budgetOverview.size > 1) {
        item(key = "sync-center-budgets") {
            SyncCenterBudgets(budgetOverview, mobileSession) { navigation.open(AppRoute.SWITCH_BUDGET) }
        }
    }
    if (mobileSession.banking.bankLinks.isNotEmpty()) {
        item(key = "sync-center-bank-links") {
            SyncCenterBankLinks(mobileSession) { navigation.open(AppRoute.BANK_CONNECTIONS) }
        }
    }
    item(key = "sync-center-services") {
        SyncCenterServices(mobileSession) { navigation.open(AppRoute.SETTINGS) }
    }
    item(key = "sync-center-history") { DeviceWriteHistory(state, mobileSession) }
    item(key = "sync-center-bottom-spacer") { Spacer(Modifier.height(72.dp)) }
}

/** Loads linked bank accounts for the budget once signed in. */
@Composable
private fun SyncCenterLinkedAccountsEffect(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    LaunchedEffect(state.selectedBudgetId, mobileSession.isAuthenticated) {
        if (mobileSession.isAuthenticated) {
            runCatching {
                mobileSession.banking.loadLinkedAccounts(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
}

/** Notice asking to update the app or the server when the server's capabilities require it. */
private fun LazyListScope.serverUpdateNoticeItems(mobileSession: TandemMobileSession) {
    val capabilities = mobileSession.serverCapabilities
    if (capabilities?.clientUpdateRequired == true) {
        item(key = "sync-center-app-update") {
            SyncNoticeCard(
                Lucide.TriangleAlert,
                tandemPalette.warning,
                "Update the Tandem app",
                "Your Tandem server needs a newer version of this app to sync every change.",
                onClick = null,
            )
        }
    } else if (capabilities?.isLegacy == true) {
        item(key = "sync-center-server-update") {
            SyncNoticeCard(
                Lucide.TriangleAlert,
                tandemPalette.warning,
                "Update your Tandem server",
                "Some changes wait on this device until your server is updated. They sync automatically after that.",
                onClick = null,
            )
        }
    }
}

@Composable
private fun rememberBudgetSyncOverview(
    state: AppState,
    mobileSession: TandemMobileSession,
): State<List<BudgetSyncOverview>> =
    produceState(
        emptyList<BudgetSyncOverview>(),
        mobileSession.budgets,
        mobileSession.pendingWrites,
        mobileSession.localSyncState,
        state.selectedBudgetId,
    ) { value = mobileSession.budgetSyncOverview(state.selectedBudgetId) }

private suspend fun retryFailedSyncWork(
    state: AppState,
    mobileSession: TandemMobileSession,
    onFinished: () -> Unit,
) {
    try {
        mobileSession.retryFailedWork(state.selectedBudgetId, state.encryptedPassword.takeIf { it.isNotBlank() })
    } finally {
        onFinished()
    }
}
