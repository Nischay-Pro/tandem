@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.BottomNavigation
import app.tandemforactual.ui.app.FloatingActionClearance
import app.tandemforactual.ui.app.FloatingSyncAction
import app.tandemforactual.ui.app.FloatingTransactionAction
import app.tandemforactual.ui.app.LocalWindowHinge
import app.tandemforactual.ui.app.WindowHinge
import app.tandemforactual.ui.core.security.LocalBackgroundBudgetKeyStore
import app.tandemforactual.ui.core.security.LocalDeviceAuthenticator
import app.tandemforactual.ui.core.security.LocalEncryptedBudgetVault
import app.tandemforactual.ui.core.session.LocalCrashReports
import app.tandemforactual.ui.core.session.LocalShareText
import app.tandemforactual.ui.core.session.rememberBankIcon
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.decodeAppTabBackStacks
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.core.state.routeContentStateKey
import app.tandemforactual.ui.core.state.shouldRenderStandaloneRouteHeader
import app.tandemforactual.ui.core.ui.accountIconFor
import app.tandemforactual.ui.design.rows.LocalRowLogos
import app.tandemforactual.ui.design.rows.RowLogos
import app.tandemforactual.ui.design.templates.LocalFloatingActionClearance
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock

/** One route page with its remembered scroll position, optionally kept in the saveable state holder. */
@Composable
internal fun ShellRoute(
    chrome: ShellChromeState,
    visibleEntry: AppRouteEntry,
    pageModifier: Modifier,
    saveRouteState: Boolean,
    includeChrome: Boolean,
) {
    val ctx = chrome.ctx
    val state = ctx.state
    val shell = ctx.shell
    if (needsEncryptedBudgetUnlock(state, visibleEntry.route)) {
        Box(pageModifier.fillMaxSize().background(tandemPalette.background))
        return
    }
    val contentStateKey = routeContentStateKey(visibleEntry, state.selectedBudgetId)
    val routeScrollState =
        remember(contentStateKey) {
            shell.routeScrollStates.getOrPut(contentStateKey) {
                ScrollState(state.scrollPositions[contentStateKey] ?: 0)
            }
        }
    val routeLazyListState =
        remember(contentStateKey) {
            shell.routeLazyListStates.getOrPut(contentStateKey) {
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
    if (saveRouteState) RouteScrollCleanup(ctx, visibleEntry, contentStateKey, routeScrollState, routeLazyListState)
    val currentWindowHinge by ctx.env.windowHinge.collectAsState()
    val routeContent: @Composable () -> Unit = {
        ShellRouteContent(chrome, visibleEntry, pageModifier, includeChrome, routeScrollState, routeLazyListState, currentWindowHinge)
    }
    if (saveRouteState) {
        ctx.saveableStateHolder.SaveableStateProvider(contentStateKey) { routeContent() }
    } else {
        routeContent()
    }
}

@Composable
private fun RouteScrollCleanup(
    ctx: ShellContext,
    visibleEntry: AppRouteEntry,
    contentStateKey: String,
    routeScrollState: ScrollState,
    routeLazyListState: LazyListState,
) {
    val state = ctx.state
    val shell = ctx.shell
    DisposableEffect(contentStateKey, routeScrollState, routeLazyListState) {
        onDispose {
            state.scrollPositions[contentStateKey] = routeScrollState.value
            state.lazyScrollPositions[contentStateKey] =
                routeLazyListState.firstVisibleItemIndex to routeLazyListState.firstVisibleItemScrollOffset
            val tabStackContainsRoute =
                decodeAppTabBackStacks(shell.savedTabBackStacks)
                    .values
                    .flatten()
                    .any { it.stateKey == visibleEntry.stateKey }
            if (!visibleEntry.route.isTab() &&
                shell.backStack.none { it.stateKey == visibleEntry.stateKey } &&
                !tabStackContainsRoute
            ) {
                state.scrollPositions.remove(contentStateKey)
                state.lazyScrollPositions.remove(contentStateKey)
                shell.routeLazyListStates.remove(contentStateKey)
            }
            shell.routeScrollStates.remove(contentStateKey)
        }
    }
}

@Composable
private fun ShellRouteContent(
    chrome: ShellChromeState,
    visibleEntry: AppRouteEntry,
    pageModifier: Modifier,
    includeChrome: Boolean,
    routeScrollState: ScrollState,
    routeLazyListState: LazyListState,
    currentWindowHinge: WindowHinge?,
) {
    val ctx = chrome.ctx
    val env = ctx.env
    val visibleRoute = visibleEntry.route
    val matcher = ctx.shell.merchantIconMatcher
    val bankLinks = ctx.mobileSession.banking.bankLinks
    val bankIds = remember(bankLinks) { bankLinks.mapNotNull { link -> link.bankId?.let { link.name to it } }.toMap() }
    val downloadBankIcons = ctx.state.bankIconsEnabled
    val pickedIcons = ctx.mobileSession.planning.accountIcons
    val liveAccounts = ctx.shell.liveAccounts
    val iconsByName =
        remember(pickedIcons, liveAccounts) {
            liveAccounts.mapNotNull { account -> accountIconFor(pickedIcons[account.id])?.let { account.name to it } }.toMap()
        }
    val logos =
        remember(matcher, bankIds, downloadBankIcons, iconsByName) {
            RowLogos(
                payee = { matcher?.iconFor(it) },
                account = { matcher?.accountIconFor(it) },
                bankIcon = { name -> rememberBankIcon(bankIds[name], downloadBankIcons) },
                bySlug = { matcher?.iconForSlug(it) },
                chosenIcon = { iconsByName[it] },
            )
        }
    CompositionLocalProvider(
        LocalRouteScrollState provides routeScrollState,
        LocalRouteLazyListState provides routeLazyListState,
        LocalDeviceAuthenticator provides env.deviceAuthenticator,
        LocalEncryptedBudgetVault provides env.encryptedBudgetVault,
        LocalBackgroundBudgetKeyStore provides env.backgroundBudgetKeyStore,
        LocalCrashReports provides env.crashReports,
        LocalShareText provides env.onShareText,
        LocalWindowHinge provides currentWindowHinge,
        LocalRowLogos provides logos,
    ) {
        Column(modifier = pageModifier.fillMaxSize()) {
            if (includeChrome && shouldRenderStandaloneRouteHeader(visibleRoute)) ShellHeader(chrome, visibleRoute)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val actions = routeFloatingActions(ctx, visibleRoute)
                CompositionLocalProvider(
                    LocalFloatingActionClearance provides if (actions.any) FloatingActionClearance else 0.dp,
                ) {
                    ShellScreen(ctx, visibleEntry)
                }
                RouteFloatingActions(ctx, actions, routeScrollState, routeLazyListState)
            }
            if (includeChrome && visibleRoute.isTab() && !chrome.useRail) {
                BottomNavigation(visibleRoute, ctx.navigation.selectTab)
            }
        }
    }
}

/** Which floating buttons the route shows over its content. */
private data class FloatingActionsShown(
    val sync: Boolean,
    val add: Boolean,
) {
    val any: Boolean get() = sync || add
}

private fun routeFloatingActions(
    ctx: ShellContext,
    visibleRoute: AppRoute,
): FloatingActionsShown {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    val pendingSyncOperation =
        mobileSession.pendingWrites.firstOrNull {
            it.operationId == state.selectedConflictOperationId &&
                (it.status == "queued" || (it.status == "sending" && mobileSession.isSyncing))
        }
    return FloatingActionsShown(
        sync = visibleRoute == AppRoute.UNCERTAIN_WRITE && pendingSyncOperation != null,
        add =
            visibleRoute in QUICK_ADD_TABS &&
                state.selectedBudgetId.isNotBlank() &&
                !needsEncryptedBudgetUnlock(state, visibleRoute),
    )
}

@Composable
private fun BoxScope.RouteFloatingActions(
    ctx: ShellContext,
    shown: FloatingActionsShown,
    routeScrollState: ScrollState,
    routeLazyListState: LazyListState,
) {
    val state = ctx.state
    val mobileSession = ctx.mobileSession
    if (shown.sync) {
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
            onClick = ctx.budgetActions::syncNow,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
        )
    }
    if (shown.add) {
        val addCompact by
            remember(routeScrollState, routeLazyListState) {
                derivedStateOf { routeScrollState.value > 72 || routeLazyListState.firstVisibleItemIndex > 0 }
            }
        FloatingTransactionAction(
            compact = addCompact,
            enabled = !state.readOnlyMode,
            onClick = { ctx.navigation.newTransaction(null) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = TandemSpacing.large, bottom = TandemSpacing.large),
        )
    }
}

private val QUICK_ADD_TABS = setOf(AppRoute.HOME, AppRoute.ACTIVITY, AppRoute.ACCOUNTS)
