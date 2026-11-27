@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.WindowWidthClass
import app.tandemforactual.ui.design.theme.tandemPalette

/** Whether a list and its detail fit side by side in the space they share. */
internal fun usesListDetailPanes(containerWidthDp: Float): Boolean =
    containerWidthDp >= TandemLayout.listPaneWidth.value + TandemLayout.minDetailPaneWidth.value

/** The list pane widens once the space the panes share reaches Expanded width itself. */
internal fun listPaneWidth(containerWidthDp: Float): Dp =
    if (WindowWidthClass.of(containerWidthDp) >= WindowWidthClass.EXPANDED) TandemLayout.wideListPaneWidth else TandemLayout.listPaneWidth

/** A hinge that splits the window, in window pixels, reported while a foldable is half open like a book. */
data class WindowHinge(
    val left: Float,
    val right: Float,
)

internal val LocalWindowHinge = staticCompositionLocalOf<WindowHinge?> { null }

private const val MIN_PANE_WIDTH_DP = 280f

/**
 * The list pane's width and the gap to leave, in dp, so the panes meet at the hinge; null when the hinge isn't usable.
 * The left half is measured from the window edge, so a rail beside the panes counts toward it.
 */
internal fun hingeSplit(
    hinge: WindowHinge?,
    containerLeftPx: Float,
    containerWidthPx: Float,
    density: Float,
): Pair<Float, Float>? {
    if (hinge == null || density <= 0f) return null
    val listWidth = (hinge.left - containerLeftPx) / density
    val detailWidth = (containerLeftPx + containerWidthPx - hinge.right) / density
    if (hinge.left / density < MIN_PANE_WIDTH_DP || listWidth <= 0f || detailWidth < MIN_PANE_WIDTH_DP) return null
    return listWidth to (hinge.right - hinge.left) / density
}

/**
 * Shows [list] beside [detail] when there's room, and [list] alone otherwise. [detailKey] scopes the detail's scroll
 * position so each item opens at its top; a null key shows [emptyTitle] instead.
 */
@Composable
internal fun ListDetailPanes(
    detailKey: String?,
    emptyTitle: String,
    emptyMessage: String,
    list: @Composable (detailInPane: Boolean) -> Unit,
    detail: @Composable () -> Unit,
) {
    val hinge = LocalWindowHinge.current
    val density = LocalDensity.current.density
    var containerLeft by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { containerLeft = it.positionInWindow().x }) {
        val width = maxWidth.value
        val split = hingeSplit(hinge, containerLeft, width * density, density)
        if (split == null && !usesListDetailPanes(width)) {
            list(false)
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(split?.first?.dp ?: listPaneWidth(width)).fillMaxHeight()) { list(true) }
            if (split != null) {
                Box(Modifier.width(split.second.dp).fillMaxHeight())
            } else {
                Box(Modifier.width(1.dp).fillMaxHeight().background(tandemPalette.border))
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                if (detailKey == null) {
                    EmptyState(emptyTitle, emptyMessage)
                } else {
                    key(detailKey) {
                        CompositionLocalProvider(LocalRouteScrollState provides rememberScrollState()) { detail() }
                    }
                }
            }
        }
    }
}
