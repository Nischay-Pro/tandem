@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.grid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.reportMonthLabel
import app.tandemforactual.ui.core.keyboard.keyPress
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.inputs.pointerHighlight
import app.tandemforactual.ui.design.rows.RowContextMenu
import app.tandemforactual.ui.design.rows.RowMenuAction
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion

private val CELL_SHAPE = RoundedCornerShape(TandemShapes.small)
private val FOCUS_RING = 1.5.dp
private const val SELECTED_ALPHA = 0.14f

/** Everything one grid cell needs to draw itself and talk back to the grid. */
internal class GridCellContext(
    val state: AppState,
    val grid: BudgetGridState,
    val data: BudgetGridData,
    val io: BudgetGridIo,
)

/**
 * One category's month: budgeted, spent and balance over a thin spending bar. A tap or Enter opens the budgeted amount
 * for typing; right-click or long-press offers the same changes as the keyboard.
 */
@Composable
internal fun RowScope.BudgetGridCell(
    context: GridCellContext,
    key: GridKey,
    name: String,
    menu: List<RowMenuAction>,
) {
    val grid = context.grid
    val focused = grid.focus == key
    val requester = remember { FocusRequester() }
    val menuAnchor = remember { mutableStateOf<Offset?>(null) }
    val currentContext by rememberUpdatedState(context)
    LaunchedEffect(focused, grid.focusRequested, grid.editing) {
        if (focused && grid.focusRequested && !grid.editing) {
            runCatching { requester.requestFocus() }
            grid.focusRequested = false
        }
    }
    RowContextMenu(menu, Modifier.weight(1f), anchorState = menuAnchor) {
        Box(
            Modifier
                .heightIn(min = GridCellMinHeight)
                .gridCellChrome(focused, grid.selected(context.data, key))
                .focusRequester(requester)
                .onFocusChanged { if (it.isFocused && grid.focus != key) grid.focusOn(key) }
                .onKeyEvent { !grid.editing && grid.onCellKey(it.keyPress(), currentContext.data, currentContext.io) }
                .focusable()
                .pointerInput(key) {
                    detectTapGestures(
                        onTap = { currentContext.let { grid.tap(key, it.data, it.io) } },
                        onLongPress = { menuAnchor.value = it },
                    )
                }.semantics {
                    role = Role.Button
                    selected = focused
                    onClick("Edit $name in ${reportMonthLabel(key.monthId)}") {
                        currentContext.let { grid.tap(key, it.data, it.io) }
                        true
                    }
                },
        ) {
            BudgetGridCellContent(context, key, focused && grid.editing)
        }
    }
}

@Composable
private fun BudgetGridCellContent(
    context: GridCellContext,
    key: GridKey,
    editing: Boolean,
) {
    val amounts = context.data.amounts(key)
    Row(
        Modifier.fillMaxSize().padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            BudgetGridEditor(context, Modifier.weight(1f))
        } else {
            GridAmountText(context.state, amounts?.budgeted, tandemPalette.text, pending = amounts?.pending == true)
        }
        GridAmountText(context.state, amounts?.spent, tandemPalette.muted)
        GridAmountText(context.state, amounts?.balance, amounts?.balance?.let { balanceColor(it) } ?: tandemPalette.muted, bold = true)
    }
    if (amounts != null) GridSpendingBar(amounts, Modifier.align(Alignment.BottomCenter))
}

@Composable
private fun Modifier.gridCellChrome(
    focused: Boolean,
    selected: Boolean,
): Modifier {
    val palette = tandemPalette
    val colorSpec = tandemMotionSpec<Color>(tandemReduceMotion, TandemMotion.COLOR_MILLIS)
    val fill by animateColorAsState(
        if (selected) palette.accent.copy(alpha = SELECTED_ALPHA) else Color.Transparent,
        colorSpec,
        label = "grid cell fill",
    )
    val ring by animateFloatAsState(
        if (focused) 1f else 0f,
        tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "grid cell ring",
    )
    return clip(CELL_SHAPE)
        .background(fill)
        .border(FOCUS_RING, palette.accent.copy(alpha = ring), CELL_SHAPE)
        .pointerHighlight(palette.text, CELL_SHAPE, tandemReduceMotion)
}
