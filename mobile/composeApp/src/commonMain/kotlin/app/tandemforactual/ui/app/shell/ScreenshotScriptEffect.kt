@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import app.tandemforactual.ui.app.ScreenshotScript
import app.tandemforactual.ui.core.session.loadBudgets
import app.tandemforactual.ui.core.session.signInWithPassword
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.feature.transactions.openTransaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Signs in, opens the scripted budget and walks to the screen a store screenshot run asks for. */
@Composable
internal fun ScreenshotScriptEffect(
    ctx: ShellContext,
    screenshotScript: ScreenshotScript,
) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    LaunchedEffect(screenshotScript, state.settingsStorageReady) {
        if (!state.settingsStorageReady) return@LaunchedEffect
        state.hasCompletedNotificationOnboarding = true
        state.hasCompletedSyncOnboarding = true
        state.hasDismissedBudgetHint = true
        if (!mobileSession.isAuthenticated) {
            state.serverUrl = screenshotScript.serverUrl
            if (!mobileSession.signInWithPassword(screenshotScript.serverUrl, screenshotScript.password, ctx.env.deviceName)) {
                return@LaunchedEffect
            }
        } else if (mobileSession.budgets.isEmpty()) {
            mobileSession.loadBudgets()
        }
        val budget = mobileSession.budgets.firstOrNull { it.name == screenshotScript.budgetName } ?: return@LaunchedEffect
        state.selectedBudget = budget.name
        state.selectedBudgetId = budget.budgetId
        state.selectedBudgetEncrypted = budget.isEncrypted
        mobileSession.synchronize(budget.budgetId, null)
        delay(TandemMotion.ROUTE_MILLIS * 2L)
        snapshotFlow { ctx.shell.routeTransitionInputBlocked }.first { !it }
        if (ctx.backStack.lastOrNull()?.route != AppRoute.BUDGET) ctx.navigation.reset(AppRoute.BUDGET)
        delay(SCREENSHOT_SETTLE_MILLIS)
        showScriptedScreen(ctx, screenshotScript)
    }
}

private suspend fun showScriptedScreen(
    ctx: ShellContext,
    screenshotScript: ScreenshotScript,
) {
    val state = ctx.state
    val navigation = ctx.navigation
    val select = screenshotScript.select
    when (screenshotScript.screen) {
        "budget" -> state.budgetScrollToSummaryRequested = true
        "activity" -> {
            navigation.selectTab(AppRoute.ACTIVITY)
            delay(SCREENSHOT_SETTLE_MILLIS)
            ctx.shell.liveActivityTransactions.firstOrNull { it.payee == select }?.let {
                openTransaction(state, navigation, it, isLive = true, navigate = false)
            }
        }
        "accounts" -> {
            ctx.shell.liveAccounts
                .firstOrNull { it.name == select }
                ?.let { state.selectedAccountId = it.id }
            navigation.selectTab(AppRoute.ACCOUNTS)
        }
        "reports" -> navigation.selectTab(AppRoute.REPORTS)
        "category" -> {
            ctx.mobileSession.categoryRecords.firstOrNull { it.recordName() == select }?.let {
                state.selectedCategoryId = it.recordId
                navigation.open(AppRoute.CATEGORY_DETAIL)
            }
        }
        "add" -> navigation.newTransaction(null)
    }
}

private const val SCREENSHOT_SETTLE_MILLIS = 1_500L
