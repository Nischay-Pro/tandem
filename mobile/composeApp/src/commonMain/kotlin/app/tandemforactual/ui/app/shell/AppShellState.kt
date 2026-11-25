package app.tandemforactual.ui.app.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.appRouteStackSaver
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.auth.AppLockState
import app.tandemforactual.ui.feature.reports.ReportsDataCache
import kotlinx.coroutines.Job

/** Back stack, route transition and back gesture flags, and the live data the app shell derives. */
@Stable
internal class AppShellState(
    val backStack: SnapshotStateList<AppRouteEntry>,
    nextRouteEntryIdState: MutableState<Long>,
    savedTabBackStacksState: MutableState<String>,
    showDiscardTransactionDialogState: MutableState<Boolean>,
    nowEpochMillis: () -> Long,
) {
    var accessibilitySettingsRevision by mutableStateOf(0)
    var nextRouteEntryId by nextRouteEntryIdState
    var savedTabBackStacks by savedTabBackStacksState
    var showDiscardTransactionDialog by showDiscardTransactionDialogState

    var merchantIconMatcher by mutableStateOf<MerchantIconMatcherPack?>(null)
    var merchantIconMatcherLoadError by mutableStateOf<String?>(null)
    var merchantIconCatalog by mutableStateOf<MerchantIconCatalog?>(null)
    var merchantIconCatalogLoadError by mutableStateOf<String?>(null)

    var syncRetryCounter by mutableStateOf(0)
    var lastAutomaticSyncAtEpochMillis by mutableStateOf<Long?>(null)
    var lastAutomaticSyncBudgetId by mutableStateOf<String?>(null)
    var automaticSyncInFlight by mutableStateOf(false)
    var pendingAutomaticSync by mutableStateOf(false)
    var cacheDiscoveryComplete by mutableStateOf(false)
    var sessionRestoreComplete by mutableStateOf(false)

    var routeTransitionDirection by mutableStateOf(0f)
    var routeTransitionInputBlocked by mutableStateOf(false)
    var routeTransitionInputJob by mutableStateOf<Job?>(null)
    var backGestureProgress by mutableStateOf(0f)
    var backGestureDirection by mutableStateOf(1f)
    var backGestureCommitPending by mutableStateOf(false)
    var backGestureOutgoingEntry by mutableStateOf<AppRouteEntry?>(null)
    var backPreviewRoute by mutableStateOf<AppRouteEntry?>(null)
    var backGestureCancelJob by mutableStateOf<Job?>(null)
    var backButtonTransitionRunning by mutableStateOf(false)
    var queuedBackPresses by mutableStateOf(0)
    var pendingBackAfterRouteTransition by mutableStateOf(false)
    var skipNextRouteTransition by mutableStateOf(false)
    var transactionSheetClosing by mutableStateOf(false)
    var transactionSheetPopCount by mutableStateOf(1)
    var previousRoute by mutableStateOf(backStack.last().route)

    var liveAccounts by mutableStateOf(emptyList<LiveAccountSummary>())
    var liveSchedules by mutableStateOf(emptyList<LiveScheduleSummary>())
    var liveTransactions by mutableStateOf(emptyList<TransactionItem>())
    var liveActivityTransactions by mutableStateOf(emptyList<TransactionItem>())
    var liveUncategorized by mutableStateOf(emptyList<TransactionItem>())
    var liveBudgetMonth by mutableStateOf<LiveBudgetMonthSummary?>(null)

    val appLockState = AppLockState(nowEpochMillis)
    val routeScrollStates = mutableMapOf<String, ScrollState>()
    val routeLazyListStates = mutableMapOf<String, LazyListState>()
    val reportsDataCache = ReportsDataCache()
    val activitySortCache = ActivityTransactionSortCache()
}

/** Shell flags that reset whenever another budget is selected. */
@Stable
internal class BudgetScopedShellState {
    var activitySelectionTotalMinor by mutableStateOf<Long?>(null)
    var budgetGroupReorderActive by mutableStateOf(false)
}

@Composable
internal fun rememberAppShellState(nowEpochMillis: () -> Long): AppShellState {
    val showDiscardTransactionDialog = rememberSaveable { mutableStateOf(false) }
    val nextRouteEntryId = rememberSaveable { mutableStateOf(2L) }
    val backStack = rememberSaveable(saver = appRouteStackSaver) { mutableStateListOf(AppRouteEntry(AppRoute.STARTUP, 1)) }
    val savedTabBackStacks = rememberSaveable { mutableStateOf("") }
    return remember {
        AppShellState(backStack, nextRouteEntryId, savedTabBackStacks, showDiscardTransactionDialog, nowEpochMillis)
    }
}
