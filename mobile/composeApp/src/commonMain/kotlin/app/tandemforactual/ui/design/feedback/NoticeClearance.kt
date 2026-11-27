@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

internal enum class NoticeEdge { TOP, BOTTOM }

/** Where the header and pinned bottom actions are on screen, so transient notices can sit clear of them. */
@Stable
internal class NoticeClearance {
    private val areas = mutableStateMapOf<Any, Pair<NoticeEdge, Rect>>()

    internal fun report(
        owner: Any,
        edge: NoticeEdge,
        bounds: Rect,
    ) {
        if (bounds.isEmpty) {
            remove(owner)
            return
        }
        val area = edge to bounds
        if (areas[owner] != area) areas[owner] = area
    }

    internal fun remove(owner: Any) {
        areas.remove(owner)
    }

    /** The highest top edge of bottom actions sharing columns with [left]..[right], in root pixels. */
    fun bottomLimit(
        left: Float,
        right: Float,
    ): Float? = areas.values.filter { (edge, rect) -> edge == NoticeEdge.BOTTOM && rect.spans(left, right) }.minOfOrNull { it.second.top }

    /** The lowest bottom edge of headers sharing columns with [left]..[right], in root pixels. */
    fun topLimit(
        left: Float,
        right: Float,
    ): Float? = areas.values.filter { (edge, rect) -> edge == NoticeEdge.TOP && rect.spans(left, right) }.maxOfOrNull { it.second.bottom }

    private fun Rect.spans(
        left: Float,
        right: Float,
    ) = this.left < right && this.right > left
}

internal val LocalNoticeClearance = staticCompositionLocalOf<NoticeClearance?> { null }

/** Keeps notices from [NoticeHost] off this element while it is on screen. */
internal fun Modifier.reservesNoticeSpace(
    edge: NoticeEdge = NoticeEdge.BOTTOM,
    enabled: Boolean = true,
): Modifier = if (enabled) this then NoticeAreaElement(edge) else this

private data class NoticeAreaElement(
    val edge: NoticeEdge,
) : ModifierNodeElement<NoticeAreaNode>() {
    override fun create() = NoticeAreaNode(edge)

    override fun update(node: NoticeAreaNode) {
        node.edge = edge
    }
}

private class NoticeAreaNode(
    var edge: NoticeEdge,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var clearance: NoticeClearance? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val current = currentValueOf(LocalNoticeClearance)
        if (current !== clearance) clearance?.remove(this)
        clearance = current
        current?.report(this, edge, coordinates.boundsInRoot())
    }

    override fun onDetach() {
        clearance?.remove(this)
        clearance = null
    }
}

/**
 * Shows [content] above the window's bottom insets and every reported bottom action, or under the header when it
 * would not fit there. Only the content itself takes touches.
 */
@Composable
internal fun NoticeHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clearance = LocalNoticeClearance.current
    val density = LocalDensity.current
    val reduceMotion = tandemReduceMotion
    val safeTop by rememberUpdatedState(WindowInsets.safeDrawing.getTop(density).toFloat())
    val safeBottom by rememberUpdatedState(WindowInsets.safeDrawing.getBottom(density).toFloat())
    val gap = with(density) { TandemSpacing.large.toPx() }
    val windowGap = with(density) { 24.dp.toPx() }
    var host by remember { mutableStateOf(Rect.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var atTop by remember { mutableStateOf(false) }
    val offset = remember { Animatable(0f) }
    var anchored by remember { mutableStateOf(false) }

    fun place(
        width: Int,
        height: Int,
    ): Pair<Float, Boolean> {
        val left = host.left + (host.width - width) / 2f
        val right = left + width
        val bottom =
            minOf(host.bottom - safeBottom - windowGap, (clearance?.bottomLimit(left, right) ?: Float.POSITIVE_INFINITY) - gap)
        val top = maxOf(host.top + safeTop + gap, (clearance?.topLimit(left, right) ?: Float.NEGATIVE_INFINITY) + gap)
        val fits = bottom - height >= top
        return (if (fits) bottom - height else top) - host.top to !fits
    }

    LaunchedEffect(clearance, density, reduceMotion) {
        snapshotFlow { size.takeIf { it.height > 0 }?.let { place(it.width, it.height) } }
            .collectLatest { target ->
                if (target == null) {
                    anchored = false
                    return@collectLatest
                }
                atTop = target.second
                if (anchored && !reduceMotion) {
                    offset.animateTo(target.first, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
                } else {
                    offset.snapTo(target.first)
                    anchored = true
                }
            }
    }
    Layout(
        content = {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.onSizeChanged { size = it },
                enter = fadeIn() + slideInVertically { if (atTop) -it / 2 else it / 2 },
                exit = fadeOut() + slideOutVertically { if (atTop) -it / 2 else it / 2 },
            ) { content() }
        },
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = TandemSpacing.large)
                .onGloballyPositioned { host = it.boundsInRoot() },
    ) { measurables, constraints ->
        val placeable = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val y = if (anchored) offset.value else place(placeable.width, placeable.height).first
            placeable.place((constraints.maxWidth - placeable.width) / 2, y.roundToInt())
        }
    }
}
