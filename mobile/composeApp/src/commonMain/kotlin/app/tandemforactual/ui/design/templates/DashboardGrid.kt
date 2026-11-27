@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.templates

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One card on a dashboard page; [fullWidth] cards span every column. */
internal class DashboardCard(
    val key: Any,
    val fullWidth: Boolean = false,
    val content: @Composable () -> Unit,
)

private typealias PlacementOffsets = MutableMap<Any, Animatable<IntOffset, AnimationVector2D>>

/**
 * Cards in balanced columns. TalkBack reads them in list order whatever column they land in, and cards that
 * change column on a resize glide there instead of jumping.
 */
@Composable
internal fun DashboardGrid(
    columns: Int,
    cards: List<DashboardCard>,
    gap: Dp,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = tandemReduceMotion
    val scope = rememberCoroutineScope()
    val offsets: PlacementOffsets = remember { mutableMapOf() }
    Layout(
        content = {
            cards.forEachIndexed { index, card ->
                key(card.key) {
                    Box(Modifier.semantics { traversalIndex = index.toFloat() }) { card.content() }
                }
            }
        },
        modifier = modifier.fillMaxWidth().semantics { isTraversalGroup = true },
    ) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val lanes = columns.coerceAtLeast(1)
        val width = constraints.maxWidth
        val laneWidth = ((width - gapPx * (lanes - 1)) / lanes).coerceAtLeast(0)
        val placeables =
            measurables.mapIndexed { index, measurable ->
                measurable.measure(Constraints.fixedWidth(if (cards[index].fullWidth) width else laneWidth))
            }
        val heights = placeables.map { it.height }
        val slots = dashboardSlots(heights, cards.map { it.fullWidth }, lanes, gapPx)
        offsets.keys.retainAll(cards.map { it.key }.toSet())
        layout(width, dashboardHeight(slots, heights).coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placeables.forEachIndexed { index, placeable ->
                val target = IntOffset(slots[index].lane * (laneWidth + gapPx), slots[index].top)
                val position = if (reduceMotion) target else offsets.glideTo(cards[index].key, target, gapPx * 2, scope)
                placeable.placeRelative(position)
            }
        }
    }
}

/** Follows small moves such as a resize exactly and animates jumps such as a card moving to another column. */
private fun PlacementOffsets.glideTo(
    key: Any,
    target: IntOffset,
    jump: Int,
    scope: CoroutineScope,
): IntOffset {
    val offset = getOrPut(key) { Animatable(target, IntOffset.VectorConverter) }
    if (offset.targetValue == target) return offset.value
    val distance = offset.targetValue - target
    val animate = offset.isRunning || maxOf(abs(distance.x), abs(distance.y)) > jump
    scope.launch {
        if (animate) {
            offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
        } else {
            offset.snapTo(target)
        }
    }
    return if (animate) offset.value else target
}

/** One lazy item per card in a single column, or one grid item once there is room for more columns. */
internal fun LazyListScope.dashboardItems(
    key: String,
    columns: Int,
    gap: Dp,
    cards: List<DashboardCard>,
) {
    if (columns > 1) {
        item(key = key) { DashboardGrid(columns, cards, gap) }
        return
    }
    cards.forEach { card ->
        item(key = card.key) { Box(tandemListItemModifier(tandemReduceMotion)) { card.content() } }
    }
}

/** A scrolling page of cards: one readable column on phones, balanced columns on wide windows. */
@Composable
internal fun PageGrid(
    cards: List<DashboardCard>,
    verticalSpacing: Dp = TandemSpacing.large,
    horizontalPadding: Dp = TandemSpacing.screen,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    val widthClass = currentWindowSizeInfo().widthClass
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = dashboardColumnCount(widthClass, maxWidth - horizontalPadding * 2, verticalSpacing)
        DashboardGrid(
            columns,
            cards,
            verticalSpacing,
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = dashboardPadding(maxWidth, horizontalPadding, columns), vertical = TandemSpacing.medium),
        )
    }
}

/**
 * A scrolling page with its main content first and supporting content after it, side by side on wide windows.
 * Each side keeps its state when the window crosses between one and two columns.
 */
@Composable
internal fun PageColumns(
    verticalSpacing: Dp = TandemSpacing.large,
    horizontalPadding: Dp = TandemSpacing.screen,
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = LocalRouteScrollState.current ?: rememberScrollState()
    val widthClass = currentWindowSizeInfo().widthClass
    val currentPrimary by rememberUpdatedState(primary)
    val currentSecondary by rememberUpdatedState(secondary)
    val primaryContent = remember { movableContentWithReceiverOf<ColumnScope> { currentPrimary() } }
    val secondaryContent = remember { movableContentWithReceiverOf<ColumnScope> { currentSecondary() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = dashboardColumnCount(widthClass, maxWidth - horizontalPadding * 2, verticalSpacing).coerceAtMost(2)
        val pageModifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = dashboardPadding(maxWidth, horizontalPadding, columns), vertical = TandemSpacing.medium)
        val spacing = Arrangement.spacedBy(verticalSpacing)
        if (columns == 1) {
            Column(pageModifier, verticalArrangement = spacing) {
                primaryContent()
                secondaryContent()
            }
        } else {
            Row(pageModifier, horizontalArrangement = spacing, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).semantics { isTraversalGroup = true }, verticalArrangement = spacing) { primaryContent() }
                Column(Modifier.weight(1f).semantics { isTraversalGroup = true }, verticalArrangement = spacing) { secondaryContent() }
            }
        }
    }
}
