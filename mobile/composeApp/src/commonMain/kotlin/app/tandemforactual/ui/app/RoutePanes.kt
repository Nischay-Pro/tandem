@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.LocalPaneDetailRoute
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.design.buttons.TandemBackButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.theme.TandemBackHandler
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemFadeEnter
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemPaneEnter
import app.tandemforactual.ui.design.theme.tandemPaneExit
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/** Whether a page and a supporting pane beside it both fit in the space they share. */
internal fun usesSupportingPane(containerWidthDp: Float): Boolean =
    containerWidthDp >= TandemLayout.minMainPaneWidth.value + TandemLayout.listPaneWidth.value

/** Opens [paneRoutes] with [show] instead of pushing them as full-screen routes. */
internal fun AppNavigation.openingInPane(
    paneRoutes: Set<AppRoute>,
    show: (AppRoute) -> Unit,
): AppNavigation {
    val push = open
    return copy(open = { route -> if (route in paneRoutes) show(route) else push(route) })
}

/** Back from a screen shown in a pane closes the pane rather than leaving the page. */
internal fun AppNavigation.closingPane(close: () -> Unit): AppNavigation = copy(back = close, backToList = close)

/** A pane's title with a close button, or a back button for a screen opened from another one in the same pane. */
@Composable
internal fun PaneHeader(
    title: String,
    onClose: () -> Unit,
    back: Boolean = false,
) {
    val modifier = Modifier.padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium)
    if (!back) {
        TandemModalHeader(title = title, onClose = onClose, modifier = modifier)
        return
    }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        TandemBackButton(onClick = onClose)
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontWeight = FontWeight.Bold,
            fontFamily = tandemAlloFamily(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Opening a screen inside a pane slides it in from the trailing edge over the one it came from; back reverses it. */
internal fun AnimatedContentTransitionScope<Boolean>.paneStackTransform(reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return EnterTransition.None togetherWith ExitTransition.None
    val slide = tween<IntOffset>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
    val fade = tween<Float>(TandemMotion.ROUTE_MILLIS, easing = TandemMotion.emphasizedEasing)
    val covered: (Int) -> Int = { -(it * TandemMotion.ROUTE_PARALLAX).toInt() }
    return if (targetState) {
        slideInHorizontally(slide) { it } togetherWith
            (slideOutHorizontally(slide, covered) + fadeOut(fade, targetAlpha = TandemMotion.ROUTE_COVERED_ALPHA))
    } else {
        (
            (slideInHorizontally(slide, covered) + fadeIn(fade, initialAlpha = TandemMotion.ROUTE_COVERED_ALPHA)) togetherWith
                slideOutHorizontally(slide) { it }
        ).apply { targetContentZIndex = -1f }
    }
}

/** Fades a pane's screen in with its own scroll positions, so each item opens at its top. */
@Composable
internal fun PaneContent(content: @Composable () -> Unit) {
    val reveal = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = reveal,
        modifier = Modifier.fillMaxSize(),
        enter = tandemFadeEnter(tandemReduceMotion),
        exit = ExitTransition.None,
    ) {
        CompositionLocalProvider(
            LocalRouteScrollState provides rememberScrollState(),
            LocalRouteLazyListState provides rememberLazyListState(),
        ) { content() }
    }
}

/** A route's own screen, rendered inside a pane with [navigation]. */
@Composable
internal fun PaneScreen(
    ctx: ScreenContext,
    route: AppRoute,
    navigation: AppNavigation,
) {
    screenRegistry[route]?.invoke(ctx.inPane(route, navigation))
}

/**
 * A list page whose links to [paneRoutes] open beside it when both fit, and push full screen otherwise. Back closes the
 * open pane first.
 */
@Composable
internal fun RouteListDetailPanes(
    ctx: ScreenContext,
    paneRoutes: Set<AppRoute>,
    emptyTitle: String,
    emptyMessage: String,
    list: @Composable (AppNavigation) -> Unit,
    detail: @Composable (AppRoute, AppNavigation) -> Unit = { route, navigation -> PaneScreen(ctx, route, navigation) },
) {
    var detailName by rememberSaveable(ctx.state.selectedBudgetId) { mutableStateOf<String?>(null) }
    val detailRoute = paneRoutes.firstOrNull { it.name == detailName }
    val show: (AppRoute) -> Unit = { detailName = it.name }
    val close: () -> Unit = { detailName = null }
    val paneNavigation = ctx.navigation.openingInPane(paneRoutes, show)
    ListDetailPanes(
        detailKey = detailRoute?.name,
        emptyTitle = emptyTitle,
        emptyMessage = emptyMessage,
        list = { detailInPane ->
            TandemBackHandler(enabled = detailInPane && detailRoute != null, onBack = close, onBackProgress = { _, _ -> })
            CompositionLocalProvider(LocalPaneDetailRoute provides detailRoute.takeIf { detailInPane }) {
                list(if (detailInPane) paneNavigation else ctx.navigation)
            }
        },
        detail = {
            if (detailRoute != null) {
                Column(Modifier.fillMaxSize()) {
                    PaneHeader(detailRoute.title, close)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PaneContent { detail(detailRoute, paneNavigation.closingPane(close)) }
                    }
                }
            }
        },
    )
}

/**
 * Shows [pane] beside [main] while [paneKey] is set and both fit, sliding it in from the trailing edge; on a book-posture
 * foldable the pane takes the half past the hinge. [main] learns whether the pane can show so its links can target it.
 */
@Composable
internal fun SupportingPane(
    paneKey: String?,
    main: @Composable (paneFits: Boolean) -> Unit,
    pane: @Composable () -> Unit,
) {
    val hinge = LocalWindowHinge.current
    val density = LocalDensity.current.density
    var containerLeft by remember { mutableFloatStateOf(0f) }
    var shownKey by remember { mutableStateOf(paneKey) }
    if (paneKey != null && paneKey != shownKey) shownKey = paneKey
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { containerLeft = it.positionInWindow().x }) {
        val width = maxWidth.value
        val split = hingeSplit(hinge, containerLeft, width * density, density)
        val fits = split != null || usesSupportingPane(width)
        val paneWidth = if (split != null) (width - split.first - split.second).dp else listPaneWidth(width)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { main(fits) }
            AnimatedVisibility(
                visible = fits && paneKey != null,
                modifier = Modifier.fillMaxHeight(),
                enter = tandemPaneEnter(tandemReduceMotion),
                exit = tandemPaneExit(tandemReduceMotion),
            ) {
                Row(Modifier.fillMaxHeight()) {
                    if (split != null) {
                        Box(Modifier.width(split.second.dp).fillMaxHeight())
                    } else {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(tandemPalette.border))
                    }
                    Box(Modifier.width(paneWidth).fillMaxHeight()) {
                        key(shownKey) { PaneContent(pane) }
                    }
                }
            }
        }
    }
}
