@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.data.database.ActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AlertKind
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.data.store.bankSyncFailureAlert
import app.tandemforactual.ui.core.format.AppLanguageController
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.format.parseSharedTransactionText
import app.tandemforactual.ui.core.format.platformMinuteOfDay
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.LocalCrashReports
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.OpenIdLoginProofState
import app.tandemforactual.ui.core.session.PlanningKeyring
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.actualOpenIdReturn
import app.tandemforactual.ui.core.session.budget.accountClosedOverridesFor
import app.tandemforactual.ui.core.session.budget.accountNameOverridesFor
import app.tandemforactual.ui.core.session.budget.accountRecordsWithPendingCreates
import app.tandemforactual.ui.core.session.budget.budgetMutationsWaitingForConnection
import app.tandemforactual.ui.core.session.budget.categoryGroupIdOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryGroupNameOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryGroupVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryNameOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.deletedCategoryIdsFor
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationsFor
import app.tandemforactual.ui.core.session.budget.pendingCategoryBudgetOverrides
import app.tandemforactual.ui.core.session.budget.pendingCategoryCarryoverOverrides
import app.tandemforactual.ui.core.session.budget.pendingCategoryCreatesFor
import app.tandemforactual.ui.core.session.budget.pendingCategoryGroupCreatesFor
import app.tandemforactual.ui.core.session.budget.processQueuedBudgetReorders
import app.tandemforactual.ui.core.session.budget.requestBudgetOrderFlush
import app.tandemforactual.ui.core.session.budget.retryQueuedBudgetReorders
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.session.transactions.undoTransactionDeletes
import app.tandemforactual.ui.core.session.transactions.updateTransaction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.AppStartupDestination
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.BackRequestDisposition
import app.tandemforactual.ui.core.state.BudgetSelectionSnapshot
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.MerchantIconCatalog
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.appRouteStackSaver
import app.tandemforactual.ui.core.state.backRequestDisposition
import app.tandemforactual.ui.core.state.budgetChoicesForSelection
import app.tandemforactual.ui.core.state.buildLiveTransactionItems
import app.tandemforactual.ui.core.state.cachedBudgetName
import app.tandemforactual.ui.core.state.cachedBudgetsWithLocalSnapshots
import app.tandemforactual.ui.core.state.decodeAppTabBackStacks
import app.tandemforactual.ui.core.state.decodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.encodeCachedBudgetCatalog
import app.tandemforactual.ui.core.state.formatAmount
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.headerSyncCaption
import app.tandemforactual.ui.core.state.liveAccountSummaries
import app.tandemforactual.ui.core.state.liveBudgetMonthSummary
import app.tandemforactual.ui.core.state.liveScheduleSummaries
import app.tandemforactual.ui.core.state.loadMerchantIconCatalog
import app.tandemforactual.ui.core.state.loadMerchantIconMatcherPack
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.resolveAppStartupDestination
import app.tandemforactual.ui.core.state.resolveMainCloudStatus
import app.tandemforactual.ui.core.state.restorePendingBudgetSelectionOrigin
import app.tandemforactual.ui.core.state.restoreTransactionFormBaseline
import app.tandemforactual.ui.core.state.routeContentStateKey
import app.tandemforactual.ui.core.state.selectAppTabNavigation
import app.tandemforactual.ui.core.state.selectAppTabRootNavigation
import app.tandemforactual.ui.core.state.shouldRenderRouteHeader
import app.tandemforactual.ui.core.state.shouldRenderStandaloneRouteHeader
import app.tandemforactual.ui.core.state.shouldTriggerAutomaticSync
import app.tandemforactual.ui.core.state.shouldTriggerAutomaticSyncForRouteEntry
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.core.state.transactionFormHasUnsavedChanges
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.appBackgroundBrush
import app.tandemforactual.ui.design.theme.SystemReduceMotionEnabled
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemSystemBars
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemBottomBarEnter
import app.tandemforactual.ui.design.theme.tandemBottomBarExit
import app.tandemforactual.ui.design.theme.tandemEdgeBackGesture
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.activity.ActivityTransactionSortCache
import app.tandemforactual.ui.feature.auth.AppLockGate
import app.tandemforactual.ui.feature.auth.AppLockState
import app.tandemforactual.ui.feature.auth.DeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalDeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.auth.budgetPasswordSecretId
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock
import app.tandemforactual.ui.feature.onboarding.cleanServerAddress
import app.tandemforactual.ui.feature.onboarding.nextSetupRoute
import app.tandemforactual.ui.feature.onboarding.rememberRecentServerAddress
import app.tandemforactual.ui.feature.reports.ReportsDataCache
import app.tandemforactual.ui.feature.sync.conflictPayeeTitle
import app.tandemforactual.ui.feature.transactions.currentMonthId
import app.tandemforactual.ui.feature.transactions.currentMonthLabel
import app.tandemforactual.ui.feature.transactions.openTransaction
import app.tandemforactual.ui.feature.transactions.receipt.fillNewTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

@Composable
fun TandemApp(
    deviceName: String = "This device",
    database: TandemDatabase? = null,
    deviceCredentialStore: DeviceCredentialStore? = null,
    nowEpochMillis: () -> Long = { 0L },
    allowInsecurePrivateNetwork: Boolean = false,
    onBackgroundSyncSettingsChanged: ((enabled: Boolean, network: SyncNetwork, budgetId: String) -> Unit)? = null,
    onSystemAppearanceChanged: ((isDark: Boolean, followsSystem: Boolean) -> Unit)? = null,
    onRequestNotificationPermission: (() -> Unit)? = null,
    onOpenNotificationSettings: (() -> Unit)? = null,
    notificationPermissionEnabled: Boolean? = null,
    onToggleNotificationPermission: ((Boolean) -> Unit)? = null,
    onStartupComplete: (() -> Unit)? = null,
    appResumeEvents: Flow<Unit> = emptyFlow(),
    /** Whether the app is on screen; periodic sync runs only then. */
    appInForeground: StateFlow<Boolean> = MutableStateFlow(true),
    networkRestoredEvents: Flow<Unit> = emptyFlow(),
    deviceAuthenticator: DeviceAuthenticator? = null,
    encryptedBudgetVault: EncryptedBudgetVault? = null,
    currentNetwork: StateFlow<NetworkKind?> = MutableStateFlow(null),
    crashReports: CrashReports? = null,
    onShareText: ((String) -> Unit)? = null,
    windowHinge: StateFlow<WindowHinge?> = MutableStateFlow(null),
    screenshotScript: ScreenshotScript? = null,
    actualBudgetFiles: ActualBudgetFiles? = null,
    planningKeyStore: DeviceCredentialStore? = null,
    postLocalAlert: ((LocalAlert) -> Unit)? = null,
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
    val apiClient =
        remember(allowInsecurePrivateNetwork, actualBudgetFiles) {
            TandemApiClient(allowInsecurePrivateNetwork, budgetFiles = actualBudgetFiles, today = ::platformTodayIsoDate)
        }
    val openIdLoginProofState =
        rememberSaveable(saver = OpenIdLoginProofState.saver(nowEpochMillis)) {
            OpenIdLoginProofState(nowEpochMillis)
        }
    val mobileSession =
        remember(apiClient, database, deviceCredentialStore, openIdLoginProofState) {
            TandemMobileSession(
                apiClient,
                database,
                nowEpochMillis,
                deviceCredentialStore,
                openIdLoginProofState = openIdLoginProofState,
            ).apply { planning.keyring = PlanningKeyring(planningKeyStore) }
        }
    LaunchedEffect(mobileSession) {
        if (mobileSession.boundEncryptedBudgetId == null && state.selectedBudgetEncrypted) state.encryptedPassword = ""
        mobileSession.budgetQueue.processQueuedBudgetReorders()
    }
    LaunchedEffect(mobileSession) {
        mobileSession.schedules.processQueuedScheduleActions()
    }
    LaunchedEffect(mobileSession) {
        mobileSession.scheduleOpenIdProofExpiry()
    }
    SideEffect { mobileSession.setReadOnlyMode(state.readOnlyMode) }
    val appPreferencesStore = remember(database) { database?.let(::AppPreferencesStore) }
    LaunchedEffect(mobileSession.banking.bankLinks) {
        val links = mobileSession.banking.bankLinks.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val store = appPreferencesStore ?: return@LaunchedEffect
        val post = postLocalAlert ?: return@LaunchedEffect
        val alert = bankSyncFailureAlert(store, state.selectedBudgetId, links.filter { it.failure != null }.map { it.name })
        if (alert != null && state.notificationPolicy.allows(AlertKind.BANK_SYNC_FAILURE, platformMinuteOfDay())) post(alert)
    }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
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
    var syncRetryCounter by remember { mutableStateOf(0) }
    var lastAutomaticSyncAtEpochMillis by remember { mutableStateOf<Long?>(null) }
    var lastAutomaticSyncBudgetId by remember { mutableStateOf<String?>(null) }
    var automaticSyncInFlight by remember { mutableStateOf(false) }
    var pendingAutomaticSync by remember { mutableStateOf(false) }
    var budgetGroupReorderActive by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var cacheDiscoveryComplete by remember { mutableStateOf(false) }
    var sessionRestoreComplete by remember { mutableStateOf(false) }
    var routeTransitionDirection by remember { mutableStateOf(0f) }
    var routeTransitionInputBlocked by remember { mutableStateOf(false) }
    var routeTransitionInputJob by remember { mutableStateOf<Job?>(null) }
    val routeTransitionInputBlockedState = rememberUpdatedState(routeTransitionInputBlocked)
    var backGestureProgress by remember { mutableStateOf(0f) }
    var backGestureDirection by remember { mutableStateOf(1f) }
    var backGestureCommitPending by remember { mutableStateOf(false) }
    var backGestureOutgoingEntry by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backPreviewRoute by remember { mutableStateOf<AppRouteEntry?>(null) }
    var backGestureCancelJob by remember { mutableStateOf<Job?>(null) }
    var backButtonTransitionRunning by remember { mutableStateOf(false) }
    var queuedBackPresses by remember { mutableStateOf(0) }
    var pendingBackAfterRouteTransition by remember { mutableStateOf(false) }
    var skipNextRouteTransition by remember { mutableStateOf(false) }
    var showDiscardTransactionDialog by rememberSaveable { mutableStateOf(false) }
    var transactionSheetClosing by remember { mutableStateOf(false) }
    var transactionSheetPopCount by remember { mutableStateOf(1) }
    var nextRouteEntryId by rememberSaveable { mutableStateOf(2L) }
    val backStack = rememberSaveable(saver = appRouteStackSaver) { mutableStateListOf(AppRouteEntry(AppRoute.STARTUP, 1)) }
    var savedTabBackStacks by rememberSaveable { mutableStateOf("") }
    val newRouteEntry: (AppRoute) -> AppRouteEntry = { nextRoute ->
        val entryId = nextRouteEntryId
        nextRouteEntryId += 1
        AppRouteEntry(nextRoute, entryId)
    }
    val beginRouteTransition: () -> Boolean = {
        if (routeTransitionInputBlocked) {
            false
        } else {
            routeTransitionInputBlocked = true
            routeTransitionInputJob?.cancel()
            routeTransitionInputJob =
                coroutineScope.launch {
                    delay(TandemMotion.ROUTE_MILLIS.toLong() + 80L)
                    routeTransitionInputBlocked = false
                    routeTransitionInputJob = null
                }
            true
        }
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
            if (storedServerUrl != null) state.serverUrl = cleanServerAddress(storedServerUrl)
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
        val hasEncryptedStore =
            state.selectedBudgetId.isNotBlank() && encryptedBudgetVault?.hasStore(state.selectedBudgetId) == true
        val destination =
            resolveAppStartupDestination(
                cacheDiscoveryComplete = cacheDiscoveryComplete,
                hasCachedBudget = mobileSession.cachedBudgets.isNotEmpty() || hasEncryptedStore,
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
                if (hasEncryptedStore) {
                    state.selectedBudgetEncrypted = true
                    backStack.clear()
                    backStack.add(newRouteEntry(AppRoute.HOME))
                } else if (firstCachedBudget != null) {
                    state.selectedBudget = cachedBudgetName(state.selectedBudgetId, state.selectedBudget, firstCachedBudget)
                    state.selectedBudgetId = firstCachedBudget.budgetId
                    state.selectedBudgetEncrypted = firstCachedBudget.isEncrypted
                    backStack.clear()
                    backStack.add(newRouteEntry(AppRoute.HOME))
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
                state.settingsStorageError = "Couldn’t load app settings. Try again."
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
                            "App settings still couldn’t be saved. Check local storage and try again."
                        } else {
                            "Couldn’t save app settings. Try again."
                        }
                }
            }
    }
    LaunchedEffect(
        state.settingsStorageReady,
        state.backgroundSyncEnabled,
        state.syncNetwork,
        state.selectedBudgetId,
        onBackgroundSyncSettingsChanged,
    ) {
        if (state.settingsStorageReady) {
            onBackgroundSyncSettingsChanged?.invoke(
                state.backgroundSyncEnabled,
                state.syncNetwork,
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
            transactionSheetClosing = false
            transactionSheetPopCount = 1
            val eligibleAccounts = liveAccounts.filterNot { it.offBudget }
            val defaultAccount =
                if (state.selectedBudgetId.isNotBlank()) {
                    eligibleAccounts.firstOrNull { it.id == requestedAccountId }
                        ?: eligibleAccounts.firstOrNull { it.id == state.lastTransactionAccountId }
                        ?: eligibleAccounts.firstOrNull()
                } else {
                    null
                }
            state.transactionAmount = state.formatAmount(0)
            state.transactionAccount = defaultAccount?.name ?: "Checking"
            state.newTransactionAccountId = defaultAccount?.id.orEmpty()
            state.newTransactionInitialAccountId = state.newTransactionAccountId
            state.newTransactionInitialAccountName = state.transactionAccount
            state.transactionPayee = ""
            state.newTransactionPayeeId = ""
            state.transactionCategory = ""
            state.newTransactionCategoryId = ""
            state.transactionDate = platformTodayIsoDate()
            state.newTransactionInitialDate = state.transactionDate
            state.transactionType = "Spending"
            state.transactionNotes = ""
            state.selectedTransactionRecordId = ""
            state.selectedTransactionAccountId = ""
            state.selectedTransactionPayeeId = ""
            state.selectedTransactionCategoryId = ""
            state.selectedTransactionDate = ""
            state.selectedTransactionAmountMinor = null
            state.selectedTransactionIsLive = false
            state.selectedTransactionIsTransfer = false
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
                            animationSpec =
                                if (reduceMotion) {
                                    tandemMotionSpec(true, TandemMotion.ROUTE_MILLIS)
                                } else {
                                    tween(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
                                },
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
        state.pendingScheduleToOpenId = null
        val tabSelection = selectAppTabNavigation(backStack.toList(), savedTabBackStacks, selectedTab, newRouteEntry)
        if (tabSelection.backStack != backStack.toList()) {
            routeTransitionDirection = 0f
            backStack.clear()
            backStack.addAll(tabSelection.backStack)
        }
        savedTabBackStacks = tabSelection.savedTabBackStacks
    }
    val selectTabRoot: (AppRoute) -> Unit = { selectedTab ->
        state.pendingScheduleToOpenId = null
        val tabSelection = selectAppTabRootNavigation(backStack.toList(), savedTabBackStacks, selectedTab, newRouteEntry)
        if (tabSelection.backStack != backStack.toList()) {
            routeTransitionDirection = 0f
            backStack.clear()
            backStack.addAll(tabSelection.backStack)
        }
        savedTabBackStacks = tabSelection.savedTabBackStacks
    }
    val performBack: () -> Unit = {
        when (backRequestDisposition(routeTransitionInputBlocked, backButtonTransitionRunning)) {
            BackRequestDisposition.QUEUE_AFTER_BACK_TRANSITION -> {
                queuedBackPresses += 1
            }
            BackRequestDisposition.DEFER_UNTIL_ROUTE_TRANSITION_FINISHES -> {
                pendingBackAfterRouteTransition = true
            }
            BackRequestDisposition.START -> {
                if (beginRouteTransition()) {
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
        }
    }
    val handleBack: () -> Unit = {
        val currentRoute = backStack.lastOrNull()?.route
        if (currentRoute == AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
        if (transactionFormHasUnsavedChanges(state, currentRoute)) {
            backGestureCancelJob?.cancel()
            backGestureCancelJob = null
            backGestureCommitPending = false
            backButtonTransitionRunning = false
            backGestureProgress = 0f
            backGestureOutgoingEntry = null
            backPreviewRoute = null
            queuedBackPresses = 0
            showDiscardTransactionDialog = true
        } else if (backStack.any { it.route.isTransactionForm() }) {
            val formIndex = backStack.indexOfLast { it.route.isTransactionForm() }
            if (backStack.lastIndex > formIndex) {
                routeTransitionDirection = -1f
                backStack.removeAt(backStack.lastIndex)
            } else {
                transactionSheetPopCount = 1
                transactionSheetClosing = true
            }
        } else {
            val returningToUnlock = backStack.getOrNull(backStack.lastIndex - 1)?.route == AppRoute.BUDGET_UNLOCK
            if ((currentRoute == AppRoute.FIRST_SYNC && !returningToUnlock) || currentRoute == AppRoute.BUDGET_UNLOCK) {
                state.restorePendingBudgetSelectionOrigin()
            }
            performBack()
        }
    }
    LaunchedEffect(routeTransitionInputBlocked, backButtonTransitionRunning, pendingBackAfterRouteTransition) {
        if (!routeTransitionInputBlocked && !backButtonTransitionRunning && pendingBackAfterRouteTransition) {
            pendingBackAfterRouteTransition = false
            handleBack()
        }
    }
    val navigation =
        AppNavigation(
            open = { next ->
                if (next != AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
                if (beginRouteTransition()) {
                    coroutineScope.launch {
                        // Finish the opening pointer dispatch before swapping in interactive content.
                        withFrameNanos { }
                        if (next == AppRoute.NEW_TRANSACTION) {
                            newTransaction(null)
                        } else if (backStack.lastOrNull()?.route != next) {
                            if (next.isTransactionForm()) {
                                transactionSheetClosing = false
                                transactionSheetPopCount = 1
                            }
                            if (next.isReportDetail() && backStack.lastOrNull()?.route != AppRoute.REPORTS) {
                                selectTab(AppRoute.REPORTS)
                            }
                            routeTransitionDirection = 1f
                            backStack.add(newRouteEntry(next))
                        }
                    }
                }
            },
            openSchedule = { scheduleId ->
                if (beginRouteTransition()) {
                    coroutineScope.launch {
                        withFrameNanos { }
                        state.pendingScheduleToOpenId = scheduleId
                        if (backStack.lastOrNull()?.route != AppRoute.SCHEDULES) {
                            routeTransitionDirection = 1f
                            backStack.add(newRouteEntry(AppRoute.SCHEDULES))
                        }
                    }
                    true
                } else {
                    false
                }
            },
            reset = { next ->
                state.pendingScheduleToOpenId = null
                routeTransitionDirection = 0f
                savedTabBackStacks = ""
                backStack.clear()
                backStack.add(newRouteEntry(next))
            },
            replaceTop = { next ->
                if (next != AppRoute.SCHEDULES) state.pendingScheduleToOpenId = null
                routeTransitionDirection = 1f
                if (backStack.isNotEmpty()) backStack.removeAt(backStack.lastIndex)
                backStack.add(newRouteEntry(next))
            },
            newTransaction = newTransaction,
            selectTab = selectTab,
            back = handleBack,
            backToList = {
                if (backStack.lastOrNull()?.route?.isTransactionForm() == true && backStack.size > 2) {
                    transactionSheetPopCount = 2
                    transactionSheetClosing = true
                } else if (backStack.size > 2) {
                    routeTransitionDirection = -1f
                    backStack.removeAt(backStack.lastIndex)
                    backStack.removeAt(backStack.lastIndex)
                } else {
                    handleBack()
                }
            },
            selectTabRoot = selectTabRoot,
        )
    val routeEntry = backStack.last()
    val route = routeEntry.route
    SideEffect { mobileSession.feedback.setBudgetActionRoute(routeEntry) }
    LaunchedEffect(mobileSession.budgets, state.selectedBudgetId) {
        mobileSession.budgets
            .firstOrNull { it.budgetId == state.selectedBudgetId }
            ?.name
            ?.takeIf { it.isNotBlank() && it != state.selectedBudget }
            ?.let { state.selectedBudget = it }
    }
    val quickAddRequested by QuickActionEvents.addTransactionRequested.collectAsState()
    LaunchedEffect(
        quickAddRequested,
        route,
        state.selectedBudgetId,
        state.encryptedPassword.isBlank(),
        state.readOnlyMode,
        liveAccounts.isEmpty(),
    ) {
        val budgetReady =
            state.selectedBudgetId.isNotBlank() &&
                !state.readOnlyMode &&
                route.isTab() &&
                !needsEncryptedBudgetUnlock(state, route) &&
                liveAccounts.any { !it.offBudget }
        if (!quickAddRequested || !budgetReady) return@LaunchedEffect
        delay(TandemMotion.ROUTE_MILLIS * 2L)
        if (!routeTransitionInputBlocked && QuickActionEvents.consumeAddTransaction()) {
            if (QuickActionEvents.consumeReceiptScan()) state.receiptScanRequested = true
            newTransaction(null)
            QuickActionEvents.consumeSharedText()?.let(::parseSharedTransactionText)?.let { shared ->
                fillNewTransaction(
                    state,
                    mobileSession,
                    shared.amountMinor,
                    shared.payeeHint,
                    isIncome = shared.isIncome,
                    unmatchedNote = shared.original,
                )
            }
        }
    }
    val quickDestination by QuickActionEvents.destinationRequested.collectAsState()
    LaunchedEffect(quickDestination, route, state.selectedBudgetId, state.encryptedPassword.isBlank()) {
        val budgetReady = state.selectedBudgetId.isNotBlank() && route.isTab() && !needsEncryptedBudgetUnlock(state, route)
        if (quickDestination == null || !budgetReady) return@LaunchedEffect
        delay(TandemMotion.ROUTE_MILLIS * 2L)
        if (routeTransitionInputBlocked) return@LaunchedEffect
        val target =
            when (QuickActionEvents.consumeDestination()) {
                QuickAction.BUDGET_CHECKUP -> AppRoute.DATA_HEALTH
                QuickAction.ACCOUNTS -> AppRoute.ACCOUNTS
                else -> null
            } ?: return@LaunchedEffect
        state.menuOpen = false
        if (target in navigationRoutes(state.interfaceMode)) navigation.selectTab(target) else navigation.open(target)
    }
    val transactionSheetIndex = backStack.indexOfLast { it.route.isTransactionForm() }
    val transactionSheetActive = transactionSheetIndex >= 0
    val handleCurrentRouteBack: () -> Unit = {
        val payeesOverTransactionSheet =
            backStack.lastOrNull()?.route == AppRoute.MANAGE_PAYEES &&
                (
                    backStack.size == 1 ||
                        backStack[backStack.lastIndex - 1].route.let { it == AppRoute.CHOOSE_PAYEE || it.isTransactionForm() }
                )
        if (payeesOverTransactionSheet) {
            routeTransitionInputJob?.cancel()
            routeTransitionInputJob = null
            routeTransitionInputBlocked = false
            pendingBackAfterRouteTransition = false
            routeTransitionDirection = -1f
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                backStack[backStack.lastIndex] = newRouteEntry(AppRoute.CHOOSE_PAYEE)
            }
        } else {
            handleBack()
        }
    }
    val transactionSheetBaseEntry = backStack.getOrNull(transactionSheetIndex - 1)
    val backgroundRouteEntry =
        if (transactionSheetActive && transactionSheetBaseEntry != null) transactionSheetBaseEntry else routeEntry
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
    LaunchedEffect(routeEntry) {
        if (isBackPreviewStale(backPreviewRoute, backGestureOutgoingEntry, routeEntry)) {
            backGestureCancelJob?.cancel()
            backGestureCancelJob = null
            backGestureCommitPending = false
            backButtonTransitionRunning = false
            backGestureProgress = 0f
            backGestureOutgoingEntry = null
            backPreviewRoute = null
        }
    }
    var previousRoute by remember { mutableStateOf(route) }
    val currentNetworkKind by currentNetwork.collectAsState()
    LaunchedEffect(state.localServerUrl, mobileSession.authenticatedDevice?.deviceId, currentNetworkKind) {
        mobileSession.configureLocalServer(state.localServerUrl.ifBlank { null })
        if (currentNetworkKind != null && mobileSession.budgetError != null && mobileSession.isAuthenticated) {
            mobileSession.loadBudgets()
        }
    }
    val automaticSyncRequest =
        rememberUpdatedState(
            newValue = {
                val now = nowEpochMillis()
                if (!state.syncNetwork.allows(currentNetworkKind)) {
                    Unit
                } else if (mobileSession.isSyncing || automaticSyncInFlight) {
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
                    lastAutomaticSyncAtEpochMillis = now
                    lastAutomaticSyncBudgetId = budgetId
                    coroutineScope.launch {
                        try {
                            mobileSession.synchronize(budgetId, encryptionPassword)
                        } finally {
                            automaticSyncInFlight = false
                        }
                    }
                }
            },
        )
    LaunchedEffect(appResumeEvents) {
        appResumeEvents.collect {
            accessibilitySettingsRevision += 1
            mobileSession.budgetQueue.retryQueuedBudgetReorders()
            mobileSession.schedules.retryQueuedScheduleActions()
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(networkRestoredEvents) {
        networkRestoredEvents.collect {
            mobileSession.budgetQueue.retryQueuedBudgetReorders()
            mobileSession.schedules.retryQueuedScheduleActions()
            if (lastAutomaticSyncBudgetId == state.selectedBudgetId) lastAutomaticSyncAtEpochMillis = null
            automaticSyncRequest.value()
        }
    }
    LaunchedEffect(mobileSession.isSyncing, pendingAutomaticSync) {
        if (!mobileSession.isSyncing && pendingAutomaticSync) {
            pendingAutomaticSync = false
            automaticSyncRequest.value()
        }
    }

    LaunchedEffect(route, mobileSession.authenticatedDevice?.deviceId) {
        if (
            route == AppRoute.BUDGET_CHOOSER &&
            mobileSession.isAuthenticated &&
            !mobileSession.budgetCatalogLoaded &&
            mobileSession.budgets.isEmpty()
        ) {
            mobileSession.loadBudgets()
        }
    }
    LaunchedEffect(route) {
        mobileSession.feedback.clearBudgetActionMessage()
    }
    LaunchedEffect(route, state.selectedBudgetId, syncRetryCounter) {
        if (route == AppRoute.FIRST_SYNC && state.selectedBudgetId.isNotBlank()) {
            mobileSession.synchronize(
                budgetId = state.selectedBudgetId,
                encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
            )
            val unlockedPassword = state.encryptedPassword
            if (mobileSession.syncComplete && state.selectedBudgetEncrypted && unlockedPassword.isNotBlank()) {
                val secretId = budgetPasswordSecretId(state.selectedBudgetId)
                withContext(Dispatchers.Default) { deviceAuthenticator?.rememberPasswordCheck(secretId, unlockedPassword) }
                if (state.rememberBudgetPasswordWithBiometrics && deviceAuthenticator?.canStoreSecrets() == true) {
                    deviceAuthenticator.storeSecret(secretId, unlockedPassword, "Use biometrics for ${state.selectedBudget}") {}
                } else {
                    deviceAuthenticator?.deleteSecret(secretId)
                }
            }
        }
    }
    LaunchedEffect(state.selectedBudgetEncrypted, state.selectedBudgetId, encryptedBudgetVault) {
        mobileSession.encryptedStorageRequired =
            if (encryptedBudgetVault != null && state.selectedBudgetEncrypted && state.selectedBudgetId.isNotBlank()) {
                setOf(state.selectedBudgetId)
            } else {
                emptySet()
            }
    }
    LaunchedEffect(state.encryptedPassword.isBlank(), state.selectedBudgetId) {
        if (state.encryptedPassword.isBlank()) mobileSession.forgetEncryptionPasswords()
        val bound = mobileSession.boundEncryptedBudgetId
        if (bound != null && (state.encryptedPassword.isBlank() || bound != state.selectedBudgetId)) {
            mobileSession.unbindEncryptedBudgetDatabase()
        }
    }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession,
        mobileSession.databaseGeneration,
        mobileSession.authenticatedDevice?.principalId,
    ) {
        if (state.selectedBudgetId.isNotBlank()) {
            mobileSession.observeLocalRecords(state.selectedBudgetId)
        }
    }
    LaunchedEffect(
        state.selectedBudgetId,
        state.selectedMonthId,
        mobileSession,
        mobileSession.databaseGeneration,
        mobileSession.authenticatedDevice?.principalId,
    ) {
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
            shouldTriggerAutomaticSyncForRouteEntry(previousRoute, route) &&
            state.syncOnOpen &&
            mobileSession.isAuthenticated &&
            state.selectedBudgetId.isNotBlank() &&
            (!state.selectedBudgetEncrypted || state.encryptedPassword.isNotBlank())
        ) {
            automaticSyncRequest.value()
        }
        previousRoute = route
    }
    val isAppInForeground by appInForeground.collectAsState()
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
    val appLockState = remember { AppLockState(nowEpochMillis) }
    LaunchedEffect(isAppInForeground) { appLockState.onForegroundChanged(isAppInForeground) }
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
            if (!mobileSession.isSyncing && !mobileSession.isPerformingBudgetAction && state.syncNetwork.allows(currentNetwork.value)) {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    background = true,
                )
            }
        }
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
    val activityTransactionRecords = mobileSession.activity.activityTransactionRecords
    val activityTransactionRecordsVersion = mobileSession.activity.activityTransactionRecordsVersion
    val activityTransactionRecordsSnapshot = activityTransactionRecords.toList()
    val accountRecords = mobileSession.budgetQueue.accountRecordsWithPendingCreates(state.selectedBudgetId, mobileSession.accountRecords)
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
        activityTransactionRecordsVersion,
        accountRecords,
        payeeRecords,
        categoryRecords,
        displaySettings,
        merchantIconMatcher,
    ) {
        liveActivityTransactions =
            withContext(Dispatchers.Default) {
                buildLiveTransactionItems(
                    activityTransactionRecordsSnapshot,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                    merchantIconMatcher,
                )
            }
    }
    val accountNameOverrides = mobileSession.budgetQueue.accountNameOverridesFor(state.selectedBudgetId)
    val accountClosedOverrides = mobileSession.budgetQueue.accountClosedOverridesFor(state.selectedBudgetId)
    LaunchedEffect(accountRecords, accountNameOverrides, accountClosedOverrides) {
        liveAccounts =
            withContext(Dispatchers.Default) {
                liveAccountSummaries(accountRecords, mobileSession.accountGroupRecords, accountNameOverrides, accountClosedOverrides)
            }
    }
    LaunchedEffect(
        mobileSession.scheduleRecords,
        mobileSession.payeeRecords,
        accountRecords,
        mobileSession.schedules.pendingScheduleActions,
    ) {
        val schedules = mobileSession.scheduleRecords
        val payees = mobileSession.payeeRecords
        val pendingScheduleActions = mobileSession.schedules.pendingScheduleActions
        liveSchedules =
            withContext(Dispatchers.Default) {
                liveScheduleSummaries(schedules, payees, accountRecords, pendingScheduleActions)
            }
    }
    var liveBudgetMonth by remember { mutableStateOf<LiveBudgetMonthSummary?>(null) }
    val categoryVisibilityOverrides = mobileSession.budgetQueue.categoryVisibilityOverridesFor(state.selectedBudgetId)
    val categoryGroupNameOverrides = mobileSession.budgetQueue.categoryGroupNameOverridesFor(state.selectedBudgetId)
    val categoryNameOverrides = mobileSession.budgetQueue.categoryNameOverridesFor(state.selectedBudgetId)
    val categoryGroupIdOverrides = mobileSession.budgetQueue.categoryGroupIdOverridesFor(state.selectedBudgetId)
    val categoryGroupVisibilityOverrides = mobileSession.budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId)
    val deletedCategoryIds = mobileSession.budgetQueue.deletedCategoryIdsFor(state.selectedBudgetId)
    val pendingCategoryGroups = mobileSession.budgetQueue.pendingCategoryGroupCreatesFor(state.selectedBudgetId)
    val pendingCategories = mobileSession.budgetQueue.pendingCategoryCreatesFor(state.selectedBudgetId)
    val pendingBudgetMutations = mobileSession.budgetQueue.pendingBudgetMutationsFor(state.selectedBudgetId)
    val categoryBudgetOverrides = mobileSession.budgetQueue.pendingCategoryBudgetOverrides(state.selectedBudgetId, state.selectedMonthId)
    val categoryCarryoverOverrides =
        mobileSession.budgetQueue.pendingCategoryCarryoverOverrides(
            state.selectedBudgetId,
            state.selectedMonthId,
        )
    LaunchedEffect(
        mobileSession.selectedBudgetMonthRecord,
        mobileSession.categoryRecords,
        mobileSession.categoryGroupRecords,
        categoryVisibilityOverrides,
        categoryGroupNameOverrides,
        categoryNameOverrides,
        categoryGroupIdOverrides,
        categoryGroupVisibilityOverrides,
        deletedCategoryIds,
        pendingCategoryGroups,
        pendingCategories,
        pendingBudgetMutations,
        categoryBudgetOverrides,
        categoryCarryoverOverrides,
        state.selectedMonthId,
    ) {
        val monthRecord =
            mobileSession.selectedBudgetMonthRecord?.takeIf {
                it.budgetId == state.selectedBudgetId && it.recordId == state.selectedMonthId
            }
        val monthCategories = mobileSession.categoryRecords
        val monthCategoryGroups = mobileSession.categoryGroupRecords
        liveBudgetMonth =
            withContext(Dispatchers.Default) {
                liveBudgetMonthSummary(
                    monthRecord,
                    monthCategories,
                    monthCategoryGroups,
                    categoryVisibilityOverrides,
                    categoryGroupNameOverrides,
                    categoryNameOverrides,
                    categoryGroupIdOverrides,
                    categoryGroupVisibilityOverrides,
                    deletedCategoryIds,
                    pendingCategoryGroups,
                    pendingCategories,
                    categoryBudgetOverrides,
                    categoryCarryoverOverrides,
                    pendingBudgetMutations,
                )
            }
    }

    val signIn = {
        if (!mobileSession.isSigningIn) {
            coroutineScope.launch {
                if (mobileSession.signInWithPassword(state.serverUrl, state.actualPassword, deviceName)) {
                    state.recentServerUrls = rememberRecentServerAddress(state.recentServerUrls, state.serverUrl)
                    state.actualPassword = ""
                    navigation.reset(AppRoute.BUDGET_CHOOSER)
                }
            }
        }
    }
    val startOpenIdSignIn = {
        if (!mobileSession.isSigningIn) {
            coroutineScope.launch {
                val redirectUrl = mobileSession.startActualOpenIdSignIn(state.serverUrl, deviceName)
                if (redirectUrl != null) {
                    try {
                        uriHandler.openUri(redirectUrl)
                    } catch (_: Exception) {
                        mobileSession.cancelActualOpenIdSignIn()
                        mobileSession.reportSignInError("Couldn’t open the sign-in page. Try again.")
                    }
                }
            }
        }
    }
    LaunchedEffect(mobileSession, state.serverUrl) {
        AuthReturnEvents.uri.collect { returnUri ->
            if (returnUri != null) {
                AuthReturnEvents.clear(returnUri)
                val callback = actualOpenIdReturn(returnUri)
                if (callback != null) {
                    if (mobileSession.completeActualOpenIdSignIn(state.serverUrl, callback.code, callback.state)) {
                        state.recentServerUrls = rememberRecentServerAddress(state.recentServerUrls, state.serverUrl)
                        navigation.reset(AppRoute.BUDGET_CHOOSER)
                    }
                }
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
    val openSelectedBudget: (BudgetCatalogItem) -> Unit = openSelectedBudget@{ selected ->
        if (selected.budgetId.isBlank()) {
            navigation.reset(AppRoute.BUDGET_CHOOSER)
            return@openSelectedBudget
        }
        val previousSelection =
            state.pendingBudgetSelectionOrigin ?: BudgetSelectionSnapshot(
                budgetId = state.selectedBudgetId,
                name = state.selectedBudget,
                isEncrypted = state.selectedBudgetEncrypted,
            )
        val switchingBudget = state.selectedBudgetId != selected.budgetId
        if (switchingBudget) state.budgetDisplaySettings = null
        state.selectedBudget = selected.name
        state.selectedBudgetId = selected.budgetId
        state.selectedBudgetEncrypted = selected.isEncrypted
        if (selected.isEncrypted && encryptedBudgetVault != null) {
            val alreadyUnlocked =
                mobileSession.boundEncryptedBudgetId == selected.budgetId && state.encryptedPassword.isNotBlank()
            if (!alreadyUnlocked) state.encryptedPassword = ""
            val setupIncomplete = !state.hasCompletedNotificationOnboarding || !state.hasCompletedSyncOnboarding
            state.pendingSetupAfterUnlock = !alreadyUnlocked && setupIncomplete
            state.pendingBudgetSelectionOrigin = null
            if (alreadyUnlocked && setupIncomplete) {
                navigation.open(nextSetupRoute(state, notificationPermissionEnabled))
            } else {
                navigation.reset(AppRoute.HOME)
            }
            return@openSelectedBudget
        }
        coroutineScope.launch {
            val isCached =
                !selected.isEncrypted &&
                    (
                        mobileSession.cachedBudgets.any { it.budgetId == selected.budgetId } ||
                            mobileSession.hasCachedBudgetSnapshot(selected.budgetId)
                    )
            when {
                isCached -> {
                    if (switchingBudget) state.budgetSwitchSplashId = selected.budgetId
                    state.pendingBudgetSelectionOrigin = null
                    val previousRoute = backStack.getOrNull(backStack.lastIndex - 1)?.route
                    if (previousRoute?.isTab() == true) {
                        navigation.back()
                    } else {
                        navigation.reset(AppRoute.HOME)
                    }
                }
                !selected.isEncrypted || state.encryptedPassword.isNotBlank() -> {
                    state.pendingBudgetSelectionOrigin = previousSelection
                    navigation.open(AppRoute.FIRST_SYNC)
                }
            }
        }
    }
    if (screenshotScript != null) {
        LaunchedEffect(screenshotScript, state.settingsStorageReady) {
            if (!state.settingsStorageReady) return@LaunchedEffect
            state.hasCompletedNotificationOnboarding = true
            state.hasCompletedSyncOnboarding = true
            state.hasDismissedBudgetHint = true
            if (!mobileSession.isAuthenticated) {
                state.serverUrl = screenshotScript.serverUrl
                if (!mobileSession.signInWithPassword(screenshotScript.serverUrl, screenshotScript.password, deviceName)) {
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
            snapshotFlow { routeTransitionInputBlocked }.first { !it }
            if (backStack.lastOrNull()?.route != AppRoute.BUDGET) navigation.reset(AppRoute.BUDGET)
            delay(SCREENSHOT_SETTLE_MILLIS)
            val select = screenshotScript.select
            when (screenshotScript.screen) {
                "budget" -> state.budgetScrollToSummaryRequested = true
                "activity" -> {
                    navigation.selectTab(AppRoute.ACTIVITY)
                    delay(SCREENSHOT_SETTLE_MILLIS)
                    liveActivityTransactions.firstOrNull { it.payee == select }?.let {
                        openTransaction(state, navigation, it, isLive = true, navigate = false)
                    }
                }
                "accounts" -> {
                    liveAccounts.firstOrNull { it.name == select }?.let { state.selectedAccountId = it.id }
                    navigation.selectTab(AppRoute.ACCOUNTS)
                }
                "reports" -> navigation.selectTab(AppRoute.REPORTS)
                "category" -> {
                    mobileSession.categoryRecords.firstOrNull { it.recordName() == select }?.let {
                        state.selectedCategoryId = it.recordId
                        navigation.open(AppRoute.CATEGORY_DETAIL)
                    }
                }
                "add" -> navigation.newTransaction(null)
            }
        }
    }
    val prepareBudgetUnlock: (BudgetCatalogItem) -> Unit = { selected ->
        state.pendingBudgetSelectionOrigin =
            BudgetSelectionSnapshot(
                budgetId = state.selectedBudgetId,
                name = state.selectedBudget,
                isEncrypted = state.selectedBudgetEncrypted,
            )
        state.selectedBudget = selected.name
        state.selectedBudgetId = selected.budgetId
        state.selectedBudgetEncrypted = selected.isEncrypted
        state.encryptedPassword = ""
        navigation.open(AppRoute.BUDGET_UNLOCK)
    }
    val syncNow: () -> Unit = {
        // Send reorders held on the device instead of waiting for the next periodic flush.
        mobileSession.budgetQueue.requestBudgetOrderFlush()
        if (!mobileSession.isSyncing && state.selectedBudgetId.isNotBlank()) {
            coroutineScope.launch {
                mobileSession.synchronize(
                    state.selectedBudgetId,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            }
        }
    }
    val haptics = LocalHapticFeedback.current
    val saveTransaction: (String?, (Boolean) -> Unit) -> Unit = { confirmedFutureDate, onComplete ->
        val transactionId = state.selectedTransactionRecordId
        val originalAmount = state.selectedTransactionAmountMinor
        if (!state.readOnlyMode && transactionId.isNotBlank() && originalAmount != null && !mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                val saved =
                    mobileSession.transactions.updateTransaction(
                        budgetId = state.selectedBudgetId,
                        transactionId = transactionId,
                        amountText = state.transactionAmount,
                        notes = state.transactionNotes,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf(String::isNotBlank),
                        categoryId = state.newTransactionCategoryId.takeIf(String::isNotBlank),
                        date = state.transactionDate,
                        originalAmountMinor = originalAmount,
                        transactionType = state.transactionType,
                        displaySettings = state.budgetDisplaySettings,
                        updateDetails = true,
                        confirmedFutureDate = confirmedFutureDate,
                    )
                if (saved) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    val amount = state.parseAmountMinorUnits(state.transactionAmount) ?: kotlin.math.abs(originalAmount)
                    state.selectedTransactionAmountMinor =
                        when {
                            state.transactionType == "Income" -> amount
                            state.transactionType == "Transfer" && originalAmount >= 0L -> amount
                            else -> -amount
                        }
                    state.selectedTransactionIsTransfer = state.transactionType == "Transfer"
                    state.selectedTransactionNotes = state.transactionNotes
                    state.selectedTransactionAccountId = state.newTransactionAccountId
                    state.selectedTransactionPayeeId = state.newTransactionPayeeId
                    state.selectedTransactionCategoryId = state.newTransactionCategoryId
                    state.selectedTransactionDate = state.transactionDate
                    state.selectedTransactionAccountName = state.transactionAccount
                    state.selectedTransactionPayeeName = state.transactionPayee
                    state.selectedTransactionCategoryName = state.transactionCategory
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
                onComplete(mobileSession.transactions.deleteTransactionsWithUndo(state.selectedBudgetId, setOf(transactionId)))
            }
        } else {
            onComplete(false)
        }
    }
    val createTransaction: (String?) -> Unit = { confirmedFutureDate ->
        if (!state.readOnlyMode && !mobileSession.isSavingTransaction) {
            val enteredAmount = state.parseAmountMinorUnits(state.transactionAmount)
            val signedAmountText =
                enteredAmount?.let { amount ->
                    val unsignedAmount = kotlin.math.abs(amount)
                    state.formatAmount(if (state.transactionType == "Income") unsignedAmount else -unsignedAmount)
                } ?: state.transactionAmount
            coroutineScope.launch {
                val created =
                    mobileSession.transactions.createTransaction(
                        budgetId = state.selectedBudgetId,
                        accountId = state.newTransactionAccountId,
                        payeeId = state.newTransactionPayeeId.takeIf { it.isNotBlank() },
                        categoryId = state.newTransactionCategoryId.takeIf { it.isNotBlank() && state.transactionType != "Transfer" },
                        amountText = signedAmountText,
                        dateText = state.transactionDate,
                        notes = state.transactionNotes,
                        encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                        displaySettings = state.budgetDisplaySettings,
                        isTransfer = state.transactionType == "Transfer",
                        confirmedFutureDate = confirmedFutureDate,
                    )
                if (created) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.lastTransactionAccountId = state.newTransactionAccountId
                    restoreTransactionFormBaseline(state, AppRoute.NEW_TRANSACTION)
                    navigation.back()
                }
            }
        }
    }
    val resolveConflict: (String, Boolean, String) -> Unit = { operationId, keepDeviceVersion, reviewedRevision ->
        if (!mobileSession.isSavingTransaction) {
            coroutineScope.launch {
                mobileSession.conflicts.resolveConflict(
                    budgetId = state.selectedBudgetId,
                    operationId = operationId,
                    keepDeviceVersion = keepDeviceVersion,
                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                    reviewedRevision = reviewedRevision,
                )
            }
        }
    }
    val discardWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch {
            mobileSession.conflicts.discardWrite(
                state.selectedBudgetId,
                operationId,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }
    val retryUncertainWrite: (String) -> Unit = { operationId ->
        coroutineScope.launch {
            mobileSession.conflicts.retryUncertainWrite(
                state.selectedBudgetId,
                operationId,
                state.encryptedPassword.takeIf { it.isNotBlank() },
            )
        }
    }
    val checkUncertainWriteStatus: (String) -> Unit = { operationId ->
        if (!mobileSession.isSyncing) {
            coroutineScope.launch {
                mobileSession.conflicts.checkUncertainWriteStatus(
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
        language = AppLanguageController(state.appLanguage) { state.appLanguage = it },
        interfaceMode = state.interfaceMode,
    ) {
        val palette = tandemPalette
        val windowSize = LocalWindowInfo.current.containerSize
        val isLandscape = isLandscapeViewport(windowSize.width, windowSize.height)
        val useRail = usesRailNavigation(with(LocalDensity.current) { windowSize.width.toDp().value }, isLandscape)
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
            enabled = backStack.size > 1 && !transactionSheetActive,
            onBack = handleCurrentRouteBack,
            onBackProgress = updateBackGesture,
        )
        var headerClockMs by remember { mutableStateOf(nowEpochMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(30_000)
                headerClockMs = nowEpochMillis()
            }
        }
        LaunchedEffect(mobileSession.localSyncState?.lastSyncedAtEpochMs) { headerClockMs = nowEpochMillis() }
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
                hasPendingBudgetChanges = mobileSession.budgetQueue.pendingBudgetMutationCount(state.selectedBudgetId) > 0,
                budgetChangesWaitingForConnection = mobileSession.budgetQueue.budgetMutationsWaitingForConnection(state.selectedBudgetId),
            )
        val renderHeader: @Composable (AppRoute) -> Unit = { headerRoute ->
            if (headerRoute != AppRoute.WELCOME && headerRoute != AppRoute.STARTUP) {
                val conflictOperation =
                    if (headerRoute == AppRoute.CONFLICT_REVIEW) {
                        mobileSession.pendingWrites
                            .firstOrNull { it.operationId == state.selectedConflictOperationId && it.status == "conflict" }
                    } else {
                        null
                    }
                val conflictTitle = conflictPayeeTitle(conflictOperation, liveTransactions, mobileSession)
                AppHeader(
                    route = headerRoute,
                    state = state,
                    showBack = !headerRoute.isTab(),
                    onBack = handleCurrentRouteBack,
                    onSync = { navigation.open(AppRoute.SYNC_CENTER) },
                    syncStatus = mainCloudStatus,
                    pendingWriteStatuses = mobileSession.pendingWrites.map { it.status },
                    onBudget = { navigation.open(AppRoute.SWITCH_BUDGET) },
                    onOpenRoute = navigation.open,
                    actualConnected = mobileSession.isServerReachable,
                    liveAccounts = liveAccounts,
                    liveBudgetMonth = liveBudgetMonth,
                    activitySelectionTotalMinor = activitySelectionTotalMinor,
                    conflictTitle = conflictTitle,
                    compactLandscape = isLandscape,
                    onAddTransaction =
                        if (isLandscape &&
                            !transactionSheetActive &&
                            headerRoute == AppRoute.BUDGET &&
                            shouldShowBudgetCreateAction(state.selectedBudgetId.isNotBlank(), budgetGroupReorderActive)
                        ) {
                            { navigation.newTransaction(null) }
                        } else {
                            null
                        },
                    addTransactionEnabled = !state.readOnlyMode,
                    syncCaption =
                        headerSyncCaption(
                            mainCloudStatus,
                            mobileSession.pendingWrites.map { it.status },
                            mobileSession.localSyncState?.lastSyncedAtEpochMs,
                            headerClockMs,
                        ),
                    onRefreshBudgets = refreshBudgets.takeIf { mobileSession.isAuthenticated && mobileSession.isServerReachable },
                    refreshingBudgets = mobileSession.isLoadingBudgets,
                    onOpenMenu = if (!useRail && state.selectedBudgetId.isNotBlank()) ({ state.menuOpen = true }) else null,
                )
            }
        }
        val renderRoute: @Composable (
            AppRouteEntry,
            Modifier,
            Boolean,
            Boolean,
        ) -> Unit = renderRoute@{ visibleEntry, pageModifier, saveRouteState, includeChrome ->
            val visibleRoute = visibleEntry.route
            if (needsEncryptedBudgetUnlock(state, visibleRoute)) {
                Box(pageModifier.fillMaxSize().background(palette.background))
                return@renderRoute
            }
            val contentStateKey = routeContentStateKey(visibleEntry, state.selectedBudgetId)
            val routeScrollState =
                remember(contentStateKey) {
                    routeScrollStates.getOrPut(contentStateKey) {
                        ScrollState(state.scrollPositions[contentStateKey] ?: 0)
                    }
                }
            val routeLazyListState =
                remember(contentStateKey) {
                    routeLazyListStates.getOrPut(contentStateKey) {
                        val (index, offset) = state.lazyScrollPositions[contentStateKey] ?: (0 to 0)
                        LazyListState(index, offset)
                    }
                }
            LaunchedEffect(contentStateKey, routeScrollState) {
                snapshotFlow { routeScrollState.value }.collect { state.scrollPositions[contentStateKey] = it }
            }
            LaunchedEffect(contentStateKey, routeLazyListState) {
                snapshotFlow {
                    routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                }.collect { state.lazyScrollPositions[contentStateKey] = it }
            }
            if (saveRouteState) {
                DisposableEffect(contentStateKey, routeScrollState, routeLazyListState) {
                    onDispose {
                        state.scrollPositions[contentStateKey] = routeScrollState.value
                        state.lazyScrollPositions[contentStateKey] =
                            routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
                        val tabStackContainsRoute =
                            decodeAppTabBackStacks(savedTabBackStacks)
                                .values
                                .flatten()
                                .any { it.stateKey == visibleEntry.stateKey }
                        if (!visibleRoute.isTab() &&
                            backStack.none { it.stateKey == visibleEntry.stateKey } &&
                            !tabStackContainsRoute
                        ) {
                            state.scrollPositions.remove(contentStateKey)
                            state.lazyScrollPositions.remove(contentStateKey)
                            routeLazyListStates.remove(contentStateKey)
                        }
                        routeScrollStates.remove(contentStateKey)
                    }
                }
            }
            val currentWindowHinge by windowHinge.collectAsState()
            val routeContent: @Composable () -> Unit = {
                CompositionLocalProvider(
                    LocalRouteScrollState provides routeScrollState,
                    LocalRouteLazyListState provides routeLazyListState,
                    LocalDeviceAuthenticator provides deviceAuthenticator,
                    LocalEncryptedBudgetVault provides encryptedBudgetVault,
                    LocalCrashReports provides crashReports,
                    LocalShareText provides onShareText,
                    LocalWindowHinge provides currentWindowHinge,
                ) {
                    Column(modifier = pageModifier.fillMaxSize()) {
                        if (includeChrome && shouldRenderStandaloneRouteHeader(visibleRoute)) renderHeader(visibleRoute)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            ScreenContent(
                                route = visibleRoute,
                                isForegroundRoute = visibleEntry == routeEntry,
                                state = state,
                                deviceName = deviceName,
                                navigation = navigation,
                                mobileSession = mobileSession,
                                reportsDataCache = reportsDataCache,
                                onSignIn = signIn,
                                onStartOpenIdSignIn = startOpenIdSignIn,
                                onRefreshBudgets = refreshBudgets,
                                onPrepareBudgetUnlock = prepareBudgetUnlock,
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
                                onBudgetGroupReorderModeChange = { budgetGroupReorderActive = it },
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
                            val pendingSyncOperation =
                                mobileSession.pendingWrites.firstOrNull {
                                    it.operationId == state.selectedConflictOperationId &&
                                        (it.status == "queued" || (it.status == "sending" && mobileSession.isSyncing))
                                }
                            val showSyncAction =
                                visibleRoute == AppRoute.UNCERTAIN_WRITE && pendingSyncOperation != null
                            if (showSyncAction) {
                                val syncActionCompact by
                                    remember(routeScrollState) {
                                        derivedStateOf { routeScrollState.value > 72 }
                                    }
                                val syncActionEnabled =
                                    !mobileSession.isSyncing && mobileSession.isAuthenticated
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
                            if (visibleRoute in QUICK_ADD_TABS &&
                                state.selectedBudgetId.isNotBlank() &&
                                !needsEncryptedBudgetUnlock(state, visibleRoute)
                            ) {
                                val addCompact by
                                    remember(routeScrollState, routeLazyListState) {
                                        derivedStateOf { routeScrollState.value > 72 || routeLazyListState.firstVisibleItemIndex > 0 }
                                    }
                                FloatingTransactionAction(
                                    compact = addCompact,
                                    enabled = !state.readOnlyMode,
                                    onClick = { navigation.newTransaction(null) },
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
                                )
                            }
                        }
                        if (includeChrome && visibleRoute.isTab() && !useRail) {
                            BottomNavigation(visibleRoute, navigation.selectTab)
                        }
                    }
                }
            }
            if (saveRouteState) {
                saveableStateHolder.SaveableStateProvider(contentStateKey) { routeContent() }
            } else {
                routeContent()
            }
        }
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val backTargetTab = backPreviewRoute?.route?.takeIf { it.isTab() }
        val tabShellRoute =
            route.takeIf { it.isTab() }
                ?: backTargetTab
                ?: backStack.dropLast(1).lastOrNull { it.route.isTab() }?.route
        val stationaryTabShell = tabShellRoute != null
        val shellHeaderRoute =
            when {
                backTargetTab != null -> backTargetTab
                transactionSheetActive -> transactionSheetBaseEntry?.route ?: route
                else -> route
            }
        val workspaceLayout =
            if (useRail && state.selectedBudgetId.isNotBlank()) {
                with(
                    LocalDensity.current,
                ) { state.workspace.effective(windowSize.width.toDp().value - 80f, windowSize.height.toDp().value) }
            } else {
                WorkspaceLayout.SINGLE
            }
        val workspaceTabs = navigationRoutes(state.interfaceMode)
        val workspaceActive =
            workspaceLayout != WorkspaceLayout.SINGLE &&
                backStack.all { it.route.isTab() } &&
                route in workspaceTabs &&
                !transactionSheetActive &&
                backGestureProgress == 0f
        val workspacePanes = state.workspace.panesFor(workspaceLayout.paneCount, route, workspaceTabs)
        val showRail =
            useRail &&
                tabShellRoute != null &&
                route.showsBottomNavigation() &&
                !imeVisible &&
                !transactionSheetActive
        val shortcutsEnabled = tabShellRoute != null && state.selectedBudgetId.isNotBlank()
        val rootFocus = remember { FocusRequester() }
        LaunchedEffect(shortcutsEnabled) { if (shortcutsEnabled) runCatching { rootFocus.requestFocus() } }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val shortcut = event.keyboardShortcut()?.takeIf { shortcutsEnabled } ?: return@onPreviewKeyEvent false
                        when (shortcut) {
                            KeyboardShortcut.NewTransaction -> QuickActionEvents.requestAddTransaction()
                            KeyboardShortcut.Search -> navigation.open(AppRoute.ACTIVITY_SEARCH)
                            KeyboardShortcut.Sync -> syncNow()
                            KeyboardShortcut.Back -> navigation.back()
                            is KeyboardShortcut.Tab -> navigation.selectTab(shortcut.route)
                        }
                        true
                    }.focusRequester(rootFocus)
                    .focusable()
                    .background(appBackgroundBrush(state.backgroundStyle, palette))
                    .safeDrawingPadding(),
        ) {
            if (showRail) {
                SideNavigationRail(
                    selected = requireNotNull(tabShellRoute),
                    selectTab = { tab ->
                        if (workspaceActive && tab !in workspacePanes) {
                            state.workspace = state.workspace.place(tab, workspacePanes.indexOf(route), workspacePanes)
                            selectTabRoot(tab)
                        } else if (workspaceActive) {
                            selectTabRoot(tab)
                        } else {
                            navigation.selectTab(tab)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart),
                    layouts =
                        if (state.selectedBudgetId.isNotBlank()) {
                            with(LocalDensity.current) {
                                val width = windowSize.width.toDp().value - 80f
                                val height = windowSize.height.toDp().value
                                WorkspaceLayout.entries.filter { it.fits(width, height) }
                            }
                        } else {
                            emptyList()
                        },
                    layout = workspaceLayout,
                    onLayout = { layout -> state.workspace = Workspace(layout, workspacePanes) },
                    onOpenMenu = if (state.selectedBudgetId.isNotBlank()) ({ state.menuOpen = true }) else null,
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (showRail) 80.dp else 0.dp),
            ) {
                if (!workspaceActive && shouldRenderRouteHeader(route, tabShellRoute != null)) {
                    AnimatedContent(
                        targetState = shellHeaderRoute,
                        transitionSpec = {
                            fadeIn(
                                tween(
                                    TandemMotion.TAB_TRANSITION_MILLIS,
                                    delayMillis = TandemMotion.TAB_EXIT_MILLIS,
                                    easing = TandemMotion.emphasizedEasing,
                                ),
                            ) togetherWith fadeOut(tween(TandemMotion.TAB_EXIT_MILLIS, easing = TandemMotion.exitEasing))
                        },
                        label = "tandem-header-transition",
                    ) { headerRoute -> if (!needsEncryptedBudgetUnlock(state, headerRoute)) renderHeader(headerRoute) }
                }
                val transitionInputModifier =
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down =
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                            state.pointerInputSequence += 1
                            if (routeTransitionInputBlockedState.value) down.consume()
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (routeTransitionInputBlockedState.value) {
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .then(transitionInputModifier)
                            .tandemEdgeBackGesture(backStack.size > 1 && !transactionSheetActive, handleBack, updateBackGesture),
                ) {
                    val routeBackground = appBackgroundBrush(state.backgroundStyle, palette)
                    if (workspaceActive) {
                        WorkspacePanes(workspaceLayout, workspacePanes, route, onFocus = {
                            if (it !=
                                route
                            ) {
                                selectTabRoot(it)
                            }
                        }) { paneRoute ->
                            renderHeader(paneRoute)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                renderRoute(
                                    if (paneRoute == route) routeEntry else AppRouteEntry(paneRoute, -1L - paneRoute.ordinal),
                                    Modifier.fillMaxSize(),
                                    true,
                                    false,
                                )
                            }
                        }
                    } else {
                        if (!transactionSheetActive &&
                            backGestureProgress > 0f &&
                            backPreviewRoute != null &&
                            backPreviewRoute != routeEntry
                        ) {
                            val direction = backGestureDirection
                            renderRoute(
                                backPreviewRoute!!,
                                Modifier
                                    .fillMaxSize()
                                    .clearAndSetSemantics {}
                                    .graphicsLayer {
                                        val progress = backGestureProgress
                                        translationX = -direction * size.width * TandemMotion.ROUTE_PARALLAX * (1f - progress)
                                        alpha = TandemMotion.ROUTE_COVERED_ALPHA + (1f - TandemMotion.ROUTE_COVERED_ALPHA) * progress
                                    }.background(routeBackground),
                                false,
                                !stationaryTabShell,
                            )
                        }
                        val routeSlideSpec = tween<IntOffset>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
                        val routeFadeSpec = tween<Float>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
                        AnimatedContent(
                            targetState = backgroundRouteEntry,
                            modifier = Modifier.fillMaxSize().clipToBounds(),
                            transitionSpec = {
                                when {
                                    reduceMotion ||
                                        skipNextRouteTransition ||
                                        initialState.route == AppRoute.STARTUP ||
                                        targetState.route == AppRoute.STARTUP ->
                                        EnterTransition.None togetherWith ExitTransition.None
                                    initialState.route.isTab() && targetState.route.isTab() ->
                                        (
                                            fadeIn(
                                                tween(
                                                    TandemMotion.TAB_TRANSITION_MILLIS,
                                                    delayMillis = TandemMotion.TAB_EXIT_MILLIS,
                                                    easing = TandemMotion.emphasizedEasing,
                                                ),
                                            ) +
                                                scaleIn(
                                                    tween(
                                                        TandemMotion.TAB_TRANSITION_MILLIS,
                                                        delayMillis = TandemMotion.TAB_EXIT_MILLIS,
                                                        easing = TandemMotion.emphasizedEasing,
                                                    ),
                                                    initialScale = 0.97f,
                                                )
                                        ) togetherWith
                                            fadeOut(
                                                tween(TandemMotion.TAB_EXIT_MILLIS, easing = TandemMotion.exitEasing),
                                            ) using null
                                    routeTransitionDirection > 0f ->
                                        slideInHorizontally(routeSlideSpec) { it } togetherWith
                                            (
                                                slideOutHorizontally(routeSlideSpec) { -(it * TandemMotion.ROUTE_PARALLAX).toInt() } +
                                                    fadeOut(routeFadeSpec, targetAlpha = TandemMotion.ROUTE_COVERED_ALPHA)
                                            ) using null
                                    routeTransitionDirection < 0f ->
                                        (
                                            (
                                                slideInHorizontally(routeSlideSpec) { -(it * TandemMotion.ROUTE_PARALLAX).toInt() } +
                                                    fadeIn(routeFadeSpec, initialAlpha = TandemMotion.ROUTE_COVERED_ALPHA)
                                            ) togetherWith slideOutHorizontally(routeSlideSpec) { it } using null
                                        ).apply { targetContentZIndex = -1f }
                                    else -> EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "tandem-screen-transition",
                        ) { visibleEntry ->
                            renderRoute(
                                if (transactionSheetActive) backgroundRouteEntry else visibleEntry,
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (visibleEntry == backGestureOutgoingEntry && backGestureProgress > 0f) {
                                            translationX = backGestureProgress * size.width * backGestureDirection
                                        }
                                    }.background(routeBackground),
                                true,
                                !stationaryTabShell,
                            )
                        }
                    }
                }
                if (tabShellRoute != null && !useRail) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = backgroundRouteEntry.route.showsBottomNavigation() && !imeVisible,
                        modifier = Modifier.fillMaxWidth(),
                        enter =
                            expandVertically(
                                tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS),
                            ) + tandemBottomBarEnter(reduceMotion),
                        exit =
                            shrinkVertically(tandemMotionSpec(reduceMotion, TandemMotion.ROUTE_MILLIS)) + tandemBottomBarExit(reduceMotion),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(88.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            BottomNavigation(
                                tabShellRoute,
                                navigation.selectTab,
                            )
                        }
                    }
                }
                if (transactionSheetActive) {
                    TandemTaskSheet(
                        visible = !transactionSheetClosing,
                        onDismissRequest = handleCurrentRouteBack,
                        fullScreen = true,
                        dismissOnBackPress = true,
                        onDismissed = {
                            routeTransitionDirection = 0f
                            repeat(transactionSheetPopCount.coerceAtMost(backStack.size - 1)) {
                                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            }
                            transactionSheetClosing = false
                            transactionSheetPopCount = 1
                        },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .then(transitionInputModifier)
                                .tandemEdgeBackGesture(
                                    enabled = transactionSheetActive && backStack.size > 1,
                                    onBack = handleCurrentRouteBack,
                                    onBackProgress = updateBackGesture,
                                ),
                        ) {
                            AnimatedContent(
                                targetState = routeEntry,
                                modifier = Modifier.fillMaxSize(),
                                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                                label = "tandem-task-sheet-content",
                            ) { sheetEntry ->
                                renderRoute(sheetEntry, Modifier.fillMaxSize(), true, true)
                            }
                        }
                    }
                }
            }
        }
        val undoNotice = mobileSession.transactions.undoNotice
        val undoAboveTabBar =
            !usesRailNavigation(
                with(LocalDensity.current) {
                    LocalWindowInfo.current.containerSize.width
                        .toDp()
                        .value
                },
                isLandscapeViewport(LocalWindowInfo.current.containerSize.width, LocalWindowInfo.current.containerSize.height),
            )
        LaunchedEffect(undoNotice?.id) {
            val shown = undoNotice ?: return@LaunchedEffect
            delay(UNDO_NOTICE_MILLIS)
            mobileSession.transactions.dismissUndoNotice(shown.id)
        }
        Box(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = TandemSpacing.large, end = TandemSpacing.large, bottom = if (undoAboveTabBar) 104.dp else 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = undoNotice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                val notice = undoNotice ?: return@AnimatedVisibility
                SurfaceCard(Modifier.widthIn(max = 560.dp)) {
                    InlineMessage(
                        notice.message,
                        InlineMessageKind.INFO,
                        actionLabel = "Undo".takeIf { notice.transactionIds.isNotEmpty() },
                        onAction = { coroutineScope.launch { mobileSession.transactions.undoTransactionDeletes(notice) } },
                        onDismiss = { mobileSession.transactions.dismissUndoNotice(notice.id) },
                    )
                }
            }
        }
        if (showDiscardTransactionDialog) {
            val formRoute = backStack.lastOrNull()?.route
            val isNewTransaction = formRoute == AppRoute.NEW_TRANSACTION
            val payee = state.transactionPayee.trim().takeIf { it.isNotBlank() }
            val amount =
                state.parseAmountMinorUnits(state.transactionAmount)?.let { state.formatCurrency(kotlin.math.abs(it)) }
                    ?: "the current amount"
            val transactionSummary = payee?.let { "$amount at $it" } ?: amount
            DestructiveConfirmationDialog(
                title = if (isNewTransaction) "Discard this transaction?" else "Discard changes?",
                message =
                    if (isNewTransaction) {
                        "Discard $transactionSummary? The transaction and its notes will be lost."
                    } else {
                        "Discard your changes to ${payee ?: "this transaction"} ($amount)? The current transaction will stay as it is."
                    },
                confirmLabel = "Discard",
                dismissLabel = "Keep editing",
                onDismissRequest = { showDiscardTransactionDialog = false },
                onConfirm = {
                    restoreTransactionFormBaseline(state, formRoute)
                    showDiscardTransactionDialog = false
                    transactionSheetPopCount = 1
                    transactionSheetClosing = true
                },
            )
        }
        val openFromMenu: (AppRoute) -> Unit = { target ->
            state.menuOpen = false
            if (target in navigationRoutes(state.interfaceMode) ||
                target == AppRoute.SETTINGS
            ) {
                navigation.selectTab(target)
            } else {
                navigation.open(target)
            }
        }
        AppMenu(
            visible = state.menuOpen && state.selectedBudgetId.isNotBlank(),
            state = state,
            syncStatus = mainCloudStatus,
            syncCaption =
                headerSyncCaption(
                    mainCloudStatus,
                    mobileSession.pendingWrites.map { it.status },
                    mobileSession.localSyncState?.lastSyncedAtEpochMs,
                    headerClockMs,
                ),
            items =
                appMenuItems(
                    advanced = state.interfaceMode == InterfaceMode.ADVANCED,
                    ruleCount = mobileSession.ruleRecords.size,
                    scheduleCount = liveSchedules.size,
                    payeeCount = mobileSession.payeeRecords.count { it.recordBody()?.stringValue("transfer_acct") == null },
                    conflictCount = mobileSession.pendingWrites.count { it.status == "conflict" },
                    reimbursementsOwedMinor =
                        mobileSession.planning.reimbursements
                            .filterNot { it.received }
                            .sumOf { it.amountMinor },
                    formatMoney = state::formatCurrency,
                    readOnly = state.readOnlyMode,
                    open = openFromMenu,
                    onAddTransaction = {
                        state.menuOpen = false
                        navigation.newTransaction(null)
                    },
                    onScanReceipt = {
                        state.menuOpen = false
                        state.receiptScanRequested = true
                        navigation.newTransaction(null)
                    },
                    onManageBanks = {
                        state.accountsRequestedAction = "banks"
                        openFromMenu(AppRoute.ACCOUNTS)
                    },
                    onFeedback = { uriHandler.openUri("https://github.com/Nischay-Pro/tandem/issues") },
                ),
            onClose = { state.menuOpen = false },
            onBudgetSettings = { openFromMenu(AppRoute.SETTINGS) },
            onSwitchBudget = {
                state.menuOpen = false
                navigation.open(AppRoute.SWITCH_BUDGET)
            },
            onSyncCenter = { openFromMenu(AppRoute.SYNC_CENTER) },
            onAbout = { openFromMenu(AppRoute.ABOUT) },
            onLicenses = { openFromMenu(AppRoute.MERCHANT_ICON_LICENSES) },
        )
        BudgetSwitchSplash(
            state.selectedBudget.takeIf { state.modeSwitchTarget == null && state.budgetSwitchSplashId != null },
            state.modeSwitchTarget,
        )
        AppLockGate(
            state = state,
            mobileSession = mobileSession,
            route = backStack.lastOrNull()?.route ?: AppRoute.BUDGET,
            authenticator = deviceAuthenticator,
            lockState = appLockState,
            appInForeground = isAppInForeground,
            onChooseBudget = { navigation.open(AppRoute.SWITCH_BUDGET) },
            vault = encryptedBudgetVault,
            backTargetRoute = backPreviewRoute?.route,
            backProgress = backGestureProgress,
            onUnlocked = {
                if (state.pendingSetupAfterUnlock) {
                    state.pendingSetupAfterUnlock = false
                    routeTransitionDirection = 0f
                    savedTabBackStacks = ""
                    backStack.clear()
                    backStack.add(newRouteEntry(AppRoute.BUDGET_CHOOSER))
                    backStack.add(newRouteEntry(nextSetupRoute(state, notificationPermissionEnabled)))
                }
            },
        )
    }
}

private val QUICK_ADD_TABS = setOf(AppRoute.HOME, AppRoute.ACTIVITY, AppRoute.ACCOUNTS)

internal fun AppRoute.isTab(): Boolean =
    this == AppRoute.HOME ||
        this == AppRoute.BUDGET ||
        this == AppRoute.ACCOUNTS ||
        this == AppRoute.ACTIVITY ||
        this == AppRoute.REPORTS ||
        this == AppRoute.SETTINGS

internal fun AppRoute.isTransactionForm(): Boolean = this == AppRoute.NEW_TRANSACTION || this == AppRoute.EDIT_TRANSACTION

internal fun AppRoute.showsBottomNavigation(): Boolean =
    when (this) {
        AppRoute.STARTUP,
        AppRoute.WELCOME,
        AppRoute.SWITCH_BUDGET,
        AppRoute.SERVER_ADDRESS,
        AppRoute.SIGN_IN,
        AppRoute.BUDGET_CHOOSER,
        AppRoute.BUDGET_UNLOCK,
        AppRoute.FIRST_SYNC,
        AppRoute.PERSONALIZE,
        AppRoute.SETUP_COMPLETE,
        AppRoute.NEW_TRANSACTION,
        AppRoute.EDIT_TRANSACTION,
        AppRoute.CHOOSE_PAYEE,
        AppRoute.CHOOSE_CATEGORY,
        AppRoute.RETIREMENT_SETUP,
        -> false
        else -> true
    }

internal fun AppRoute.isReportDetail(): Boolean = this == AppRoute.REPORT_DETAIL

/** How often an open budget pulls changes from the server while the app is on screen. */
internal const val UNDO_NOTICE_MILLIS = 6_000L

internal const val BUDGET_PERIODIC_SYNC_INTERVAL_MS = 60_000L

internal fun <T> isBackPreviewStale(
    preview: T?,
    gestureOrigin: T?,
    current: T,
): Boolean = preview != null && gestureOrigin != current && preview != current

private const val SCREENSHOT_SETTLE_MILLIS = 1_500L
private const val BUDGET_SWITCH_SPLASH_MIN_MS = 700L
private const val BUDGET_SWITCH_SPLASH_MAX_MS = 5_000L
private const val BUDGET_SWITCH_SETTLE_MS = 250L
private const val MODE_SWITCH_COVER_MS = 220L
