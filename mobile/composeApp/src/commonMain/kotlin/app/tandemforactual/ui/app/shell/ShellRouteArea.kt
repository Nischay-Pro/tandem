@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import app.tandemforactual.ui.app.ListDetailContinuityEffect
import app.tandemforactual.ui.app.WorkspacePanes
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppRouteEntry
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.decodeAppTabBackStacks
import app.tandemforactual.ui.core.state.isTab
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.appBackgroundBrush
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.ThemeMode
import app.tandemforactual.ui.design.theme.tandemEdgeBackGesture
import app.tandemforactual.ui.design.theme.tandemPalette

/** The route viewport with its edge back gesture, followed by the bottom bar and the transaction task sheet. */
@Composable
internal fun ColumnScope.ShellRouteArea(
    chrome: ShellChromeState,
    layout: ShellLayout,
) {
    val ctx = chrome.ctx
    val routeTransitionInputBlockedState = rememberUpdatedState(ctx.shell.routeTransitionInputBlocked)
    val transitionInputModifier = Modifier.transitionInputBlocker(ctx.state, routeTransitionInputBlockedState)
    var routeBounds by remember { mutableStateOf<Rect?>(null) }
    if (!layout.workspaceActive) {
        ListDetailContinuityEffect(
            routeBounds,
            ctx.backStack.map { it.route },
            ctx.navigator::listDetailOpen,
            ctx.navigator::showListDetail,
        )
    }
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { routeBounds = Rect(it.positionInWindow(), it.size.toSize()) }
                .clipToBounds()
                .then(transitionInputModifier)
                .tandemEdgeBackGesture(
                    ctx.backStack.size > 1 && !ctx.transactionSheetActive,
                    ctx.navigator::handleBack,
                    ctx.navigator::updateBackGesture,
                ),
    ) {
        if (layout.workspaceActive) {
            ShellWorkspace(chrome, layout)
        } else {
            ShellRouteTransitions(chrome, layout)
        }
    }
    ShellBottomBar(chrome, layout)
    if (ctx.transactionSheetActive) TransactionTaskSheet(chrome, transitionInputModifier)
}

/**
 * The workspace panes. Each shows the top of its tab's stack, so a screen opened from a pane stays in that pane and
 * slides in there; touching another pane brings back that tab's own stack.
 */
@Composable
private fun ShellWorkspace(
    chrome: ShellChromeState,
    layout: ShellLayout,
) {
    val ctx = chrome.ctx
    val focused = layout.tabShellRoute ?: ctx.route
    val savedStacks = remember(ctx.shell.savedTabBackStacks) { decodeAppTabBackStacks(ctx.shell.savedTabBackStacks) }
    val routeBackground = appBackgroundBrush(ctx.state.backgroundStyle, tandemPalette)
    WorkspacePanes(
        layout.workspaceLayout,
        layout.workspacePanes,
        focused,
        onFocus = { if (it != focused) ctx.navigator.selectTab(it) },
    ) { paneRoute ->
        val entry =
            if (paneRoute == focused) {
                ctx.routeEntry
            } else {
                savedStacks[paneRoute]?.lastOrNull() ?: AppRouteEntry(paneRoute, -1L - paneRoute.ordinal)
            }
        AnimatedContent(
            targetState = entry,
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            transitionSpec = { routeTransform(ctx.reduceMotion, ctx.shell) },
            contentKey = { it.stateKey },
            label = "tandem-workspace-pane",
        ) { paneEntry ->
            Column(Modifier.fillMaxSize().background(routeBackground)) {
                ShellHeader(chrome, paneEntry.route)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    ShellRoute(chrome, paneEntry, Modifier.fillMaxSize(), true, false)
                }
            }
        }
    }
}

/** Counts pointer sequences and swallows touches while a route transition is still running. */
private fun Modifier.transitionInputBlocker(
    state: AppState,
    routeTransitionInputBlockedState: State<Boolean>,
): Modifier =
    pointerInput(Unit) {
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

@Composable
private fun ShellRouteTransitions(
    chrome: ShellChromeState,
    layout: ShellLayout,
) {
    val ctx = chrome.ctx
    val shell = ctx.shell
    val routeBackground = appBackgroundBrush(ctx.state.backgroundStyle, tandemPalette, ctx.state.theme == ThemeMode.PURE_BLACK)
    if (!ctx.transactionSheetActive &&
        shell.backGestureProgress > 0f &&
        shell.backPreviewRoute != null &&
        shell.backPreviewRoute != ctx.routeEntry
    ) {
        val direction = shell.backGestureDirection
        ShellRoute(
            chrome,
            shell.backPreviewRoute!!,
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .graphicsLayer {
                    val progress = shell.backGestureProgress
                    translationX = -direction * size.width * TandemMotion.ROUTE_PARALLAX * (1f - progress)
                    alpha = TandemMotion.ROUTE_COVERED_ALPHA + (1f - TandemMotion.ROUTE_COVERED_ALPHA) * progress
                }.background(routeBackground),
            false,
            !layout.stationaryTabShell,
        )
    }
    AnimatedContent(
        targetState = ctx.backgroundRouteEntry,
        modifier = Modifier.fillMaxSize().clipToBounds(),
        transitionSpec = { routeTransform(ctx.reduceMotion, shell) },
        label = "tandem-screen-transition",
    ) { visibleEntry ->
        ShellRoute(
            chrome,
            if (ctx.transactionSheetActive) ctx.backgroundRouteEntry else visibleEntry,
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (visibleEntry == shell.backGestureOutgoingEntry && shell.backGestureProgress > 0f) {
                        translationX = shell.backGestureProgress * size.width * shell.backGestureDirection
                    }
                }.background(routeBackground),
            true,
            !layout.stationaryTabShell,
        )
    }
}

private fun AnimatedContentTransitionScope<AppRouteEntry>.routeTransform(
    reduceMotion: Boolean,
    shell: AppShellState,
): ContentTransform {
    val routeSlideSpec = tween<IntOffset>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
    val routeFadeSpec = tween<Float>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
    return when {
        reduceMotion ||
            shell.skipNextRouteTransition ||
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
        shell.routeTransitionDirection > 0f ->
            slideInHorizontally(routeSlideSpec) { it } togetherWith
                (
                    slideOutHorizontally(routeSlideSpec) { -(it * TandemMotion.ROUTE_PARALLAX).toInt() } +
                        fadeOut(routeFadeSpec, targetAlpha = TandemMotion.ROUTE_COVERED_ALPHA)
                ) using null
        shell.routeTransitionDirection < 0f ->
            (
                (
                    slideInHorizontally(routeSlideSpec) { -(it * TandemMotion.ROUTE_PARALLAX).toInt() } +
                        fadeIn(routeFadeSpec, initialAlpha = TandemMotion.ROUTE_COVERED_ALPHA)
                ) togetherWith slideOutHorizontally(routeSlideSpec) { it } using null
            ).apply { targetContentZIndex = -1f }
        else -> EnterTransition.None togetherWith ExitTransition.None
    }
}

@Composable
private fun TransactionTaskSheet(
    chrome: ShellChromeState,
    transitionInputModifier: Modifier,
) {
    val ctx = chrome.ctx
    val shell = ctx.shell
    val backStack = ctx.backStack
    TandemTaskSheet(
        visible = !shell.transactionSheetClosing,
        onDismissRequest = ctx.navigator::handleCurrentRouteBack,
        fullScreen = true,
        dismissOnBackPress = true,
        onDismissed = {
            shell.routeTransitionDirection = 0f
            repeat(shell.transactionSheetPopCount.coerceAtMost(backStack.size - 1)) {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            }
            shell.transactionSheetClosing = false
            shell.transactionSheetPopCount = 1
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .then(transitionInputModifier)
                .tandemEdgeBackGesture(
                    enabled = ctx.transactionSheetActive && backStack.size > 1,
                    onBack = ctx.navigator::handleCurrentRouteBack,
                    onBackProgress = ctx.navigator::updateBackGesture,
                ),
        ) {
            AnimatedContent(
                targetState = ctx.routeEntry,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                label = "tandem-task-sheet-content",
            ) { sheetEntry ->
                ShellRoute(chrome, sheetEntry, Modifier.fillMaxSize(), true, true)
            }
        }
    }
}
