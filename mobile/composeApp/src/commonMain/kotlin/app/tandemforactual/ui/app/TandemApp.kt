@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
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
import app.tandemforactual.data.database.ActualBudgetFiles
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.LocalAlert
import app.tandemforactual.ui.app.shell.AppSettingsEffects
import app.tandemforactual.ui.app.shell.BudgetScopedShellState
import app.tandemforactual.ui.app.shell.BudgetSessionEffects
import app.tandemforactual.ui.app.shell.ForegroundEffects
import app.tandemforactual.ui.app.shell.LiveBudgetMonthEffect
import app.tandemforactual.ui.app.shell.LiveDataEffects
import app.tandemforactual.ui.app.shell.NavigationEffects
import app.tandemforactual.ui.app.shell.OpenIdReturnEffect
import app.tandemforactual.ui.app.shell.PeriodicSyncEffects
import app.tandemforactual.ui.app.shell.RouteEntryEffects
import app.tandemforactual.ui.app.shell.RouteSyncEffect
import app.tandemforactual.ui.app.shell.ScreenshotScriptEffect
import app.tandemforactual.ui.app.shell.SessionQueueEffects
import app.tandemforactual.ui.app.shell.ShellContext
import app.tandemforactual.ui.app.shell.ShellEnvironment
import app.tandemforactual.ui.app.shell.ShellNavigator
import app.tandemforactual.ui.app.shell.StartupEffects
import app.tandemforactual.ui.app.shell.rememberAppShellState
import app.tandemforactual.ui.app.shell.rememberAutomaticSync
import app.tandemforactual.ui.app.shell.rememberShellSession
import app.tandemforactual.ui.app.shell.updateBackGesture
import app.tandemforactual.ui.core.format.AppLanguageController
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.LocalCrashReports
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.budget.budgetMutationsWaitingForConnection
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.session.forgetDevice
import app.tandemforactual.ui.core.session.forgetDeviceLocally
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.undoTransactionDeletes
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.decodeAppTabBackStacks
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.headerSyncCaption
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.resolveMainCloudStatus
import app.tandemforactual.ui.core.state.restoreTransactionFormBaseline
import app.tandemforactual.ui.core.state.routeContentStateKey
import app.tandemforactual.ui.core.state.shouldRenderRouteHeader
import app.tandemforactual.ui.core.state.shouldRenderStandaloneRouteHeader
import app.tandemforactual.ui.core.state.stringValue
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
import app.tandemforactual.ui.feature.auth.AppLockGate
import app.tandemforactual.ui.feature.auth.DeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalDeviceAuthenticator
import app.tandemforactual.ui.feature.auth.LocalEncryptedBudgetVault
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock
import app.tandemforactual.ui.feature.onboarding.nextSetupRoute
import app.tandemforactual.ui.feature.sync.conflictPayeeTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

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
    val env =
        ShellEnvironment(
            deviceName,
            database,
            deviceCredentialStore,
            nowEpochMillis,
            allowInsecurePrivateNetwork,
            onBackgroundSyncSettingsChanged,
            onSystemAppearanceChanged,
            onRequestNotificationPermission,
            onOpenNotificationSettings,
            notificationPermissionEnabled,
            onToggleNotificationPermission,
            onStartupComplete,
            appResumeEvents,
            appInForeground,
            networkRestoredEvents,
            deviceAuthenticator,
            encryptedBudgetVault,
            currentNetwork,
            crashReports,
            onShareText,
            windowHinge,
            screenshotScript,
            actualBudgetFiles,
            planningKeyStore,
            postLocalAlert,
        )
    val shell = rememberAppShellState(nowEpochMillis)
    val systemReduceMotion = key(shell.accessibilitySettingsRevision) { SystemReduceMotionEnabled() }
    val state = rememberSaveable(saver = AppState.Saver) { AppState() }
    val saveableStateHolder = rememberSaveableStateHolder()
    val budgetScoped = remember(state.selectedBudgetId) { BudgetScopedShellState() }
    SideEffect { state.systemReduceMotionEnabled = systemReduceMotion }
    val reduceMotion = state.reduceMotionOverride ?: systemReduceMotion
    val mobileSession = rememberShellSession(env)
    val appPreferencesStore = remember(database) { database?.let(::AppPreferencesStore) }
    SessionQueueEffects(env, state, mobileSession, appPreferencesStore)
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val navigator = ShellNavigator(shell, state, coroutineScope, reduceMotion)
    StartupEffects(env, state, shell, navigator, mobileSession)
    AppSettingsEffects(env, state, mobileSession, appPreferencesStore)
    val ctx =
        ShellContext(
            env,
            state,
            shell,
            budgetScoped,
            mobileSession,
            navigator,
            saveableStateHolder,
            uriHandler,
            LocalHapticFeedback.current,
        )
    NavigationEffects(ctx)
    RouteEntryEffects(ctx)
    val automaticSyncRequest = rememberAutomaticSync(ctx)
    BudgetSessionEffects(ctx)
    RouteSyncEffect(ctx, automaticSyncRequest)
    val isAppInForeground by appInForeground.collectAsState()
    ForegroundEffects(ctx, isAppInForeground)
    PeriodicSyncEffects(ctx, isAppInForeground)
    LiveDataEffects(ctx)
    LiveBudgetMonthEffect(ctx)
    OpenIdReturnEffect(state, mobileSession, ctx.navigation)
    if (screenshotScript != null) ScreenshotScriptEffect(ctx, screenshotScript)
    var activitySelectionTotalMinor by budgetScoped::activitySelectionTotalMinor
    var budgetGroupReorderActive by budgetScoped::budgetGroupReorderActive
    val routeScrollStates = shell.routeScrollStates
    val routeLazyListStates = shell.routeLazyListStates
    val reportsDataCache = shell.reportsDataCache
    val activitySortCache = shell.activitySortCache
    val merchantIconMatcher = shell.merchantIconMatcher
    val merchantIconMatcherLoadError = shell.merchantIconMatcherLoadError
    val merchantIconCatalog = shell.merchantIconCatalog
    val merchantIconCatalogLoadError = shell.merchantIconCatalogLoadError
    var syncRetryCounter by shell::syncRetryCounter
    var routeTransitionDirection by shell::routeTransitionDirection
    val routeTransitionInputBlockedState = rememberUpdatedState(shell.routeTransitionInputBlocked)
    val backGestureProgress by shell::backGestureProgress
    val backGestureDirection by shell::backGestureDirection
    val backGestureOutgoingEntry by shell::backGestureOutgoingEntry
    val backPreviewRoute by shell::backPreviewRoute
    val skipNextRouteTransition by shell::skipNextRouteTransition
    var showDiscardTransactionDialog by shell::showDiscardTransactionDialog
    var transactionSheetClosing by shell::transactionSheetClosing
    var transactionSheetPopCount by shell::transactionSheetPopCount
    val backStack = shell.backStack
    var savedTabBackStacks by shell::savedTabBackStacks
    val newRouteEntry: (AppRoute) -> AppRouteEntry = navigator::newRouteEntry
    val selectTabRoot: (AppRoute) -> Unit = navigator::selectTabRoot
    val handleBack: () -> Unit = navigator::handleBack
    val handleCurrentRouteBack: () -> Unit = navigator::handleCurrentRouteBack
    val navigation = ctx.navigation
    val routeEntry = ctx.routeEntry
    val route = ctx.route
    val transactionSheetActive = ctx.transactionSheetActive
    val transactionSheetBaseEntry = ctx.transactionSheetBaseEntry
    val backgroundRouteEntry = ctx.backgroundRouteEntry
    val liveAccounts by shell::liveAccounts
    val liveSchedules by shell::liveSchedules
    val liveTransactions by shell::liveTransactions
    val liveActivityTransactions by shell::liveActivityTransactions
    val liveBudgetMonth by shell::liveBudgetMonth
    val appLockState = shell.appLockState
    val signIn: () -> Unit = ctx.signInActions::signIn
    val startOpenIdSignIn: () -> Unit = ctx.signInActions::startOpenIdSignIn
    val refreshBudgets: () -> Unit = ctx.signInActions::refreshBudgets
    val openSelectedBudget: (BudgetCatalogItem) -> Unit = ctx.signInActions::openSelectedBudget
    val prepareBudgetUnlock: (BudgetCatalogItem) -> Unit = ctx.signInActions::prepareBudgetUnlock
    val budgetActions = ctx.budgetActions
    val syncNow: () -> Unit = budgetActions::syncNow
    val saveTransaction: (String?, (Boolean) -> Unit) -> Unit = budgetActions::saveTransaction
    val deleteTransaction: ((Boolean) -> Unit) -> Unit = budgetActions::deleteTransaction
    val createTransaction: (String?) -> Unit = budgetActions::createTransaction
    val resolveConflict: (String, Boolean, String) -> Unit = budgetActions::resolveConflict
    val discardWrite: (String) -> Unit = budgetActions::discardWrite
    val retryUncertainWrite: (String) -> Unit = budgetActions::retryUncertainWrite
    val checkUncertainWriteStatus: (String) -> Unit = budgetActions::checkUncertainWriteStatus
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
        val updateBackGesture: (Float, Float) -> Unit = navigator::updateBackGesture
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
