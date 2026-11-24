package app.tandemforactual.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.ui.app.routes.aboutScreens
import app.tandemforactual.ui.app.routes.accountsScreens
import app.tandemforactual.ui.app.routes.activityScreens
import app.tandemforactual.ui.app.routes.bankingScreens
import app.tandemforactual.ui.app.routes.budgetScreens
import app.tandemforactual.ui.app.routes.checkupScreens
import app.tandemforactual.ui.app.routes.currencyScreens
import app.tandemforactual.ui.app.routes.exportScreens
import app.tandemforactual.ui.app.routes.homeScreens
import app.tandemforactual.ui.app.routes.householdScreens
import app.tandemforactual.ui.app.routes.insightsScreens
import app.tandemforactual.ui.app.routes.onboardingScreens
import app.tandemforactual.ui.app.routes.planningScreens
import app.tandemforactual.ui.app.routes.reimbursementsScreens
import app.tandemforactual.ui.app.routes.reportsScreens
import app.tandemforactual.ui.app.routes.residenceScreens
import app.tandemforactual.ui.app.routes.retirementScreens
import app.tandemforactual.ui.app.routes.reviewScreens
import app.tandemforactual.ui.app.routes.rulesScreens
import app.tandemforactual.ui.app.routes.schedulesScreens
import app.tandemforactual.ui.app.routes.selectorScreens
import app.tandemforactual.ui.app.routes.settingsScreens
import app.tandemforactual.ui.app.routes.sharedBillsScreens
import app.tandemforactual.ui.app.routes.syncScreens
import app.tandemforactual.ui.app.routes.transactionsScreens
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.reports.ReportsDataCache
import kotlinx.coroutines.CoroutineScope

/** Everything the shell hands to a screen; route files read what they need from it. */
internal class ScreenContext(
    val route: AppRoute,
    val isForegroundRoute: Boolean,
    val state: AppState,
    val deviceName: String,
    val navigation: AppNavigation,
    val mobileSession: TandemMobileSession,
    val reportsDataCache: ReportsDataCache,
    val onSignIn: () -> Unit,
    val onStartOpenIdSignIn: () -> Unit,
    val onRefreshBudgets: () -> Unit,
    val onPrepareBudgetUnlock: (BudgetCatalogItem) -> Unit,
    val onOpenSelectedBudget: (BudgetCatalogItem) -> Unit,
    val onRetrySync: () -> Unit,
    val backgroundSyncSupported: Boolean,
    val merchantIconMatcher: MerchantIconMatcherPack?,
    val merchantIconMatcherLoadError: String?,
    val merchantIconCatalog: MerchantIconCatalog?,
    val merchantIconCatalogLoadError: String?,
    val liveTransactions: List<TransactionItem>,
    val liveActivityTransactions: List<TransactionItem>,
    val onActivitySelectionTotalChanged: (Long?) -> Unit,
    val activitySortCache: ActivityTransactionSortCache,
    val liveAccounts: List<LiveAccountSummary>,
    val liveSchedules: List<LiveScheduleSummary>,
    val liveBudgetMonth: LiveBudgetMonthSummary?,
    val onSyncNow: () -> Unit,
    val onBudgetGroupReorderModeChange: (Boolean) -> Unit,
    val onSaveTransaction: (String?, (Boolean) -> Unit) -> Unit,
    val onDeleteTransaction: ((Boolean) -> Unit) -> Unit,
    val onCreateTransaction: (String?) -> Unit,
    val onResolveConflict: (String, Boolean, String) -> Unit,
    val onDiscardWrite: (String) -> Unit,
    val onRetryUncertainWrite: (String) -> Unit,
    val onCheckUncertainWriteStatus: (String) -> Unit,
    val onForgetDevice: () -> Unit,
    val onForgetDeviceLocally: () -> Unit,
    val onRequestNotificationPermission: (() -> Unit)?,
    val onOpenNotificationSettings: (() -> Unit)?,
    val notificationPermissionEnabled: Boolean?,
    val onToggleNotificationPermission: ((Boolean) -> Unit)?,
    val scope: CoroutineScope,
    val uriHandler: UriHandler,
)

/** Every route's screen, gathered from the per-feature route files in `app/routes`. */
internal val screenRoutes: List<Map<AppRoute, @Composable (ScreenContext) -> Unit>> =
    listOf(
        aboutScreens,
        accountsScreens,
        activityScreens,
        bankingScreens,
        budgetScreens,
        checkupScreens,
        currencyScreens,
        exportScreens,
        homeScreens,
        householdScreens,
        insightsScreens,
        onboardingScreens,
        planningScreens,
        reimbursementsScreens,
        reviewScreens,
        sharedBillsScreens,
        reportsScreens,
        retirementScreens,
        residenceScreens,
        rulesScreens,
        schedulesScreens,
        selectorScreens,
        settingsScreens,
        syncScreens,
        transactionsScreens,
    )

internal val screenRegistry: Map<AppRoute, @Composable (ScreenContext) -> Unit> = screenRoutes.fold(emptyMap()) { all, next -> all + next }
