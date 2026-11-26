@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.design.buttons

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide

@Composable
internal fun ReorderDragHandle(
    itemName: String = "item",
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val stepPixels = with(density) { 52.dp.toPx() }
    val latestMoveUp by rememberUpdatedState(onMoveUp)
    val latestMoveDown by rememberUpdatedState(onMoveDown)
    val latestOnDragStarted by rememberUpdatedState(onDragStarted)
    val latestOnDragBy by rememberUpdatedState(onDragBy)
    val latestOnDragStopped by rememberUpdatedState(onDragStopped)
    val latestCanMoveUp by rememberUpdatedState(canMoveUp)
    val latestCanMoveDown by rememberUpdatedState(canMoveDown)
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val dragState =
        rememberDraggableState { delta ->
            val dragHandler = latestOnDragBy
            if (dragHandler != null) {
                dragHandler(delta)
            } else {
                dragDistance += delta
                if (dragDistance >= stepPixels && latestCanMoveDown) {
                    latestMoveDown()
                    dragDistance -= stepPixels
                }
                if (dragDistance <= -stepPixels && latestCanMoveUp) {
                    latestMoveUp()
                    dragDistance += stepPixels
                }
                if (!latestCanMoveDown && dragDistance > 0f || !latestCanMoveUp && dragDistance < 0f) {
                    dragDistance = 0f
                }
            }
        }
    val actionList =
        reorderAccessibilityActions(itemName, enabled, canMoveUp, canMoveDown, { latestMoveUp() }, { latestMoveDown() })
    val reorderSemantics =
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Drag to reorder $itemName"
            customActions = actionList
        }
    val handleModifier =
        Modifier
            .size(MIN_TOUCH_TARGET)
            .clip(RoundedCornerShape(TandemShapes.medium))
            .then(reorderSemantics)
            .draggable(
                orientation = Orientation.Vertical,
                state = dragState,
                enabled = enabled && (canMoveUp || canMoveDown),
                onDragStarted = {
                    dragDistance = 0f
                    latestOnDragStarted?.invoke()
                },
                onDragStopped = {
                    dragDistance = 0f
                    latestOnDragStopped?.invoke()
                },
            )
    Box(
        modifier = handleModifier,
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.GripVertical, contentDescription = null, modifier = Modifier.size(21.dp), tint = tandemPalette.muted)
    }
}

private fun reorderAccessibilityActions(
    itemName: String,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    latestMoveUp: () -> Unit,
    latestMoveDown: () -> Unit,
): List<CustomAccessibilityAction> =
    buildList {
        if (enabled && canMoveUp) {
            add(
                CustomAccessibilityAction("Move $itemName up") {
                    latestMoveUp()
                    true
                },
            )
        }
        if (enabled && canMoveDown) {
            add(
                CustomAccessibilityAction("Move $itemName down") {
                    latestMoveDown()
                    true
                },
            )
        }
    }
