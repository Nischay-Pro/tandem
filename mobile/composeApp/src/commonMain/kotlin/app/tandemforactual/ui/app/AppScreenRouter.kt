@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.recentPayeeIds
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.SelectorGroup
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.reports.ReportsDataCache

@Composable
internal fun ScreenContent(
    route: AppRoute,
    isForegroundRoute: Boolean,
    state: AppState,
    deviceName: String,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    reportsDataCache: ReportsDataCache,
    onSignIn: () -> Unit,
    onStartOpenIdSignIn: () -> Unit,
    onRefreshBudgets: () -> Unit,
    onPrepareBudgetUnlock: (BudgetCatalogItem) -> Unit,
    onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
    onRetrySync: () -> Unit,
    backgroundSyncSupported: Boolean,
    merchantIconMatcher: MerchantIconMatcherPack?,
    merchantIconMatcherLoadError: String?,
    merchantIconCatalog: MerchantIconCatalog?,
    merchantIconCatalogLoadError: String?,
    liveTransactions: List<TransactionItem>,
    liveActivityTransactions: List<TransactionItem>,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
    activitySortCache: ActivityTransactionSortCache,
    liveAccounts: List<LiveAccountSummary>,
    liveSchedules: List<LiveScheduleSummary>,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    onSyncNow: () -> Unit,
    onBudgetGroupReorderModeChange: (Boolean) -> Unit,
    onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    onCreateTransaction: (String?) -> Unit,
    onResolveConflict: (String, Boolean, String) -> Unit,
    onDiscardWrite: (String) -> Unit,
    onRetryUncertainWrite: (String) -> Unit,
    onCheckUncertainWriteStatus: (String) -> Unit,
    onForgetDevice: () -> Unit,
    onForgetDeviceLocally: () -> Unit,
    onRequestNotificationPermission: (() -> Unit)?,
    onOpenNotificationSettings: (() -> Unit)?,
    notificationPermissionEnabled: Boolean?,
    onToggleNotificationPermission: ((Boolean) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context =
        ScreenContext(
            route = route,
            isForegroundRoute = isForegroundRoute,
            state = state,
            deviceName = deviceName,
            navigation = navigation,
            mobileSession = mobileSession,
            reportsDataCache = reportsDataCache,
            onSignIn = onSignIn,
            onStartOpenIdSignIn = onStartOpenIdSignIn,
            onRefreshBudgets = onRefreshBudgets,
            onPrepareBudgetUnlock = onPrepareBudgetUnlock,
            onOpenSelectedBudget = onOpenSelectedBudget,
            onRetrySync = onRetrySync,
            backgroundSyncSupported = backgroundSyncSupported,
            merchantIconMatcher = merchantIconMatcher,
            merchantIconMatcherLoadError = merchantIconMatcherLoadError,
            merchantIconCatalog = merchantIconCatalog,
            merchantIconCatalogLoadError = merchantIconCatalogLoadError,
            liveTransactions = liveTransactions,
            liveActivityTransactions = liveActivityTransactions,
            onActivitySelectionTotalChanged = onActivitySelectionTotalChanged,
            activitySortCache = activitySortCache,
            liveAccounts = liveAccounts,
            liveSchedules = liveSchedules,
            liveBudgetMonth = liveBudgetMonth,
            onSyncNow = onSyncNow,
            onBudgetGroupReorderModeChange = onBudgetGroupReorderModeChange,
            onSaveTransaction = onSaveTransaction,
            onDeleteTransaction = onDeleteTransaction,
            onCreateTransaction = onCreateTransaction,
            onResolveConflict = onResolveConflict,
            onDiscardWrite = onDiscardWrite,
            onRetryUncertainWrite = onRetryUncertainWrite,
            onCheckUncertainWriteStatus = onCheckUncertainWriteStatus,
            onForgetDevice = onForgetDevice,
            onForgetDeviceLocally = onForgetDeviceLocally,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
            notificationPermissionEnabled = notificationPermissionEnabled,
            onToggleNotificationPermission = onToggleNotificationPermission,
            scope = scope,
            uriHandler = uriHandler,
        )
    screenRegistry.getValue(route)(context)
}

/** The payees used most recently, in that order, shown above the full list. */
internal fun recentPayeeGroup(
    values: List<SelectorItem>,
    recentPayeeIds: List<String>,
): List<SelectorGroup> {
    val byId = values.associateBy { it.id }
    val recent = recentPayeeIds.mapNotNull { byId[it] }
    return if (recent.isEmpty() || values.size <= 8) emptyList() else listOf(SelectorGroup("Recent", recent))
}
