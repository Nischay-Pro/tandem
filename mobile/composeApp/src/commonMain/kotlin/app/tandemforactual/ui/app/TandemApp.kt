@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
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
import app.tandemforactual.ui.app.shell.ShellChromeState
import app.tandemforactual.ui.app.shell.ShellContext
import app.tandemforactual.ui.app.shell.ShellEnvironment
import app.tandemforactual.ui.app.shell.ShellNavigator
import app.tandemforactual.ui.app.shell.ShellScaffold
import app.tandemforactual.ui.app.shell.StartupEffects
import app.tandemforactual.ui.app.shell.mainCloudStatus
import app.tandemforactual.ui.app.shell.rememberAppShellState
import app.tandemforactual.ui.app.shell.rememberAutomaticSync
import app.tandemforactual.ui.app.shell.rememberHeaderClock
import app.tandemforactual.ui.app.shell.rememberShellSession
import app.tandemforactual.ui.app.shell.updateBackGesture
import app.tandemforactual.ui.core.format.AppLanguageController
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.session.CrashReports
import app.tandemforactual.ui.core.session.DeviceCredentialStore
import app.tandemforactual.ui.core.session.EncryptedBudgetVault
import app.tandemforactual.ui.core.session.NetworkKind
import app.tandemforactual.ui.core.session.SyncNetwork
import app.tandemforactual.ui.core.session.transactions.createTransaction
import app.tandemforactual.ui.core.session.transactions.undoTransactionDeletes
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.InterfaceMode
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.headerSyncCaption
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.restoreTransactionFormBaseline
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.SystemReduceMotionEnabled
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemSystemBars
import app.tandemforactual.ui.design.theme.TandemTheme
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.auth.AppLockGate
import app.tandemforactual.ui.feature.auth.DeviceAuthenticator
import app.tandemforactual.ui.feature.onboarding.nextSetupRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        TandemBackHandler(
            enabled = backStack.size > 1 && !transactionSheetActive,
            onBack = handleCurrentRouteBack,
            onBackProgress = navigator::updateBackGesture,
        )
        val headerClock = rememberHeaderClock(ctx)
        val chrome = ShellChromeState(ctx, windowSize, isLandscape, useRail, mainCloudStatus(ctx), headerClock)
        ShellScaffold(chrome)
        val mainCloudStatus = chrome.mainCloudStatus
        val headerClockMs by headerClock
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
