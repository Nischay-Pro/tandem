@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw

/** Pull distance of a page's pull-to-refresh gesture. */
private class PullRefreshState {
    var pullDistancePx by mutableStateOf(0f)
}

/** Page lazy column wrapped with a pull-to-refresh gesture and spinner. */
@Composable
internal fun PullToRefreshLazyColumn(
    verticalSpacing: Dp,
    horizontalPadding: Dp,
    modifier: Modifier,
    routeListState: LazyListState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reduceMotion = tandemReduceMotion
    val refreshThresholdPx = with(density) { 72.dp.toPx() }
    val state = remember(routeListState) { PullRefreshState() }
    val refreshConnection = rememberPullRefreshConnection(routeListState, refreshThresholdPx, state, isRefreshing, onRefresh)
    val pullDistancePx = state.pullDistancePx
    val pullProgress = (pullDistancePx / refreshThresholdPx).coerceIn(0f, 1f)
    val indicatorTarget = if (isRefreshing) 44.dp else with(density) { pullDistancePx.toDp().coerceAtMost(64.dp) }
    val indicatorOffset by
        animateDpAsState(
            targetValue = indicatorTarget,
            animationSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
            label = "pull-to-refresh-offset",
        )
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = routeListState,
            modifier = Modifier.fillMaxSize().nestedScroll(refreshConnection),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
        if (isRefreshing || pullDistancePx > 0f) {
            PullRefreshIndicator(isRefreshing, pullDistancePx, pullProgress, indicatorOffset, reduceMotion, density)
        }
    }
}

@Composable
private fun rememberPullRefreshConnection(
    routeListState: LazyListState,
    refreshThresholdPx: Float,
    state: PullRefreshState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
): NestedScrollConnection {
    val latestIsRefreshing by rememberUpdatedState(isRefreshing)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    return remember(routeListState, refreshThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val atTop = routeListState.firstVisibleItemIndex == 0 && routeListState.firstVisibleItemScrollOffset == 0
                if (available.y <= 0f || !atTop || latestIsRefreshing) return Offset.Zero
                val remainingPull = (refreshThresholdPx * 1.5f - state.pullDistancePx).coerceAtLeast(0f)
                val acceptedPull = available.y.coerceAtMost(remainingPull)
                state.pullDistancePx += acceptedPull
                return Offset(x = 0f, y = acceptedPull)
            }

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y >= 0f || state.pullDistancePx <= 0f) return Offset.Zero
                val consumedPull = (-available.y).coerceAtMost(state.pullDistancePx)
                state.pullDistancePx -= consumedPull
                return Offset(x = 0f, y = -consumedPull)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                releasePull()
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                releasePull()
                return Velocity.Zero
            }

            private fun releasePull() {
                val shouldRefresh = state.pullDistancePx >= refreshThresholdPx && !latestIsRefreshing
                state.pullDistancePx = 0f
                if (shouldRefresh) latestOnRefresh()
            }
        }
    }
}

/** Spinning refresh badge that follows the pull distance. */
@Composable
private fun BoxScope.PullRefreshIndicator(
    isRefreshing: Boolean,
    pullDistancePx: Float,
    pullProgress: Float,
    indicatorOffset: Dp,
    reduceMotion: Boolean,
    density: Density,
) {
    val rotation =
        if (isRefreshing && !reduceMotion) {
            val transition = rememberInfiniteTransition(label = "pull-to-refresh-spin")
            val spin by
                transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
                    label = "pull-to-refresh-rotation",
                )
            spin
        } else if (reduceMotion) {
            0f
        } else {
            pullProgress * 360f
        }
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = (indicatorOffset - 18.dp).coerceAtLeast(0.dp))
            .size(36.dp)
            .graphicsLayer {
                val visibleProgress = if (isRefreshing) 1f else (pullDistancePx / with(density) { 20.dp.toPx() }).coerceIn(0.2f, 1f)
                alpha = visibleProgress
                scaleX = 0.72f + 0.28f * visibleProgress
                scaleY = 0.72f + 0.28f * visibleProgress
            }.clip(CircleShape)
            .background(tandemPalette.raised)
            .border(1.dp, tandemPalette.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Lucide.RefreshCw,
            contentDescription = if (isRefreshing) "Refreshing" else "Pull to refresh",
            modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
            tint = tandemPalette.accent,
        )
    }
}
