@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import app.tandemforactual.data.store.AppPreferencesStore
import app.tandemforactual.data.store.bankSyncFailureAlert
import app.tandemforactual.data.store.uncategorizedAlert
import app.tandemforactual.ui.app.QuickAction
import app.tandemforactual.ui.app.QuickActionEvents
import app.tandemforactual.ui.app.isBackPreviewStale
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.core.format.parseSharedTransactionText
import app.tandemforactual.ui.core.format.platformMinuteOfDay
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.processQueuedBudgetReorders
import app.tandemforactual.ui.core.session.scheduleOpenIdProofExpiry
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.core.state.loadMerchantIconCatalog
import app.tandemforactual.ui.core.state.loadMerchantIconMatcherPack
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock
import app.tandemforactual.ui.feature.transactions.receipt.fillNewTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Replays queued budget and schedule work for a new session and posts bank sync failure alerts. */
@Composable
internal fun SessionQueueEffects(
    env: ShellEnvironment,
    state: AppState,
    mobileSession: TandemMobileSession,
    appPreferencesStore: AppPreferencesStore?,
) {
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
    LaunchedEffect(mobileSession.banking.bankLinks) {
        val links = mobileSession.banking.bankLinks.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        val store = appPreferencesStore ?: return@LaunchedEffect
        val post = env.postLocalAlert ?: return@LaunchedEffect
        val failing = links.filter { it.failure != null }.map { it.name }
        bankSyncFailureAlert(store, state.selectedBudgetId, failing, state.notificationPolicy, platformMinuteOfDay())?.let(post)
    }
    LaunchedEffect(mobileSession.transactionRecords) {
        val repository = mobileSession.repository ?: return@LaunchedEffect
        val store = appPreferencesStore ?: return@LaunchedEffect
        val post = env.postLocalAlert ?: return@LaunchedEffect
        if (state.selectedBudgetId.isBlank() || mobileSession.transactionRecords.isEmpty()) return@LaunchedEffect
        val policy = state.notificationPolicy
        uncategorizedAlert(repository, store, state.selectedBudgetId, platformTodayIsoDate(), policy, platformMinuteOfDay()) {
            state.formatCurrency(it)
        }?.let(post)
    }
}

/** Runs deferred back presses and opens the screens that quick actions and shortcuts ask for. */
@Composable
internal fun NavigationEffects(ctx: ShellContext) {
    val state = ctx.state
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    val route = ctx.route
    LaunchedEffect(shell.routeTransitionInputBlocked, shell.backButtonTransitionRunning, shell.pendingBackAfterRouteTransition) {
        if (!shell.routeTransitionInputBlocked && !shell.backButtonTransitionRunning && shell.pendingBackAfterRouteTransition) {
            shell.pendingBackAfterRouteTransition = false
            ctx.navigator.handleBack()
        }
    }
    SideEffect { mobileSession.feedback.setBudgetActionRoute(ctx.routeEntry) }
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
        shell.liveAccounts.isEmpty(),
    ) {
        val budgetReady =
            state.selectedBudgetId.isNotBlank() &&
                !state.readOnlyMode &&
                route.isTab() &&
                !needsEncryptedBudgetUnlock(state, route) &&
                shell.liveAccounts.any { !it.offBudget }
        if (!quickAddRequested || !budgetReady) return@LaunchedEffect
        delay(TandemMotion.ROUTE_MILLIS * 2L)
        if (!shell.routeTransitionInputBlocked && QuickActionEvents.consumeAddTransaction()) {
            if (QuickActionEvents.consumeReceiptScan()) state.receiptScanRequested = true
            QuickActionEvents.consumeSharedReceiptPhoto()?.let { state.pendingReceiptPhoto = it }
            ctx.navigator.newTransaction(QuickActionEvents.consumeRequestedAccount())
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
            QuickActionEvents.consumeQuickAddDraft()?.let { draft ->
                val fractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2
                fillNewTransaction(state, mobileSession, draft.amountMinor(fractionDigits), draft.payee)
            }
        }
    }
    QuickDestinationEffect(ctx)
}

@Composable
private fun QuickDestinationEffect(ctx: ShellContext) {
    val state = ctx.state
    val route = ctx.route
    val quickDestination by QuickActionEvents.destinationRequested.collectAsState()
    LaunchedEffect(quickDestination, route, state.selectedBudgetId, state.encryptedPassword.isBlank()) {
        val budgetReady = state.selectedBudgetId.isNotBlank() && route.isTab() && !needsEncryptedBudgetUnlock(state, route)
        if (quickDestination == null || !budgetReady) return@LaunchedEffect
        delay(TandemMotion.ROUTE_MILLIS * 2L)
        if (ctx.shell.routeTransitionInputBlocked) return@LaunchedEffect
        val target =
            when (QuickActionEvents.consumeDestination()) {
                QuickAction.BUDGET_CHECKUP -> AppRoute.DATA_HEALTH
                QuickAction.ACCOUNTS -> AppRoute.ACCOUNTS
                QuickAction.REVIEW_TRANSACTIONS -> AppRoute.REVIEW_TRANSACTIONS
                else -> null
            } ?: return@LaunchedEffect
        state.menuOpen = false
        if (target == AppRoute.ACCOUNTS) QuickActionEvents.consumeSharedStatement()?.let { state.pendingStatementFile = it }
        if (target in navigationRoutes(state.interfaceMode)) ctx.navigation.selectTab(target) else ctx.navigation.open(target)
    }
}

/** Loads merchant icons on demand and settles the back preview once a route change lands. */
@Composable
internal fun RouteEntryEffects(ctx: ShellContext) {
    val shell = ctx.shell
    val mobileSession = ctx.mobileSession
    val routeEntry = ctx.routeEntry
    val route = ctx.route
    MerchantIconEffects(shell, mobileSession, route)
    LaunchedEffect(routeEntry) {
        if (route != AppRoute.STARTUP) ctx.env.onStartupComplete?.invoke()
        if (shell.skipNextRouteTransition) {
            shell.skipNextRouteTransition = false
            shell.backGestureCommitPending = false
            if (shell.backPreviewRoute == routeEntry) {
                // Keep the interactive preview in place until AnimatedContent has
                // promoted this same route entry to its foreground destination.
                ctx.coroutineScope.launch {
                    withFrameNanos { }
                    if (shell.backPreviewRoute == routeEntry) {
                        shell.backGestureProgress = 0f
                        shell.backGestureOutgoingEntry = null
                        shell.backPreviewRoute = null
                    }
                }
            } else {
                shell.backGestureProgress = 0f
                shell.backGestureOutgoingEntry = null
                shell.backPreviewRoute = null
            }
        }
    }
    LaunchedEffect(routeEntry) {
        if (isBackPreviewStale(shell.backPreviewRoute, shell.backGestureOutgoingEntry, routeEntry)) {
            shell.backGestureCancelJob?.cancel()
            shell.backGestureCancelJob = null
            shell.backGestureCommitPending = false
            shell.backButtonTransitionRunning = false
            shell.backGestureProgress = 0f
            shell.backGestureOutgoingEntry = null
            shell.backPreviewRoute = null
        }
    }
}

@Composable
private fun MerchantIconEffects(
    shell: AppShellState,
    mobileSession: TandemMobileSession,
    route: AppRoute,
) {
    val merchantMatcherNeeded = mobileSession.transactionRecords.isNotEmpty() || mobileSession.accountRecords.isNotEmpty()
    LaunchedEffect(mobileSession, merchantMatcherNeeded) {
        if (shell.merchantIconMatcher == null && shell.merchantIconMatcherLoadError == null && merchantMatcherNeeded) {
            try {
                shell.merchantIconMatcher = withContext(Dispatchers.Default) { loadMerchantIconMatcherPack() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                shell.merchantIconMatcherLoadError = "Couldn’t load merchant icons."
            }
        }
    }
    val merchantCatalogNeeded = route == AppRoute.MERCHANT_ICON_LICENSES
    LaunchedEffect(mobileSession, merchantCatalogNeeded) {
        if (shell.merchantIconCatalog == null && shell.merchantIconCatalogLoadError == null && merchantCatalogNeeded) {
            try {
                shell.merchantIconCatalog = withContext(Dispatchers.Default) { loadMerchantIconCatalog() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                shell.merchantIconCatalogLoadError = "Couldn’t load icon sources."
            }
        }
    }
}
