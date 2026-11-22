@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.app.ScreenContent
import app.tandemforactual.ui.core.session.forgetDevice
import app.tandemforactual.ui.core.session.forgetDeviceLocally
import app.tandemforactual.ui.core.state.AppRouteEntry
import kotlinx.coroutines.launch

/** The screen registered for [visibleEntry], wired to the shell's live data and actions. */
@Composable
internal fun ShellScreen(
    ctx: ShellContext,
    visibleEntry: AppRouteEntry,
) {
    val env = ctx.env
    val shell = ctx.shell
    val signIn = ctx.signInActions
    val budget = ctx.budgetActions
    val mobileSession = ctx.mobileSession
    ScreenContent(
        route = visibleEntry.route,
        isForegroundRoute = visibleEntry == ctx.routeEntry,
        state = ctx.state,
        deviceName = env.deviceName,
        navigation = ctx.navigation,
        mobileSession = mobileSession,
        reportsDataCache = shell.reportsDataCache,
        onSignIn = signIn::signIn,
        onStartOpenIdSignIn = signIn::startOpenIdSignIn,
        onRefreshBudgets = signIn::refreshBudgets,
        onPrepareBudgetUnlock = signIn::prepareBudgetUnlock,
        onOpenSelectedBudget = signIn::openSelectedBudget,
        onRetrySync = { shell.syncRetryCounter += 1 },
        backgroundSyncSupported = env.onBackgroundSyncSettingsChanged != null,
        merchantIconMatcher = shell.merchantIconMatcher,
        merchantIconMatcherLoadError = shell.merchantIconMatcherLoadError,
        merchantIconCatalog = shell.merchantIconCatalog,
        merchantIconCatalogLoadError = shell.merchantIconCatalogLoadError,
        liveTransactions = shell.liveTransactions,
        liveActivityTransactions = shell.liveActivityTransactions,
        onActivitySelectionTotalChanged = { ctx.budgetScoped.activitySelectionTotalMinor = it },
        activitySortCache = shell.activitySortCache,
        liveAccounts = shell.liveAccounts,
        liveSchedules = shell.liveSchedules,
        liveBudgetMonth = shell.liveBudgetMonth,
        onSyncNow = budget::syncNow,
        onBudgetGroupReorderModeChange = { ctx.budgetScoped.budgetGroupReorderActive = it },
        onSaveTransaction = budget::saveTransaction,
        onDeleteTransaction = budget::deleteTransaction,
        onCreateTransaction = budget::createTransaction,
        onResolveConflict = budget::resolveConflict,
        onDiscardWrite = budget::discardWrite,
        onRetryUncertainWrite = budget::retryUncertainWrite,
        onCheckUncertainWriteStatus = budget::checkUncertainWriteStatus,
        onForgetDevice = { ctx.coroutineScope.launch { mobileSession.forgetDevice() } },
        onForgetDeviceLocally = { ctx.coroutineScope.launch { mobileSession.forgetDeviceLocally() } },
        onRequestNotificationPermission = env.onRequestNotificationPermission,
        onOpenNotificationSettings = env.onOpenNotificationSettings,
        notificationPermissionEnabled = env.notificationPermissionEnabled,
        onToggleNotificationPermission = env.onToggleNotificationPermission,
    )
}
