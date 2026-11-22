@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.BottomNavigation
import app.tandemforactual.ui.app.KeyboardShortcut
import app.tandemforactual.ui.app.QuickActionEvents
import app.tandemforactual.ui.app.SideNavigationRail
import app.tandemforactual.ui.app.Workspace
import app.tandemforactual.ui.app.WorkspaceLayout
import app.tandemforactual.ui.app.isTab
import app.tandemforactual.ui.app.keyboardShortcut
import app.tandemforactual.ui.app.navigationRoutes
import app.tandemforactual.ui.app.showsBottomNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.shouldRenderRouteHeader
import app.tandemforactual.ui.design.templates.appBackgroundBrush
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.tandemBottomBarEnter
import app.tandemforactual.ui.design.theme.tandemBottomBarExit
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.auth.needsEncryptedBudgetUnlock

/** Which tab shell, header, rail and workspace panes the scaffold shows in this composition. */
internal class ShellLayout(
    val tabShellRoute: AppRoute?,
    val shellHeaderRoute: AppRoute,
    val workspaceLayout: WorkspaceLayout,
    val workspaceActive: Boolean,
    val workspacePanes: List<AppRoute>,
    val showRail: Boolean,
    val imeVisible: Boolean,
) {
    val stationaryTabShell = tabShellRoute != null
}

/** The window-filling scaffold: keyboard shortcuts, the side rail, and the header, routes and bottom bar column. */
@Composable
internal fun ShellScaffold(chrome: ShellChromeState) {
    val ctx = chrome.ctx
    val state = ctx.state
    val layout = shellLayout(chrome)
    val shortcutsEnabled = layout.tabShellRoute != null && state.selectedBudgetId.isNotBlank()
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
                        KeyboardShortcut.Search -> ctx.navigation.open(AppRoute.ACTIVITY_SEARCH)
                        KeyboardShortcut.Sync -> ctx.budgetActions.syncNow()
                        KeyboardShortcut.Back -> ctx.navigation.back()
                        is KeyboardShortcut.Tab -> ctx.navigation.selectTab(shortcut.route)
                    }
                    true
                }.focusRequester(rootFocus)
                .focusable()
                .background(appBackgroundBrush(state.backgroundStyle, tandemPalette))
                .safeDrawingPadding(),
    ) {
        if (layout.showRail) ShellRail(chrome, layout, Modifier.align(Alignment.CenterStart))
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = if (layout.showRail) 80.dp else 0.dp),
        ) {
            if (!layout.workspaceActive && shouldRenderRouteHeader(ctx.route, layout.tabShellRoute != null)) {
                AnimatedContent(
                    targetState = layout.shellHeaderRoute,
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
                ) { headerRoute -> if (!needsEncryptedBudgetUnlock(state, headerRoute)) ShellHeader(chrome, headerRoute) }
            }
            ShellRouteArea(chrome, layout)
        }
    }
}

@Composable
private fun shellLayout(chrome: ShellChromeState): ShellLayout {
    val ctx = chrome.ctx
    val state = ctx.state
    val route = ctx.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val backTargetTab =
        ctx.shell.backPreviewRoute
            ?.route
            ?.takeIf { it.isTab() }
    val tabShellRoute =
        route.takeIf { it.isTab() }
            ?: backTargetTab
            ?: ctx.backStack
                .dropLast(1)
                .lastOrNull { it.route.isTab() }
                ?.route
    val shellHeaderRoute =
        when {
            backTargetTab != null -> backTargetTab
            ctx.transactionSheetActive -> ctx.transactionSheetBaseEntry?.route ?: route
            else -> route
        }
    val workspaceLayout =
        if (chrome.useRail && state.selectedBudgetId.isNotBlank()) {
            with(
                LocalDensity.current,
            ) {
                state.workspace.effective(
                    chrome.windowSize.width
                        .toDp()
                        .value - 80f,
                    chrome.windowSize.height
                        .toDp()
                        .value,
                )
            }
        } else {
            WorkspaceLayout.SINGLE
        }
    val workspaceTabs = navigationRoutes(state.interfaceMode)
    val workspaceActive =
        workspaceLayout != WorkspaceLayout.SINGLE &&
            ctx.backStack.all { it.route.isTab() } &&
            route in workspaceTabs &&
            !ctx.transactionSheetActive &&
            ctx.shell.backGestureProgress == 0f
    val workspacePanes = state.workspace.panesFor(workspaceLayout.paneCount, route, workspaceTabs)
    val showRail =
        chrome.useRail &&
            tabShellRoute != null &&
            route.showsBottomNavigation() &&
            !imeVisible &&
            !ctx.transactionSheetActive
    return ShellLayout(tabShellRoute, shellHeaderRoute, workspaceLayout, workspaceActive, workspacePanes, showRail, imeVisible)
}

@Composable
private fun ShellRail(
    chrome: ShellChromeState,
    layout: ShellLayout,
    modifier: Modifier,
) {
    val ctx = chrome.ctx
    val state = ctx.state
    val workspacePanes = layout.workspacePanes
    SideNavigationRail(
        selected = requireNotNull(layout.tabShellRoute),
        selectTab = { tab ->
            if (layout.workspaceActive && tab !in workspacePanes) {
                state.workspace = state.workspace.place(tab, workspacePanes.indexOf(ctx.route), workspacePanes)
                ctx.navigator.selectTabRoot(tab)
            } else if (layout.workspaceActive) {
                ctx.navigator.selectTabRoot(tab)
            } else {
                ctx.navigation.selectTab(tab)
            }
        },
        modifier = modifier,
        layouts =
            if (state.selectedBudgetId.isNotBlank()) {
                with(LocalDensity.current) {
                    val width =
                        chrome.windowSize.width
                            .toDp()
                            .value - 80f
                    val height =
                        chrome.windowSize.height
                            .toDp()
                            .value
                    WorkspaceLayout.entries.filter { it.fits(width, height) }
                }
            } else {
                emptyList()
            },
        layout = layout.workspaceLayout,
        onLayout = { selectedLayout -> state.workspace = Workspace(selectedLayout, workspacePanes) },
        onOpenMenu = if (state.selectedBudgetId.isNotBlank()) ({ state.menuOpen = true }) else null,
    )
}

/** The bottom tab bar, which slides away on screens without it and while the keyboard is open. */
@Composable
internal fun ShellBottomBar(
    chrome: ShellChromeState,
    layout: ShellLayout,
) {
    val tabShellRoute = layout.tabShellRoute
    val reduceMotion = chrome.ctx.reduceMotion
    if (tabShellRoute != null && !chrome.useRail) {
        androidx.compose.animation.AnimatedVisibility(
            visible =
                chrome.ctx.backgroundRouteEntry.route
                    .showsBottomNavigation() &&
                    !layout.imeVisible,
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
                    chrome.ctx.navigation.selectTab,
                )
            }
        }
    }
}
