@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.LiveBudgetMonthSummary
import app.tandemforactual.ui.model.LiveScheduleSummary
import app.tandemforactual.ui.model.liveAccountSummaries
import app.tandemforactual.ui.model.liveBudgetMonthSummary
import app.tandemforactual.ui.model.liveScheduleSummaries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TandemApp(
    startInPreview: Boolean = false,
    deviceName: String = "This device",
    database: TandemDatabase? = null,
    deviceCredentialStore: DeviceCredentialStore? = null,
    nowEpochMillis: () -> Long = { 0L },
    allowInsecurePrivateNetwork: Boolean = false,
    onBackgroundSyncSettingsChanged: ((enabled: Boolean, unmeteredOnly: Boolean, budgetId: String) -> Unit)? = null,
    onSystemAppearanceChanged: ((isDark: Boolean, followsSystem: Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    notificationPermissionEnabled: Boolean? = null,
    onToggleNotificationPermission: ((Boolean) -> Unit)? = null,
    onStartupComplete: (() -> Unit)? = null,
    appResumeEvents: Flow<Unit> = emptyFlow(),
    networkRestoredEvents: Flow<Unit> = emptyFlow(),
) {
    var accessibilitySettingsRevision by remember { mutableStateOf(0) }
    val systemReduceMotion = key(accessibilitySettingsRevision) { SystemReduceMotionEnabled() }
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    var activitySelectionTotalMinor by remember(state.selectedBudgetId) { mutableStateOf<Long?>(null) }
    val routeScrollStates = remember { mutableMapOf<String, ScrollState>() }
    val routeLazyListStates = remember { mutableMapOf<String, LazyListState>() }
    val reportsDataCache = remember { ReportsDataCache() }
    val activitySortCache = remember { ActivityTransactionSortCache() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val apiClient = remember(allowInsecurePrivateNetwork) { TandemApiClient(allowInsecurePrivateNetwork) }
    val mobileSession =
        remember(apiClient, database, deviceCredentialStore) {
            TandemMobileSession(apiClient, database, nowEpochMillis, deviceCredentialStore)
        }
    SideEffect { mobileSession.setReadOnlyMode(state.readOnlyMode) }
    val appPreferencesStore = remember(database) { database?.let(::AppPreferencesStore) }
    val coroutineScope = rememberCoroutineScope()
    var merchantIconMatcher by remember { mutableStateOf<MerchantIconMatcherPack?>(null) }
    var merchantIconMatcherLoadError by remember { mutableStateOf<String?>(null) }
    var merchantIconCatalog by remember { mutableStateOf<MerchantIconCatalog?>(null) }
    var merchantIconCatalogLoadError by remember { mutableStateOf<String?>(null) }
    val cachedBudgetCatalog =
        remember(state.cachedBudgetCatalogJson, mobileSession.cachedBudgets) {
            cachedBudgetsWithLocalSnapshots(
                decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                mobileSession.cachedBudgets,
            )
        }
    val availableBudgets =
        budgetChoicesForSelection(mobileSession.budgets, cachedBudgetCatalog, mobileSession.budgetCatalogLoaded)
    val usingCachedBudgetCatalog = !mobileSession.budgetCatalogLoaded && cachedBudgetCatalog.isNotEmpty()
    var syncRetryCounter by remember { mutableStateOf(0) }
    var lastAutomaticSyncAtEpochMillis by remember { mutableStateOf<Long?>(null) }
    var lastAutomaticSyncBudgetId by remember { mutableStateOf<String?>(null) }
    var automaticSyncInFlight by remember { mutableStateOf(false) }
    var pendingAutomaticSync by remember { mutableStateOf(false) }
    var cacheDiscoveryComplete by remember { mutableStateOf(false) }
    var sessionRestoreComplete by remember { mutableStateOf(false) }
    var routeTransitionDirection by remember { mutableStateOf(0f) }
    var backGestureProgress by remember { mutableStateOf(0f) }
    var backGestureDirection by remember { mutableStateOf(1f) }
    var backGestureCommitPending by remember { mutableStateOf(false) }
    var backGestureOutgoingEntry by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backPreviewRoute by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backGestureCancelJob by remember { mutableStateOf<Job?>(null) }
    var backButtonTransitionRunning by remember { mutableStateOf(false) }
    var queuedBackPresses by remember { mutableStateOf(0) }
    var skipNextRouteTransition by remember { mutableStateOf(false) }
    var nextRouteEntryId by rememberSaveable { mutableStateOf(2L) }
    val backStack =
        rememberSaveable(startInPreview, saver = appRouteStackSaver) {
            mutableStateListOf(AppRouteEntry(if (startInPreview) AppRoute.BUDGET else AppRoute.STARTUP, 1))
        }
    val newRouteEntry: (AppRoute) -> AppRouteEntry = { nextRoute ->
        val entryId = nextRouteEntryId
        nextRouteEntryId += 1
        AppRouteEntry(nextRoute, entryId)
    }
    LaunchedEffect(mobileSession) {
        try {
            mobileSession.loadCachedBudgets()
        } finally {
            cacheDiscoveryComplete = true
        }
    }
    LaunchedEffect(mobileSession) {
        try {
            val storedServerUrl = mobileSession.restoreStoredSession()
            if (storedServerUrl != null) state.serverUrl = storedServerUrl
        } finally {
            sessionRestoreComplete = true
        }
    }
    LaunchedEffect(state.selectedBudgetId, state.selectedBudgetEncrypted) {
        if (state.selectedBudgetEncrypted) state.backgroundSyncEnabled = false
    }
    LaunchedEffect(
        cacheDiscoveryComplete,
        mobileSession.cachedBudgets,
        state.settingsStorageReady,
        state.settingsStorageError,
        state.selectedBudgetId,
        state.cachedBudgetCatalogJson,
        sessionRestoreComplete,
        mobileSession.isAuthenticated,
    ) {
        if (backStack.lastOrNull()?.route != AppRoute.STARTUP) return@LaunchedEffect
        val destination =
            resolveAppStartupDestination(
                cacheDiscoveryComplete = cacheDiscoveryComplete,
                hasCachedBudget = mobileSession.cachedBudgets.isNotEmpty(),
                preferencesReady = state.settingsStorageReady,
                preferencesFailed = state.settingsStorageError != null,
                sessionRestoreComplete = sessionRestoreComplete,
                isAuthenticated = mobileSession.isAuthenticated,
            )
        when (destination) {
            AppStartupDestination.WAIT -> Unit
            AppStartupDestination.CACHED_BUDGET -> {
                val localCachedBudgets = mobileSession.cachedBudgets
                val availableCachedBudgets =
                    cachedBudgetsWithLocalSnapshots(
                        decodeCachedBudgetCatalog(state.cachedBudgetCatalogJson),
                        localCachedBudgets,
                    )
                val selectedCachedBudget = availableCachedBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }
                val firstCachedBudget = selectedCachedBudget ?: availableCachedBudgets.firstOrNull()
                if (firstCachedBudget != null) {
                    state.selectedBudget = firstCachedBudget.name
                    state.selectedBudgetId = firstCachedBudget.budgetId
                    state.selectedBudgetEncrypted = firstCachedBudget.isEncrypted
                    backStack.clear()
                    backStack.add(newRouteEntry(AppRoute.BUDGET))
                }
            }
            AppStartupDestination.BUDGET_CHOOSER -> {
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.BUDGET_CHOOSER))
            }
            AppStartupDestination.WELCOME -> {
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.WELCOME))
            }
        }
    }
    LaunchedEffect(appPreferencesStore) {
        val store = appPreferencesStore
        if (store == null) {
            state.settingsStorageReady = true
            return@LaunchedEffect
        }
        val initialSettings = state.persistentSettings()
        val localChanges = mutableMapOf<String, String>()
        val localSettingsObserver =
            launch {
                snapshotFlow { state.persistentSettings() }
                    .collect { currentSettings ->
                        currentSettings
                            .filter { (name, value) -> initialSettings[name] != value }
                            .forEach { (name, value) -> localChanges[name] = value }
                    }
            }
        var storedPreferences: Map<String, String>? = null
        while (storedPreferences == null) {
            val retryAtRead = state.settingsStorageRetry
            try {
                storedPreferences = store.read()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.settingsStorageError = "App settings couldn’t be loaded. Retry to load saved preferences."
                snapshotFlow { state.settingsStorageRetry }.first { it != retryAtRead }
            }
        }
        state
            .persistentSettings()
            .filter { (name, value) -> initialSettings[name] != value }
            .forEach { (name, value) -> localChanges[name] = value }
        localSettingsObserver.cancel()
        state.restorePersistentSettings(storedPreferences + localChanges)
        state.selectedMonthId = currentMonthId()
        state.selectedMonth = currentMonthLabel()
        state.reportsMonthId = state.selectedMonthId
        state.settingsStorageReady = true
        var lastSavedPreferences = if (localChanges.isEmpty()) state.persistentSettings() else emptyMap()
        var lastAttemptedRetry = state.settingsStorageRetry
        snapshotFlow { state.persistentSettings() to state.settingsStorageRetry }
            .collect { (updatedPreferences, retryCount) ->
                if (updatedPreferences == lastSavedPreferences && retryCount == lastAttemptedRetry) return@collect
                val retryRequested = retryCount != lastAttemptedRetry
                lastAttemptedRetry = retryCount
                try {
                    store.save(updatedPreferences)
                    lastSavedPreferences = updatedPreferences
                    state.settingsStorageError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    state.settingsStorageError =
                        if (retryRequested) {
                            "App settings still couldn’t be saved. Check local storage and retry."
                        } else {
                            "App settings couldn’t be saved. Retry to save your changes."
                        }
                }
            }
    }
    LaunchedEffect(
        state.settingsStorageReady,
        state.backgroundSyncEnabled,
        state.syncOnWifi,
        state.selectedBudgetId,
        onBackgroundSyncSettingsChanged,
    ) {
        if (state.settingsStorageReady) {
            onBackgroundSyncSettingsChanged?.invoke(
                state.backgroundSyncEnabled,
                state.syncOnWifi,
                state.selectedBudgetId,
            )
        }
    }
    LaunchedEffect(mobileSession.budgetCatalogVersion) {
        if (mobileSession.budgetCatalogVersion > 0) {
            state.cachedBudgetCatalogJson = encodeCachedBudgetCatalog(mobileSession.budgets)
        }
    }
    var liveAccounts by remember { mutableStateOf(emptyList<LiveAccountSummary>()) }
    var liveSchedules by remember { mutableStateOf(emptyList<LiveScheduleSummary>()) }
    val newTransaction: (String?) -> Unit = { requestedAccountId ->
        if (!state.readOnlyMode && state.selectedBudgetId.isNotBlank()) {
            routeTransitionDirection = 1f
            val eligibleAccounts = liveAccounts.filterNot { it.offBudget }
            val defaultAccount =
                if (state.selectedBudgetId.isNotBlank()) {
                    eligibleAccounts.firstOrNull { it.id == requestedAccountId } ?: eligibleAccounts.firstOrNull()
                } else {
                    null
                }
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = defaultAccount?.name ?: "Checking"
            state.newTransactionAccountId = defaultAccount?.id.orEmpty()
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = platformTodayIsoDate()
            state.transactionType = "Spending"
            state.transactionNotes = ""
            state.selectedTransactionRecordId = ""
            state.selectedTransactionAccountId = ""
            state.selectedTransactionPayeeId = ""
            state.selectedTransactionCategoryId = ""
            state.selectedTransactionDate = ""
            state.selectedTransactionAmountMinor = null
            state.selectedTransactionIsLive = false
            backStack.add(newRouteEntry(AppRoute.NEW_TRANSACTION))
        }
    }
    val popRoute: () -> Unit = {
        if (backStack.size > 1) {
            routeTransitionDirection = -1f
            backStack.removeAt(backStack.lastIndex)
        }
    }
    lateinit var startBackButtonTransition: () -> Unit
    startBackButtonTransition = {
        if (backStack.size > 1) {
            val outgoingEntry = backStack.last()
            val incomingEntry = backStack.getOrNull(backStack.lastIndex - 1)
            if (incomingEntry == null) {
                popRoute()
            } else {
                backGestureProgress = 0f
                backGestureDirection = 1f
                backGestureOutgoingEntry = outgoingEntry
                backPreviewRoute = incomingEntry
                backButtonTransitionRunning = true
                backGestureCancelJob =
                    coroutineScope.launch {
                        val returnAnimation = Animatable(0f)
                        returnAnimation.animateTo(
                            targetValue = 1f,
                            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS),
                        ) {
                            backGestureProgress = value
                        }
                        backButtonTransitionRunning = false
                        skipNextRouteTransition = true
                        popRoute()
                        backGestureCancelJob = null
                        if (queuedBackPresses > 0) {
                            backButtonTransitionRunning = true
                            coroutineScope.launch {
                                withFrameNanos { }
                                backButtonTransitionRunning = false
                                if (queuedBackPresses > 0 && backStack.size > 1) {
                                    queuedBackPresses -= 1
                                    startBackButtonTransition()
                                } else {
                                    queuedBackPresses = 0
                                }
                            }
                        }
                    }
            }
        }
    }
    val selectTab: (AppRoute) -> Unit = { selectedTab ->
        if (backStack.lastOrNull()?.route != selectedTab) {
            routeTransitionDirection = 0f
            backStack.clear()
            backStack.add(newRouteEntry(AppRoute.BUDGET))
            if (selectedTab != AppRoute.BUDGET) backStack.add(newRouteEntry(selectedTab))
            if (selectedTab == AppRoute.REPORTS) {
                val reportsStateKey = "tab-${AppRoute.REPORTS.name}"
                state.scrollPositions[reportsStateKey] = 0
                state.lazyScrollPositions[reportsStateKey] = 0 to 0
                routeLazyListStates[reportsStateKey]?.let { listState ->
                    listState.requestScrollToItem(0)
                }
            }
        }
    }
    val handleBack: () -> Unit = {
        if (backButtonTransitionRunning) {
            queuedBackPresses += 1
        } else {
            val backAnimationWasRunning = backGestureCancelJob != null
            backGestureCancelJob?.cancel()
            backGestureCancelJob = null
            if (backGestureCommitPending || backAnimationWasRunning) {
                backGestureCommitPending = false
                backGestureProgress = 1f
                skipNextRouteTransition = true
                popRoute()
            } else if (backStack.size > 1) {
                // Button back runs the same route pair and progress transform as predictive back.
                startBackButtonTransition()
            }
        }
    }
    val navigation =
        AppNavigation(
            open = { next ->
                if (next == AppRoute.NEW_TRANSACTION) {
                    newTransaction(null)
                } else {
                    if (next.isReportDetail() && backStack.lastOrNull()?.route != AppRoute.REPORTS) {
                        selectTab(AppRoute.REPORTS)
                    }
                    routeTransitionDirection = 1f
                    backStack.add(newRouteEntry(next))
                }
            },
            reset = { next ->
                routeTransitionDirection = 0f
                backStack.clear()
                backStack.add(newRouteEntry(next))
            },
            replaceTop = { next ->
                routeTransitionDirection = 1f
                if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                backStack.add(newRouteEntry(next))
            },
            newTransaction = newTransaction,
            selectTab = selectTab,
            back = handleBack,
            backToList = {
                if (backStack.size > 2) {
                    routeTransitionDirection = -1f
                    backStack.removeAt(backStack.lastIndex)
                    backStack.removeAt(backStack.lastIndex)
                } else {
                    handleBack()
                }
            },
        )
    val routeEntry = backStack.last()
    val route = routeEntry.route
    val merchantMatcherNeeded = mobileSession.transactionRecords.isNotEmpty() || mobileSession.accountRecords.isNotEmpty()
    LaunchedEffect(mobileSession, merchantMatcherNeeded) {
        if (merchantIconMatcher == null && merchantIconMatcherLoadError == null && merchantMatcherNeeded) {
            try {
                merchantIconMatcher = withContext(Dispatchers.Default) { loadMerchantIconMatcherPack() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantIconMatcherLoadError = "Couldn’t load merchant icons."
            }
        }
    }
    val merchantCatalogNeeded = route == AppRoute.MERCHANT_ICON_LICENSES
    LaunchedEffect(mobileSession, merchantCatalogNeeded) {
        if (merchantIconCatalog == null && merchantIconCatalogLoadError == null && merchantCatalogNeeded) {
            try {
                merchantIconCatalog = withContext(Dispatchers.Default) { loadMerchantIconCatalog() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                merchantIconCatalogLoadError = "Couldn’t load icon sources."
            }
        }
    }
    LaunchedEffect(routeEntry) {
        if (route != AppRoute.STARTUP) onStartupComplete?.invoke()
        if (skipNextRouteTransition) {
            skipNextRouteTransition = false
            backGestureCommitPending = false
            if (backPreviewRoute == routeEntry) {
                // Keep the interactive preview in place until AnimatedContent has
                // promoted this same route entry to its foreground destination.
                coroutineScope.launch {
                    withFrameNanos { }
                    if (backPreviewRoute == routeEntry) {
                        backGestureProgress = 0f
                        backGestureOutgoingEntry = null
                        backPreviewRoute = null
                    }
                }
            } else {
                backGestureProgress = 0f
                backGestureOutgoingEntry = null
                backPreviewRoute = null
            }
        }
    }
    var previousRoute by remember { mutableStateOf(route) }
    val automaticSyncRequest =
        rememberUpdatedState(
            newValue = {
                val now = nowEpochMillis()
                if (mobileSession.isSyncing || automaticSyncInFlight) {
                    pendingAutomaticSync = true
                } else if (
                    shouldTriggerAutomaticSync(
                        isBudgetVisible = route == AppRoute.BUDGET,
                        isAuthenticated = mobileSession.isAuthenticated,
                        syncOnOpen = state.syncOnOpen,
                        budgetId = state.selectedBudgetId,
                        isSyncing = false,
                        encryptedBudgetNeedsPassword = state.selectedBudgetEncrypted,
                        hasEncryptionPassword = state.encryptedPassword.isNotBlank(),
                        lastAutomaticSyncAtEpochMillis =
                            lastAutomaticSyncAtEpochMillis.takeIf {
                                lastAutomaticSyncBudgetId == state.selectedBudgetId
                            },
                        nowEpochMillis = now,
                    )
                ) {
                    val budgetId = state.selectedBudgetId
                    val encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() }
                    automaticSyncInFlight = true
                    coroutineScope.launch {
                        var syncSucceeded = false
                        try {
                            syncSucceeded =
                                mobileSession.synchronize(
                                    budgetId,
                                    encryptionPassword,
                                ) != null
                        } finally {
                            lastAutomaticSyncAtEpochMillis = now.takeIf { syncSucceeded }
                            lastAutomaticSyncBudgetId = budgetId.takeIf { syncSucceeded }
                            automaticSyncInFlight = false
                        }
                    }
                }
            },
        )
    LaunchedEffect(appResumeEvents) {
        appResumeEvents.collect {
            accessibilitySettingsRevision += 1
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(networkRestoredEvents) {
        networkRestoredEvents.collect { automaticSyncRequest.value() }
    }
    LaunchedEffect(mobileSession.isSyncing, pendingAutomaticSync) {
        if (!mobileSession.isSyncing && pendingAutomaticSync) {
            pendingAutomaticSync = false
            if (!mobileSession.syncComplete) lastAutomaticSyncAtEpochMillis = null
            automaticSyncRequest.value()
        }
    }

    LaunchedEffect(route, mobileSession.pairedDevice?.deviceId) {
        if (
            route == AppRoute.BUDGET_CHOOSER &&
            mobileSession.isAuthenticated &&
            !mobileSession.budgetCatalogLoaded &&
            mobileSession.budgets.isEmpty()
        ) {
            mobileSession.loadBudgets()
        }
    }
    LaunchedEffect(route, state.selectedBudgetId, availableBudgets) {
        if (route == AppRoute.BUDGET_CHOOSER && state.selectedBudgetId.isBlank()) {
            availableBudgets.firstOrNull()?.let { budget ->
                state.selectedBudget = budget.name
                state.selectedBudgetId = budget.budgetId
                state.selectedBudgetEncrypted = budget.isEncrypted
            }
        }
    }
    LaunchedEffect(route, state.selectedBudgetId, syncRetryCounter) {
        if (route == AppRoute.FIRST_SYNC && state.selectedBudgetId.isNotBlank()) {
            mobileSession.synchronize(
                budgetId = state.selectedBudgetId,
                encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
            )
            if (mobileSession.syncComplete) state.encryptedPassword = ""
        }
    }
    LaunchedEffect(state.selectedBudgetId, mobileSession) {
        if (state.selectedBudgetId.isNotBlank()) {
            mobileSession.observeLocalRecords(state.selectedBudgetId)
        }
    }
    LaunchedEffect(state.selectedBudgetId, state.selectedMonthId, mobileSession) {
        mobileSession.observeSelectedBudgetMonth(state.selectedBudgetId, state.selectedMonthId)
    }
    LaunchedEffect(
        route,
        state.selectedBudgetId,
        state.syncOnOpen,
        mobileSession.isAuthenticated,
        state.selectedBudgetEncrypted,
        state.encryptedPassword,
    ) {
        if (
            route == AppRoute.BUDGET &&
            previousRoute != AppRoute.FIRST_SYNC &&
            state.syncOnOpen &&
            mobileSession.isAuthenticated &&
            state.selectedBudgetId.isNotBlank() &&
            (!state.selectedBudgetEncrypted || state.encryptedPassword.isNotBlank())
        ) {
            automaticSyncRequest.value()
        }
        previousRoute = route
    }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.budgetDisplaySettingsBudgetId,
        mobileSession.budgetDisplaySettings,
    ) {
        state.budgetDisplaySettings =
            mobileSession.budgetDisplaySettings.takeIf {
                mobileSession.budgetDisplaySettingsBudgetId == state.selectedBudgetId
            }
    }
    var liveTransactions by remember { mutableStateOf(emptyList<TransactionItem>()) }
    var liveActivityTransactions by remember { mutableStateOf(emptyList<TransactionItem>()) }
    val transactionRecords = mobileSession.transactionRecords
    val activityTransactionRecords = mobileSession.activityTransactionRecords
    val accountRecords = mobileSession.accountRecords
    val payeeRecords = mobileSession.payeeRecords
    val categoryRecords = mobileSession.categoryRecords
    val displaySettings = mobileSession.budgetDisplaySettings
    LaunchedEffect(
        transactionRecords,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        merchantIconMatcher,
    ) {
        liveTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    transactionRecords,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    merchantIconMatcher,
                )
            }
    }
    LaunchedEffect(
        activityTransactionRecords,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        merchantIconMatcher,
    ) {
        liveActivityTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    activityTransactionRecords,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    merchantIconMatcher,
                )
            }
    }
    LaunchedEffect(accountRecords) {
        liveAccounts =
            withContext(Dispatchers.Default) {
                liveAccountSummaries(accountRecords, mobileSession.accountGroupRecords)
            }
    }
    LaunchedEffect(mobileSession.scheduleRecords, mobileSession.payeeRecords, accountRecords) {
        val schedules = mobileSession.scheduleRecords
        val payees = mobileSession.payeeRecords
        liveSchedules =
            withContext(Dispatchers.Default) {
                liveScheduleSummaries(schedules, payees, accountRecords)
            }
    }
    var liveBudgetMonth by remember { mutableStateOf<LiveBudgetMonthSummary?>(null) }
    LaunchedEffect(
        mobileSession.selectedBudgetMonthRecord,
        mobileSession.categoryRecords,
        mobileSession.categoryGroupRecords,
        state.selectedMonthId,
    ) {
        val monthRecord = mobileSession.selectedBudgetMonthRecord?.takeIf { it.recordId == state.selectedMonthId }
        val monthCategories = mobileSession.categoryRecords
        val monthCategoryGroups = mobileSession.categoryGroupRecords
        liveBudgetMonth = null
        liveBudgetMonth =
            withContext(Dispatchers.Default) {
                liveBudgetMonthSummary(monthRecord, monthCategories, monthCategoryGroups)
            }
    }

    val pairDevice = {
        if (!mobileSession.isPairing) {
            coroutineScope.launch {
                mobileSession.pair(state.serverUrl, state.pairingCode, deviceName)
                if (mobileSession.pairedDevice != null) {
                    state.pairingCode = ""
                    navigation.reset(AppRoute.PAIRING_RESULT)
                }
            }
        }
    }
    val retryStoredSession: () -> Unit = {
        coroutineScope.launch {
            val storedServerUrl = mobileSession.restoreStoredSession()
            if (storedServerUrl != null) {
                state.serverUrl = storedServerUrl
                backStack.clear()
                backStack.add(newRouteEntry(AppRoute.BUDGET_CHOOSER))
            }
        }
    }
    val selectBudget: (BudgetCatalogItem) -> Unit = { budget ->
        if (state.selectedBudgetId != budget.budgetId) state.encryptedPassword = ""
        if (state.selectedBudgetId != budget.budgetId) state.budgetDisplaySettings = null
        state.selectedBudget = budget.name
        state.selectedBudgetId = budget.budgetId
        state.selectedBudgetEncrypted = budget.isEncrypted
    }
    val refreshBudgets: () -> Unit = {
        coroutineScope.launch {
            mobileSession.loadBudgets()
            if (mobileSession.budgets.none { it.budgetId == state.selectedBudgetId }) {
                mobileSession.budgets.firstOrNull()?.let(selectBudget)
            }
        }
    }
    val openSelectedBudget: () -> Unit = {
        val selected = availableBudgets.firstOrNull { it.budgetId == state.selectedBudgetId }
        if (selected == null) {
            // Keep a visible message in the chooser when neither cache nor server has a catalog.
            coroutineScope.launch {
                mobileSession.loadBudgets()
            }
        } else {
            coroutineScope.launch {
                val isCached = mobileSession.hasCachedBudgetSnapshot(selected.budgetId)
                when {
                    isCached -> navigation.reset(AppRoute.BUDGET)
                    selected.isEncrypted && state.encryptedPassword.isBlank() -> navigation.reset(AppRoute.UNLOCK_BUDGET)
                    else -> navigation.reset(AppRoute.FIRST_SYNC)
                }
            }
        }
    }
    val syncNow: () -> Unit = {
        if (!mobileSession.isSyncing && state.selectedBudgetId.isNotBlank()) {
            coroutineScope.launch {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val saveTransaction: ((Boolean) -> Unit) -> Unit = { onComplete ->
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (!state.readOnlyMode && transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                val saved =
                    mobileSession.updateTransaction(
                        budgetId = state.selectedBudgetId,
                        transactionId = transactionId,
                        amountText = state.transactionAmount,
                        notes = state.transactionNotes,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf(String::isNotBlank),
                        categoryId = state.newTransactionCategoryId.takeIf(String::isNotBlank),
                        date = state.transactionDate,
                        originalAmountMinor = originalAmount,
                        displaySettings = state.budgetDisplaySettings,
                        updateDetails = true,
                    )
                if (saved) {
                    val amount = state.parseAmountMinorUnits(state.transactionAmount) ?: kotlin.math.abs(originalAmount)
                    state.selectedTransactionAmountMinor = if (originalAmount < 0) -amount else amount
                    state.selectedTransactionNotes = state.transactionNotes
                    state.selectedTransactionAccountId = state.newTransactionAccountId
                    state.selectedTransactionPayeeId = state.newTransactionPayeeId
                    state.selectedTransactionCategoryId = state.newTransactionCategoryId
                    state.selectedTransactionDate = state.transactionDate
                }
                onComplete(saved)
            }
        } else {
            onComplete(false)
        }
    }
    val deleteTransaction: ((Boolean) -> Unit) -> Unit = { onComplete ->
        val transactionId = state.selectedTransactionRecordId
        if (!state.readOnlyMode && transactionId.isNotBlank() && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                onComplete(mobileSession.deleteTransaction(state.selectedBudgetId, transactionId))
            }
        } else {
            onComplete(false)
        }
    }
    val createTransaction: () -> Unit = {
        if (!state.readOnlyMode && !mobileSession.isSavingTransaction) {
            val enteredAmount = state.parseAmountMinorUnits(state.transactionAmount)
            val signedAmountText =
                enteredAmount?.let { amount ->
                    val unsignedAmount = kotlin.math.abs(amount)
                    state.formatAmount(if (state.transactionType == "Income") unsignedAmount else -unsignedAmount)
                } ?: state.transactionAmount
            coroutineScope.launch {
                val created =
                    mobileSession.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf { it.isNotBlank() },
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() },
                        amountText = signedAmountText,
                        dateText = state.transactionDate,
                        notes = state.transactionNotes,
                        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                        displaySettings = state.budgetDisplaySettings,
                    )
                if (created) navigation.back()
            }
        }
    }
    val resolveConflict: (String, Boolean) -> Unit = { operationId, keepDeviceVersion ->
        if (!mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                mobileSession.resolveConflict(
                    budgetId = state.selectedBudgetId,
                    operationId = operationId,
                    keepDeviceVersion = keepDeviceVersion,
                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val discardWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch { mobileSession.discardWrite(state.selectedBudgetId, operationId) }
    }
    val retryUncertainWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch {
            mobileSession.retryUncertainWrite(
                state.selectedBudgetId,
                operationId,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }
    val checkUncertainWriteStatus: (String) -> Unit = { operationId ->
        if (!mobileSession.isSyncing) {
            coroutineScope.launch {
                mobileSession.checkUncertainWriteStatus(
                    state.selectedBudgetId,
                    operationId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    TandemTheme(
        state.theme,
        state.accent,
        reduceMotion,
        animateThemeChanges = state.settingsStorageReady || appPreferencesStore == null,
    ) {
        val palette = tandemPalette
        val isDark =
            state.theme == ThemeMode.DARK ||
                state.theme == ThemeMode.PURE_BLACK ||
                (state.theme == ThemeMode.SYSTEM && isSystemInDarkTheme())
        SideEffect {
            onSystemAppearanceChanged?.invoke(isDark, state.theme == ThemeMode.SYSTEM)
        }
        TandemSystemBars(isDark, palette.background)
        val updateBackGesture: (Float, Float) -> Unit = { progress, direction ->
            backGestureDirection = direction
            if (progress > 0f) {
                backGestureCommitPending = progress >= 1f
                backGestureCancelJob?.cancel()
                backGestureCancelJob = null
                backButtonTransitionRunning = false
                backGestureProgress = progress
                if (backGestureOutgoingEntry == null) {
                    backGestureOutgoingEntry = backStack.lastOrNull()
                }
                if (backPreviewRoute == null) {
                    backPreviewRoute = backStack.getOrNull(backStack.lastIndex - 1)
                }
            } else if (backGestureProgress > 0f) {
                backGestureCommitPending = false
                backButtonTransitionRunning = false
                backGestureCancelJob?.cancel()
                backGestureCancelJob =
                    coroutineScope.launch {
                        val returnAnimation = Animatable(backGestureProgress)
                        returnAnimation.animateTo(
                            targetValue = 0f,
                            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.BACK_CANCEL_MILLIS),
                        ) {
                            backGestureProgress = value
                        }
                        backGestureOutgoingEntry = null
                        backPreviewRoute = null
                        backGestureCancelJob = null
                    }
            } else {
                backGestureCommitPending = false
                backGestureProgress = 0f
                backGestureOutgoingEntry = null
                backPreviewRoute = null
            }
        }
        TandemBackHandler(
            enabled = backStack.size > 1,
            onBack = handleBack,
            onBackProgress = updateBackGesture,
        )
        val mainCloudStatus =
            resolveMainCloudStatus(
                syncEnabled =
                    state.selectedBudgetId.isNotBlank() &&
                        (mobileSession.isAuthenticated || mobileSession.isRestoringStoredSession),
                isSyncing = mobileSession.isSyncing,
                hasSyncError = mobileSession.syncError != null,
                persistedSyncStatus = mobileSession.localSyncState?.status,
                pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
                hasSuccessfulSync = mobileSession.localSyncState?.lastSyncedAtEpochMs != null,
            )
        val renderHeader: @Composable (AppRoute) -> Unit = { headerRoute ->
            if (headerRoute != AppRoute.WELCOME && headerRoute != AppRoute.STARTUP) {
                AppHeader(
                    route = headerRoute,
                    state = state,
                    showBack = !headerRoute.isTab(),
                    onBack = handleBack,
                    onSync = { navigation.open(AppRoute.SYNC_CENTER) },
                    syncStatus = mainCloudStatus,
                    onBudget = { navigation.open(AppRoute.BUDGET_CHOOSER) },
                    actualConnected = mobileSession.isServerReachable,
                    liveAccounts = liveAccounts,
                    liveBudgetMonth = liveBudgetMonth,
                    activitySelectionTotalMinor = activitySelectionTotalMinor,
                )
            }
        }
        val renderRoute: @Composable (
            AppRouteEntry,
            Modifier,
            Boolean,
            Boolean,
        ) -> Unit = { visibleEntry, pageModifier, saveRouteState, includeChrome ->
            val visibleRoute = visibleEntry.route
            val routeScrollState =
                remember(visibleEntry.stateKey) {
                    routeScrollStates.getOrPut(visibleEntry.stateKey) {
                        ScrollState(state.scrollPositions[visibleEntry.stateKey] ?: 0)
                    }
                }
            val routeLazyListState =
                remember(visibleEntry.stateKey) {
                    routeLazyListStates.getOrPut(visibleEntry.stateKey) {
                        val (index, offset) = state.lazyScrollPositions[visibleEntry.stateKey] ?: (0 to 0)
                        LazyListState(index, offset)
                    }
                }
            LaunchedEffect(visibleEntry.stateKey, routeScrollState) {
                snapshotFlow { routeScrollState.value }.collect { state.scrollPositions[visibleEntry.stateKey] = it }
            }
            LaunchedEffect(visibleEntry.stateKey, routeLazyListState) {
                snapshotFlow {
                    routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                }.collect { state.lazyScrollPositions[visibleEntry.stateKey] = it }
            }
            if (saveRouteState) {
                DisposableEffect(visibleEntry.stateKey, routeScrollState, routeLazyListState) {
                    onDispose {
                        state.scrollPositions[visibleEntry.stateKey] = routeScrollState.value
                        state.lazyScrollPositions[visibleEntry.stateKey] =
                            routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                        if (!visibleRoute.isTab() && backStack.none { it.stateKey == visibleEntry.stateKey }) {
                            state.scrollPositions.remove(visibleEntry.stateKey)
                            state.lazyScrollPositions.remove(visibleEntry.stateKey)
                            routeLazyListStates.remove(visibleEntry.stateKey)
                        }
                        routeScrollStates.remove(visibleEntry.stateKey)
                    }
                }
            }
            val routeContent: @Composable () -> Unit = {
                CompositionLocalProvider(
                    LocalRouteScrollState provides routeScrollState,
                    LocalRouteLazyListState provides routeLazyListState,
                ) {
                    Column(modifier = pageModifier.fillMaxSize()) {
                        if (includeChrome) renderHeader(visibleRoute)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            ScreenContent(
                                route = visibleRoute,
                                state = state,
                                deviceName = deviceName,
                                navigation = navigation,
                                mobileSession = mobileSession,
                                reportsDataCache = reportsDataCache,
                                onPairDevice = pairDevice,
                                onRetryStoredSession = retryStoredSession,
                                onSelectBudget = selectBudget,
                                onRefreshBudgets = refreshBudgets,
                                onOpenSelectedBudget = openSelectedBudget,
                                onRetrySync = { syncRetryCounter += 1 },
                                backgroundSyncSupported = onBackgroundSyncSettingsChanged != null,
                                merchantIconMatcher = merchantIconMatcher,
                                merchantIconMatcherLoadError = merchantIconMatcherLoadError,
                                merchantIconCatalog = merchantIconCatalog,
                                merchantIconCatalogLoadError = merchantIconCatalogLoadError,
                                liveTransactions = liveTransactions,
                                liveActivityTransactions = liveActivityTransactions,
                                onActivitySelectionTotalChanged = { activitySelectionTotalMinor = it },
                                activitySortCache = activitySortCache,
                                liveAccounts = liveAccounts,
                                liveSchedules = liveSchedules,
                                liveBudgetMonth = liveBudgetMonth,
                                onSyncNow = syncNow,
                                onSaveTransaction = saveTransaction,
                                onDeleteTransaction = deleteTransaction,
                                onCreateTransaction = createTransaction,
                                onResolveConflict = resolveConflict,
                                onDiscardWrite = discardWrite,
                                onRetryUncertainWrite = retryUncertainWrite,
                                onCheckUncertainWriteStatus = checkUncertainWriteStatus,
                                onForgetDevice = { coroutineScope.launch { mobileSession.forgetDevice() } },
                                onForgetDeviceLocally = { coroutineScope.launch { mobileSession.forgetDeviceLocally() } },
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onOpenNotificationSettings = onOpenNotificationSettings,
                                notificationPermissionEnabled = notificationPermissionEnabled,
                                onToggleNotificationPermission = onToggleNotificationPermission,
                            )
                            if (visibleRoute == AppRoute.BUDGET && state.selectedBudgetId.isNotBlank()) {
                                val transactionActionCompact by
                                    remember(routeScrollState, routeLazyListState) {
                                        derivedStateOf {
                                            routeScrollState.value > 72 ||
                                                routeLazyListState.firstVisibleItemIndex > 0 ||
                                                routeLazyListState.firstVisibleItemScrollOffset > 72
                                        }
                                    }
                                FloatingTransactionAction(
                                    compact = transactionActionCompact,
                                    enabled = !state.readOnlyMode,
                                    onClick = {
                                        navigation.newTransaction(null)
                                    },
                                    modifier =
                                        Modifier
                                            .align(
                                                Alignment.BottomEnd,
                                            ).padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                                )
                            }
                            val pendingSyncOperation =
                                mobileSession.pendingWrites.firstOrNull {
                                    it.operationId == state.selectedConflictOperationId &&
                                        (it.status == "queued" || (it.status == "sending" && mobileSession.isSyncing))
                                }
                            val showSyncAction =
                                (visibleRoute == AppRoute.SYNC_CENTER && state.selectedBudgetId.isNotBlank()) ||
                                    (visibleRoute == AppRoute.UNCERTAIN_WRITE && pendingSyncOperation != null)
                            if (showSyncAction) {
                                val syncActionCompact by
                                    remember(routeScrollState) {
                                        derivedStateOf { routeScrollState.value > 72 }
                                    }
                                val syncActionEnabled =
                                    !mobileSession.isSyncing &&
                                        (visibleRoute != AppRoute.SYNC_CENTER || mobileSession.isAuthenticated)
                                FloatingSyncAction(
                                    compact = syncActionCompact,
                                    syncing = mobileSession.isSyncing,
                                    enabled = syncActionEnabled,
                                    onClick = syncNow,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                                )
                            }
                        }
                        if (includeChrome && visibleRoute.isTab()) BottomNavigation(visibleRoute, navigation.selectTab)
                    }
                }
            }
            if (saveRouteState) {
                saveableStateHolder.SaveableStateProvider(visibleEntry.stateKey) { routeContent() }
            } else {
                routeContent()
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(appBackgroundBrush(state.backgroundStyle, palette))
                    .safeDrawingPadding(),
        ) {
            val backTargetTab = backPreviewRoute?.route?.takeIf { it.isTab() }
            val tabShellRoute =
                route.takeIf { it.isTab() }
                    ?: backTargetTab
                    ?: backStack.dropLast(1).lastOrNull { it.route.isTab() }?.route
            val stationaryTabShell = tabShellRoute != null
            val shellHeaderRoute = if (backTargetTab != null) backTargetTab else route
            if (tabShellRoute != null) renderHeader(shellHeaderRoute)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .tandemEdgeBackGesture(backStack.size > 1, handleBack, updateBackGesture),
            ) {
                if (backGestureProgress > 0f && backPreviewRoute != null && backPreviewRoute != routeEntry) {
                    val direction = backGestureDirection
                    renderRoute(
                        backPreviewRoute!!,
                        Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics {}
                            .graphicsLayer {
                                val progress = backGestureProgress
                                translationX = -direction * size.width * (1f - progress)
                            },
                        false,
                        !stationaryTabShell,
                    )
                }
                AnimatedContent(
                    targetState = routeEntry,
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = {
                        when {
                            reduceMotion ||
                                skipNextRouteTransition ||
                                initialState.route == AppRoute.STARTUP ||
                                targetState.route == AppRoute.STARTUP ->
                                EnterTransition.None togetherWith ExitTransition.None
                            initialState.route.isTab() && targetState.route.isTab() ->
                                fadeIn(tween(TandemMotion.CROSSFADE_MILLIS)) togetherWith
                                    fadeOut(tween(TandemMotion.CROSSFADE_MILLIS)) using null
                            routeTransitionDirection > 0f ->
                                (
                                    slideInHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { it }
                                ) togetherWith
                                    slideOutHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { -it } using null
                            routeTransitionDirection < 0f ->
                                slideInHorizontally(
                                    tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                ) { -it } togetherWith
                                    slideOutHorizontally(
                                        tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.movementEasing),
                                    ) { it } using null
                            else -> EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                    label = "tandem-screen-transition",
                ) { visibleEntry ->
                    renderRoute(
                        visibleEntry,
                        Modifier.fillMaxSize().graphicsLayer {
                            if (visibleEntry == backGestureOutgoingEntry && backGestureProgress > 0f) {
                                translationX = backGestureProgress * size.width * backGestureDirection
                            }
                        },
                        true,
                        !stationaryTabShell,
                    )
                }
            }
            if (tabShellRoute != null) {
                BottomNavigation(
                    tabShellRoute,
                    navigation.selectTab,
                    visible = route.isTab() || backTargetTab != null,
                )
            }
        }
    }
}

internal fun AppRoute.isTab(): Boolean =
    this == AppRoute.BUDGET ||
        this == AppRoute.ACCOUNTS ||
        this == AppRoute.ACTIVITY ||
        this == AppRoute.REPORTS ||
        this == AppRoute.SETTINGS

internal fun AppRoute.isReportDetail(): Boolean =
    this == AppRoute.REPORT_SPENDING_BY_CATEGORY ||
        this == AppRoute.REPORT_SPENDING_OVER_TIME ||
        this == AppRoute.REPORT_INCOME_AND_SPENDING ||
        this == AppRoute.REPORT_BUDGETED_AND_SPENT ||
        this == AppRoute.REPORT_NET_WORTH
