@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.ui.app.BUDGET_PERIODIC_SYNC_INTERVAL_MS
import app.tandemforactual.ui.core.session.hasLoadedBudget
import app.tandemforactual.ui.core.session.refreshServerCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Refreshes server capabilities and budget flags, and times the interface mode and budget switch covers. */
@Composable
internal fun ForegroundEffects(
    ctx: ShellContext,
    isAppInForeground: Boolean,
) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    LaunchedEffect(mobileSession.authenticatedDevice?.deviceId, isAppInForeground) {
        if (mobileSession.authenticatedDevice != null && isAppInForeground) mobileSession.refreshServerCapabilities(force = true)
    }
    LaunchedEffect(mobileSession.budgets, state.selectedBudgetId) {
        mobileSession.budgets.firstOrNull { it.budgetId == state.selectedBudgetId }?.let {
            if (state.selectedBudgetEncrypted != it.isEncrypted) state.selectedBudgetEncrypted = it.isEncrypted
        }
    }
    LaunchedEffect(state.modeSwitchTarget) {
        val target = state.modeSwitchTarget ?: return@LaunchedEffect
        delay(MODE_SWITCH_COVER_MS)
        state.interfaceMode = target
        delay(BUDGET_SWITCH_SPLASH_MIN_MS)
        state.modeSwitchTarget = null
    }
    LaunchedEffect(state.budgetSwitchSplashId) {
        val budgetId = state.budgetSwitchSplashId ?: return@LaunchedEffect
        val shown = TimeSource.Monotonic.markNow()
        withTimeoutOrNull(BUDGET_SWITCH_SPLASH_MAX_MS) { snapshotFlow { mobileSession.hasLoadedBudget(budgetId) }.first { it } }
        delay(BUDGET_SWITCH_SETTLE_MS)
        delay((BUDGET_SWITCH_SPLASH_MIN_MS - shown.elapsedNow().inWholeMilliseconds).coerceAtLeast(0))
        if (state.budgetSwitchSplashId == budgetId) state.budgetSwitchSplashId = null
    }
}

/** Tells the app lock about foreground changes and pulls remote changes while the budget is open. */
@Composable
internal fun PeriodicSyncEffects(
    ctx: ShellContext,
    isAppInForeground: Boolean,
) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    LaunchedEffect(isAppInForeground) { ctx.shell.appLockState.onForegroundChanged(isAppInForeground) }
    LaunchedEffect(
        isAppInForeground,
        state.syncOnOpen,
        mobileSession.isAuthenticated,
        state.selectedBudgetId,
        state.selectedBudgetEncrypted,
        state.encryptedPassword,
    ) {
        if (
            !isAppInForeground ||
            !state.syncOnOpen ||
            !mobileSession.isAuthenticated ||
            state.selectedBudgetId.isBlank() ||
            (state.selectedBudgetEncrypted && state.encryptedPassword.isBlank())
        ) {
            return@LaunchedEffect
        }
        // Pick up changes from Actual and other devices while the budget is open.
        while (true) {
            delay(BUDGET_PERIODIC_SYNC_INTERVAL_MS)
            if (!mobileSession.isSyncing &&
                !mobileSession.isPerformingBudgetAction &&
                state.syncNetwork.allows(ctx.env.currentNetwork.value)
            ) {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    background = true,
                )
            }
        }
    }
}

private const val BUDGET_SWITCH_SPLASH_MIN_MS = 700L
private const val BUDGET_SWITCH_SPLASH_MAX_MS = 5_000L
private const val BUDGET_SWITCH_SETTLE_MS = 250L
private const val MODE_SWITCH_COVER_MS = 220L
