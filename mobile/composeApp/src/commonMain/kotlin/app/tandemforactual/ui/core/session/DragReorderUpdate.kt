@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.session

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.tandemforactual.ui.core.state.reorderItemToIndex
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.tandemMotionSpec

internal data class DragReorderUpdate(
    val itemOrder: List<String>,
    val dragOffsetPx: Float,
)

internal data class DragReorderResult(
    val itemId: String,
    val startingOrder: List<String>,
    val finalOrder: List<String>,
    val startingDisplayOrder: List<String>,
    val finalDisplayOrder: List<String>,
)

internal fun reorderModeConsumesItemTap(selectedItemId: String?): Boolean = selectedItemId != null

internal fun Modifier.reorderAccessibilityAction(
    itemName: String,
    onEnableReorder: () -> Unit,
): Modifier =
    semantics {
        customActions =
            listOf(
                CustomAccessibilityAction("Show reorder controls for $itemName") {
                    onEnableReorder()
                    true
                },
            )
    }

internal fun toggleReorderSelection(
    selectedItemId: String?,
    tappedItemId: String,
): String? = if (selectedItemId == tappedItemId) null else tappedItemId

/**
 * Shared pointer-drag state for every reorderable list.
 *
 * The dragged row follows the finger: its offset is the finger position minus the
 * row's current layout position, so it stays under the finger when the list's
 * layout changes mid-drag (for example, groups collapsing when a drag starts) and
 * when rows have different heights. Swaps compare the finger with the other rows'
 * current positions.
 */
internal class DragReorderState {
    var draggedItemId by mutableStateOf<String?>(null)
        private set

    var reorderedItemIds by mutableStateOf<List<String>?>(null)
        private set

    var displayOrder by mutableStateOf<List<String>?>(null)
        private set

    /** Finger position (row centre at drag start plus the drag so far), in root coordinates. */
    private var fingerY by mutableFloatStateOf(0f)

    /** Where the layout currently places the dragged row's centre, in root coordinates. */
    private var draggedLayoutCenter by mutableFloatStateOf(0f)

    /** How far to shift the dragged row so it sits under the finger. */
    val dragOffsetPx: Float
        get() = if (draggedItemId == null) 0f else fingerY - draggedLayoutCenter

    private var startingOrder by mutableStateOf<List<String>>(emptyList())
    private var startingDisplayOrder by mutableStateOf<List<String>>(emptyList())

    private val measuredCenters = mutableMapOf<String, Float>()
    private var originCenter = 0f
    private var totalDrag = 0f

    /**
     * The order just dropped, shown until the list's own order changes. Saving the
     * drop is asynchronous, so without this the old order flashes back for a frame.
     */
    private var heldDropOrder: List<String>? = null
    private var orderWhenDropped: List<String>? = null

    fun measure(
        id: String,
        center: Float,
    ) {
        measuredCenters[id] = center
        if (id == draggedItemId) draggedLayoutCenter = center
    }

    fun forget(id: String) {
        measuredCenters.remove(id)
    }

    fun currentOrder(fallback: List<String>): List<String> {
        reorderedItemIds?.let { return it }
        val held = heldDropOrder ?: return fallback
        if (orderWhenDropped != null && fallback != orderWhenDropped) {
            // The list now reflects the drop (or something newer), so stop holding.
            releaseHeldDrop()
            return fallback
        }
        return held
    }

    /** Stops showing a dropped order, for example when saving it failed. */
    fun releaseHeldDrop() {
        heldDropOrder = null
        orderWhenDropped = null
    }

    fun start(
        itemOrder: List<String>,
        itemId: String,
        fullDisplayOrder: List<String> = itemOrder,
    ) {
        if (itemId !in itemOrder) return
        releaseHeldDrop()
        originCenter = measuredCenters[itemId] ?: 0f
        totalDrag = 0f
        fingerY = originCenter
        draggedLayoutCenter = originCenter
        draggedItemId = itemId
        startingOrder = itemOrder
        reorderedItemIds = itemOrder
        startingDisplayOrder = fullDisplayOrder
        displayOrder = fullDisplayOrder
    }

    fun dragBy(
        delta: Float,
        rowCenters: Map<String, Float>? = null,
        fullDisplayOrderFor: (List<String>, List<String>) -> List<String> = { order, _ -> order },
    ) {
        val itemId = draggedItemId ?: return
        totalDrag += delta
        fingerY = originCenter + totalDrag
        // The finger started at the dragged row's centre, so that is its anchor.
        val centers = rowCenters ?: (measuredCenters.toMap() + (itemId to originCenter))
        val update = updateDragReorder(startingOrder, itemId, totalDrag, centers)
        if (update.itemOrder != reorderedItemIds) {
            reorderedItemIds = update.itemOrder
            displayOrder = fullDisplayOrderFor(update.itemOrder, startingDisplayOrder)
        }
    }

    /**
     * Ends the drag. [listOrder] is the order the list shows without the drag; the
     * dropped order stays on screen until that changes.
     */
    fun finish(listOrder: List<String>? = null): DragReorderResult? {
        val itemId = draggedItemId ?: return null
        val result =
            DragReorderResult(
                itemId = itemId,
                startingOrder = startingOrder,
                finalOrder = reorderedItemIds ?: startingOrder,
                startingDisplayOrder = startingDisplayOrder,
                finalDisplayOrder = displayOrder ?: startingDisplayOrder,
            )
        if (result.finalOrder != result.startingOrder) {
            heldDropOrder = result.finalDisplayOrder
            orderWhenDropped = listOrder ?: result.startingDisplayOrder
        }
        draggedItemId = null
        reorderedItemIds = null
        displayOrder = null
        totalDrag = 0f
        startingOrder = emptyList()
        startingDisplayOrder = emptyList()
        return result
    }
}

@Composable
internal fun LazyItemScope.dragReorderItemModifier(
    itemId: String,
    dragState: DragReorderState,
    reduceMotion: Boolean,
    dragEnabled: Boolean = false,
    onDragStarted: (() -> Unit)? = null,
    onDragBy: ((Float) -> Unit)? = null,
    onDragStopped: (() -> Unit)? = null,
): Modifier {
    val isDragging = dragState.draggedItemId == itemId
    val latestOnDragStarted by rememberUpdatedState(onDragStarted)
    val latestOnDragBy by rememberUpdatedState(onDragBy)
    val latestOnDragStopped by rememberUpdatedState(onDragStopped)
    val dragScale by
        animateFloatAsState(
            targetValue = if (isDragging) 1.035f else 1f,
            animationSpec =
                if (reduceMotion) {
                    tandemMotionSpec(
                        true,
                        TandemMotion.CONTROL_MILLIS,
                    )
                } else {
                    spring(dampingRatio = 0.78f, stiffness = 520f)
                },
            label = "list-reorder-scale",
        )
    val placementModifier =
        Modifier.animateItem(
            fadeInSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
            placementSpec =
                if (reduceMotion) {
                    tandemMotionSpec(
                        true,
                        TandemMotion.CONTENT_SIZE_MILLIS,
                    )
                } else {
                    spring<IntOffset>(dampingRatio = 0.86f, stiffness = 430f)
                },
            fadeOutSpec = tandemMotionSpec(reduceMotion, TandemMotion.REVEAL_MILLIS),
        )
    val longPressDragModifier =
        Modifier.pointerInput(itemId, dragEnabled) {
            if (dragEnabled) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestOnDragStarted?.invoke() },
                    onDragEnd = { latestOnDragStopped?.invoke() },
                    onDragCancel = { latestOnDragStopped?.invoke() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDragBy?.invoke(dragAmount.y)
                    },
                )
            }
        }
    return longPressDragModifier
        .then(if (isDragging) Modifier else placementModifier)
        .onGloballyPositioned { coordinates ->
            dragState.measure(itemId, coordinates.positionInRoot().y + coordinates.size.height / 2f)
        }.graphicsLayer {
            translationY = if (isDragging) dragState.dragOffsetPx else 0f
            scaleX = dragScale
            scaleY = dragScale
        }.then(
            if (isDragging) {
                Modifier.shadow(12.dp, RoundedCornerShape(TandemShapes.large))
            } else {
                Modifier
            },
        ).zIndex(if (isDragging) 1f else 0f)
}

internal fun updateDragReorder(
    itemOrder: List<String>,
    draggedItemId: String,
    dragOffsetPx: Float,
    rowCenters: Map<String, Float>,
): DragReorderUpdate {
    if (draggedItemId !in itemOrder) return DragReorderUpdate(itemOrder, dragOffsetPx)

    val start = itemOrder.indexOf(draggedItemId)
    val origin = rowCenters[draggedItemId] ?: return DragReorderUpdate(itemOrder, dragOffsetPx)
    val pointer = origin + dragOffsetPx
    val direction = if (dragOffsetPx >= 0f) 1 else -1
    var target = start
    var targetCenter = origin
    while (target + direction in itemOrder.indices) {
        val center = rowCenters[itemOrder[target + direction]] ?: break
        if ((pointer - center) * direction < 0f) break
        target += direction
        targetCenter = center
    }
    return DragReorderUpdate(reorderItemToIndex(itemOrder, draggedItemId, target), pointer - targetCenter)
}
