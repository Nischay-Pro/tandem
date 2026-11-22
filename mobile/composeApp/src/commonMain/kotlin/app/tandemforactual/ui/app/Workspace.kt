@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.WorkspaceLayout
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

/**
 * Lays [panes] out for [layout] with thin dividers; the pane showing [focused] gets an accent outline, and touching a
 * pane focuses it so the rail, shortcuts and new screens follow that pane.
 */
@Composable
internal fun WorkspacePanes(
    layout: WorkspaceLayout,
    panes: List<AppRoute>,
    focused: AppRoute,
    onFocus: (AppRoute) -> Unit,
    pane: @Composable ColumnScope.(AppRoute) -> Unit,
) {
    val palette = tandemPalette
    val cell: @Composable (AppRoute, Modifier) -> Unit = { route, modifier ->
        val outline by animateFloatAsState(
            if (route ==
                focused
            ) {
                1f
            } else {
                0f
            },
            tandemMotionSpec(tandemReduceMotion, TandemMotion.CONTROL_MILLIS),
        )
        Box(
            modifier
                .padding(TandemSpacing.xxSmall)
                .clip(RoundedCornerShape(TandemShapes.large))
                .background(palette.background)
                .border(1.5.dp, palette.accent.copy(alpha = outline * 0.7f), RoundedCornerShape(TandemShapes.large))
                .pointerInput(route) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) onFocus(route)
                        }
                    }
                },
        ) {
            Column(Modifier.fillMaxSize()) { pane(route) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(TandemSpacing.xxSmall)) {
        if (layout == WorkspaceLayout.GRID) {
            Column(Modifier.fillMaxSize()) {
                panes.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { route -> cell(route, Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Start) {
                panes.forEach { route -> cell(route, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}
