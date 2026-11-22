@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.configureLocalServer
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.shouldTriggerAutomaticSync
import app.tandemforactual.ui.core.state.shouldTriggerAutomaticSyncForRouteEntry
import kotlinx.coroutines.launch

/** Points the session at the local server and retries queued work and sync on resume and reconnect; returns the automatic sync request. */
@Composable
internal fun rememberAutomaticSync(ctx: ShellContext): State<() -> Unit> {
    val env = ctx.env
    val state = ctx.state
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    val currentNetworkKind by env.currentNetwork.collectAsState()
    LaunchedEffect(state.localServerUrl, mobileSession.authenticatedDevice?.deviceId, currentNetworkKind) {
        mobileSession.configureLocalServer(state.localServerUrl.ifBlank { null })
        if (currentNetworkKind != null && mobileSession.budgetError != null && mobileSession.isAuthenticated) {
            mobileSession.loadBudgets()
        }
    }
    val automaticSyncRequest = rememberUpdatedState(newValue = { requestAutomaticSync(ctx, currentNetworkKind) })
    LaunchedEffect(env.appResumeEvents) {
        env.appResumeEvents.collect {
            shell.accessibilitySettingsRevision += 1
            mobileSession.budgetQueue.retryQueuedBudgetReorders()
            mobileSession.schedules.retryQueuedScheduleActions()
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(env.networkRestoredEvents) {
        env.networkRestoredEvents.collect {
            mobileSession.budgetQueue.retryQueuedBudgetReorders()
            mobileSession.schedules.retryQueuedScheduleActions()
            if (shell.lastAutomaticSyncBudgetId == state.selectedBudgetId) shell.lastAutomaticSyncAtEpochMillis = null
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(mobileSession.isSyncing, shell.pendingAutomaticSync) {
        if (!mobileSession.isSyncing && shell.pendingAutomaticSync) {
            shell.pendingAutomaticSync = false
            automaticSyncRequest.value()
        }
    }
    return automaticSyncRequest
}

private fun requestAutomaticSync(
    ctx: ShellContext,
    currentNetworkKind: NetworkKind?,
) {
    val state = ctx.state
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    val now = ctx.env.nowEpochMillis()
    if (!state.syncNetwork.allows(currentNetworkKind)) {
        Unit
    } else if (mobileSession.isSyncing || shell.automaticSyncInFlight) {
        shell.pendingAutomaticSync = true
    } else if (
        shouldTriggerAutomaticSync(
            isBudgetVisible = ctx.route == AppRoute.BUDGET,
            isAuthenticated = mobileSession.isAuthenticated,
            syncOnOpen = state.syncOnOpen,
            budgetId = state.selectedBudgetId,
            isSyncing = false,
            encryptedBudgetNeedsPassword = state.selectedBudgetEncrypted,
            hasEncryptionPassword = state.encryptedPassword.isNotBlank(),
            lastAutomaticSyncAtEpochMillis =
                shell.lastAutomaticSyncAtEpochMillis.takeIf {
                    shell.lastAutomaticSyncBudgetId == state.selectedBudgetId
                },
            nowEpochMillis = now,
        )
    ) {
        val budgetId = state.selectedBudgetId
        val encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() }
        shell.automaticSyncInFlight = true
        shell.lastAutomaticSyncAtEpochMillis = now
        shell.lastAutomaticSyncBudgetId = budgetId
        ctx.coroutineScope.launch {
            try {
                mobileSession.synchronize(budgetId, encryptionPassword)
            } finally {
                shell.automaticSyncInFlight = false
            }
        }
    }
}

/** Requests an automatic sync when a route that should refresh the budget comes into view. */
@Composable
internal fun RouteSyncEffect(
    ctx: ShellContext,
    automaticSyncRequest: State<() -> Unit>,
) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val route = ctx.route
    LaunchedEffect(
        route,
        state.selectedBudgetId,
        state.syncOnOpen,
        mobileSession.isAuthenticated,
        state.selectedBudgetEncrypted,
        state.encryptedPassword,
    ) {
        if (
            shouldTriggerAutomaticSyncForRouteEntry(ctx.shell.previousRoute, route) &&
            state.syncOnOpen &&
            mobileSession.isAuthenticated &&
            state.selectedBudgetId.isNotBlank() &&
            (!state.selectedBudgetEncrypted || state.encryptedPassword.isNotBlank())
        ) {
            automaticSyncRequest.value()
        }
        ctx.shell.previousRoute = route
    }
}
