@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.tandemforactual.ui.app.AppHeader
import app.tandemforactual.ui.core.security.hidesContentInAppSwitcher
import app.tandemforactual.ui.core.session.budget.budgetMutationsWaitingForConnection
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.MainCloudStatus
import app.tandemforactual.ui.core.state.headerSyncCaption
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.core.state.resolveMainCloudStatus
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemSystemBars
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.WindowSizeInfo
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.isDarkTheme
import app.tandemforactual.ui.feature.sync.conflictPayeeTitle
import kotlinx.coroutines.delay

/** Window and sync status values the shell chrome shares within one composition. */
internal class ShellChromeState(
    val ctx: ShellContext,
    val window: WindowSizeInfo,
    val mainCloudStatus: MainCloudStatus,
    private val headerClockMs: State<Long>,
) {
    val useRail get() = window.usesRail

    fun syncCaption(): String? =
        headerSyncCaption(
            mainCloudStatus,
            ctx.mobileSession.pendingWrites.map { it.status },
            ctx.mobileSession.localSyncState?.lastSyncedAtEpochMs,
            headerClockMs.value,
        )
}

/** Everything inside the theme: system bars, back handling, the scaffold and the overlays above it. */
@Composable
internal fun ShellFrame(
    ctx: ShellContext,
    isAppInForeground: Boolean,
) {
    val state = ctx.state
    val window = currentWindowSizeInfo()
    val isDark = isDarkTheme(state.theme, isSystemInDarkTheme())
    val hideInAppSwitcher = hidesContentInAppSwitcher(state.biometricLockEnabled, state.privacyModeEnabled, state.selectedBudgetEncrypted)
    SideEffect {
        ctx.env.onSystemAppearanceChanged?.invoke(isDark, state.theme == ThemeMode.SYSTEM)
        ctx.env.onAppSwitcherPrivacyChanged?.invoke(hideInAppSwitcher)
    }
    TandemSystemBars(isDark)
    TandemBackHandler(
        enabled = ctx.backStack.size > 1 && !ctx.transactionSheetActive,
        onBack = ctx.navigator::handleCurrentRouteBack,
        onBackProgress = ctx.navigator::updateBackGesture,
    )
    val headerClockMs = rememberHeaderClock(ctx, isAppInForeground)
    val chrome = ShellChromeState(ctx, window, mainCloudStatus(ctx), headerClockMs)
    ShellScaffold(chrome)
    ShellOverlays(chrome, isAppInForeground)
}

@Composable
internal fun rememberHeaderClock(
    ctx: ShellContext,
    isAppInForeground: Boolean,
): State<Long> {
    val headerClockMs = remember { mutableStateOf(ctx.env.nowEpochMillis()) }
    val lastSyncedAtEpochMs = ctx.mobileSession.localSyncState?.lastSyncedAtEpochMs
    LaunchedEffect(isAppInForeground, lastSyncedAtEpochMs) {
        if (!isAppInForeground) return@LaunchedEffect
        headerClockMs.value = ctx.env.nowEpochMillis()
        while (true) {
            delay(headerClockDelayMs(ctx.env.nowEpochMillis(), lastSyncedAtEpochMs))
            headerClockMs.value = ctx.env.nowEpochMillis()
        }
    }
    return headerClockMs
}

internal fun headerClockDelayMs(
    nowEpochMs: Long,
    lastSyncedAtEpochMs: Long?,
): Long {
    val sinceSync = lastSyncedAtEpochMs?.let { nowEpochMs - it }
    val phase =
        if (sinceSync != null && sinceSync in 0L until 3_600_000L) {
            sinceSync % 60_000L
        } else {
            nowEpochMs.mod(60_000L)
        }
    return (60_000L - phase).coerceAtLeast(1_000L)
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

/** The app header for [headerRoute], with the sync status, budget switcher and short-window add action. */
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
        compactHeight = chrome.window.compactHeight,
        onAddTransaction =
            if (chrome.window.compactHeight &&
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
