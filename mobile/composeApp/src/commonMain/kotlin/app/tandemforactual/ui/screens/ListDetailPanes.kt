@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState

internal const val LIST_DETAIL_MIN_WIDTH_DP = 700f

/** Whether a list and its detail fit side by side in this much width. */
internal fun usesListDetailPanes(widthDp: Float): Boolean = widthDp >= LIST_DETAIL_MIN_WIDTH_DP

internal fun listPaneWidth(widthDp: Float): Dp = if (widthDp >= 1_000f) 420.dp else 360.dp

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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        if (!usesListDetailPanes(width)) {
            list(false)
            return@BoxWithConstraints
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listPaneWidth(width)).fillMaxHeight()) { list(true) }
            Box(Modifier.width(1.dp).fillMaxHeight().background(tandemPalette.border))
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
