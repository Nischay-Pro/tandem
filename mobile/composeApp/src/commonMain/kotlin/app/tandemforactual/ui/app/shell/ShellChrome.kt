@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import app.tandemforactual.ui.app.AppHeader
import app.tandemforactual.ui.app.isTab
import app.tandemforactual.ui.core.session.budget.budgetMutationsWaitingForConnection
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.headerSyncCaption
import app.tandemforactual.ui.core.state.resolveMainCloudStatus
import app.tandemforactual.ui.feature.sync.conflictPayeeTitle
import kotlinx.coroutines.delay

/** Window and sync status values the shell chrome shares within one composition. */
internal class ShellChromeState(
    val ctx: ShellContext,
    val windowSize: IntSize,
    val isLandscape: Boolean,
    val useRail: Boolean,
    val mainCloudStatus: MainCloudStatus,
    private val headerClockMs: State<Long>,
) {
    fun syncCaption(): String? =
        headerSyncCaption(
            mainCloudStatus,
            ctx.mobileSession.pendingWrites.map { it.status },
            ctx.mobileSession.localSyncState?.lastSyncedAtEpochMs,
            headerClockMs.value,
        )
}

@Composable
internal fun rememberHeaderClock(ctx: ShellContext): State<Long> {
    val headerClockMs = remember { mutableStateOf(ctx.env.nowEpochMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            headerClockMs.value = ctx.env.nowEpochMillis()
        }
    }
    LaunchedEffect(ctx.mobileSession.localSyncState?.lastSyncedAtEpochMs) { headerClockMs.value = ctx.env.nowEpochMillis() }
    return headerClockMs
}

internal fun mainCloudStatus(ctx: ShellContext): MainCloudStatus {
    val mobileSession = ctx.mobileSession
    val state = ctx.state
    return resolveMainCloudStatus(
        syncEnabled =
            state.selectedBudgetId.isNotBlank() &&
                (mobileSession.isAuthenticated || mobileSession.isRestoringStoredSession),
        isSyncing = mobileSession.isSyncing,
        hasSyncError = mobileSession.syncError != null,
        persistedSyncStatus = mobileSession.localSyncState?.status,
        pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
        hasSuccessfulSync = mobileSession.localSyncState?.lastSyncedAtEpochMs != null,
        hasPendingBudgetChanges = mobileSession.budgetQueue.pendingBudgetMutationCount(state.selectedBudgetId) > 0,
        budgetChangesWaitingForConnection = mobileSession.budgetQueue.budgetMutationsWaitingForConnection(state.selectedBudgetId),
    )
}

/** The app header for [headerRoute], with the sync status, budget switcher and landscape add action. */
@Composable
internal fun ShellHeader(
    chrome: ShellChromeState,
    headerRoute: AppRoute,
) {
    if (headerRoute == AppRoute.WELCOME || headerRoute == AppRoute.STARTUP) return
    val ctx = chrome.ctx
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val navigation = ctx.navigation
    val conflictOperation =
        if (headerRoute == AppRoute.CONFLICT_REVIEW) {
            mobileSession.pendingWrites
                .firstOrNull { it.operationId == state.selectedConflictOperationId && it.status == "conflict" }
        } else {
            null
        }
    val conflictTitle = conflictPayeeTitle(conflictOperation, ctx.shell.liveTransactions, mobileSession)
    AppHeader(
        route = headerRoute,
        state = state,
        showBack = !headerRoute.isTab(),
        onBack = ctx.navigator::handleCurrentRouteBack,
        onSync = { navigation.open(AppRoute.SYNC_CENTER) },
        syncStatus = chrome.mainCloudStatus,
        pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
        onBudget = { navigation.open(AppRoute.SWITCH_BUDGET) },
        onOpenRoute = navigation.open,
        actualConnected = mobileSession.isServerReachable,
        liveAccounts = ctx.shell.liveAccounts,
        liveBudgetMonth = ctx.shell.liveBudgetMonth,
        activitySelectionTotalMinor = ctx.budgetScoped.activitySelectionTotalMinor,
        conflictTitle = conflictTitle,
        compactLandscape = chrome.isLandscape,
        onAddTransaction =
            if (chrome.isLandscape &&
                !ctx.transactionSheetActive &&
                headerRoute == AppRoute.BUDGET &&
                shouldShowBudgetCreateAction(state.selectedBudgetId.isNotBlank(), ctx.budgetScoped.budgetGroupReorderActive)
            ) {
                { navigation.newTransaction(null) }
            } else {
                null
            },
        addTransactionEnabled = !state.readOnlyMode,
        syncCaption = chrome.syncCaption(),
        onRefreshBudgets = ctx.signInActions::refreshBudgets.takeIf { mobileSession.isAuthenticated && mobileSession.isServerReachable },
        refreshingBudgets = mobileSession.isLoadingBudgets,
        onOpenMenu = if (!chrome.useRail && state.selectedBudgetId.isNotBlank()) ({ state.menuOpen = true }) else null,
    )
}
