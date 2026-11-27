@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemMotionSpec

/** Extra room under a page's last item for floating buttons drawn over the page. */
internal val LocalFloatingActionClearance = compositionLocalOf { 0.dp }

/** The page's padding, with room at the bottom for any floating buttons over it. */
@Composable
internal fun pageContentPadding(horizontal: Dp): PaddingValues =
    PaddingValues(
        start = horizontal,
        top = TandemSpacing.medium,
        end = horizontal,
        bottom = TandemSpacing.medium + LocalFloatingActionClearance.current,
    )

@Composable
internal fun PageColumn(
    verticalSpacing: androidx.compose.ui.unit.Dp = TandemSpacing.large,
    horizontalPadding: androidx.compose.ui.unit.Dp = TandemSpacing.screen,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(pageContentPadding(readablePadding(maxWidth, horizontalPadding, TandemLayout.readableContentWidth))),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

/** Keeps content at a readable width on tablets and unfolded foldables, centered, while the whole width still scrolls. */
internal fun readablePadding(
    availableWidth: Dp,
    minimumPadding: Dp,
    contentWidth: Dp,
): Dp = maxOf(minimumPadding, (availableWidth - contentWidth) / 2)

@Composable
internal fun OnboardingLayout(
    modifier: Modifier = Modifier,
    verticalSpacing: androidx.compose.ui.unit.Dp = TandemSpacing.large,
    horizontalPadding: androidx.compose.ui.unit.Dp = TandemSpacing.screen,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize().imePadding()) {
        val sidePadding = readablePadding(maxWidth, horizontalPadding, TandemLayout.setupContentWidth)
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = sidePadding, vertical = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = content,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePadding, vertical = TandemSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                content = actions,
            )
        }
    }
}

@Composable
internal fun PageLazyColumn(
    verticalSpacing: androidx.compose.ui.unit.Dp = TandemSpacing.large,
    horizontalPadding: androidx.compose.ui.unit.Dp = TandemSpacing.screen,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    contentWidth: Dp = TandemLayout.readableContentWidth,
    content: LazyListScope.() -> Unit,
) {
    val routeListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxSize()) {
        PageLazyColumnContent(
            verticalSpacing,
            readablePadding(maxWidth, horizontalPadding, contentWidth),
            Modifier.fillMaxSize(),
            routeListState,
            isRefreshing,
            onRefresh,
            content,
        )
    }
}

/** A lazy page whose content learns how many card columns fit, for dashboards that spread out on wide windows. */
@Composable
internal fun PageDashboardLazyColumn(
    verticalSpacing: Dp = TandemSpacing.large,
    horizontalPadding: Dp = TandemSpacing.screen,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyListScope.(columns: Int) -> Unit,
) {
    val routeListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    val widthClass = currentWindowSizeInfo().widthClass
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = dashboardColumnCount(widthClass, maxWidth - horizontalPadding * 2, verticalSpacing)
        PageLazyColumnContent(
            verticalSpacing,
            dashboardPadding(maxWidth, horizontalPadding, columns),
            Modifier.fillMaxSize(),
            routeListState,
            isRefreshing,
            onRefresh,
        ) { content(columns) }
    }
}

@Composable
private fun PageLazyColumnContent(
    verticalSpacing: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    routeListState: androidx.compose.foundation.lazy.LazyListState,
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    if (onRefresh == null) {
        LazyColumn(
            state = routeListState,
            modifier = modifier.fillMaxSize(),
            contentPadding = pageContentPadding(horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
        return
    }
    PullToRefreshLazyColumn(verticalSpacing, horizontalPadding, modifier, routeListState, isRefreshing, onRefresh, content)
}

internal fun LazyItemScope.tandemListItemModifier(reduceMotion: Boolean): Modifier =
    Modifier.animateItem(
        fadeInSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
        placementSpec = tandemMotionSpec(reduceMotion, TandemMotion.CONTENT_SIZE_MILLIS),
        fadeOutSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
    )

internal fun appBackgroundBrush(
    style: String,
    palette: TandemPalette,
    pureBlack: Boolean,
): Brush =
    when {
        pureBlack || style == "Solid surface" -> Brush.verticalGradient(listOf(palette.background, palette.background))
        style == "Low-motion glow" -> Brush.radialGradient(listOf(palette.accentSurface, palette.background), radius = 1_300f)
        else ->
            Brush.verticalGradient(
                colorStops =
                    arrayOf(
                        0f to palette.surface,
                        0.42f to palette.background,
                        1f to palette.background,
                    ),
            )
    }
