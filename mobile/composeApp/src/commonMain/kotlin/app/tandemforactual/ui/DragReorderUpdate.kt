@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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

/** Shared pointer-drag state for every reorderable list. */
internal class DragReorderState {
    var draggedItemId by mutableStateOf<String?>(null)
        private set

    var reorderedItemIds by mutableStateOf<List<String>?>(null)
        private set

    var displayOrder by mutableStateOf<List<String>?>(null)
        private set

    var dragOffsetPx by mutableFloatStateOf(0f)
        private set

    private var startingOrder by mutableStateOf<List<String>>(emptyList())
    private var startingDisplayOrder by mutableStateOf<List<String>>(emptyList())

    private val measuredCenters = mutableMapOf<String, Float>()
    private var initialCenters: Map<String, Float> = emptyMap()
    private var totalDrag = 0f

    fun measure(
        id: String,
        center: Float,
    ) {
        measuredCenters[id] = center
    }

    fun forget(id: String) {
        measuredCenters.remove(id)
    }

    fun currentOrder(fallback: List<String>): List<String> = reorderedItemIds ?: fallback

    fun start(
        itemOrder: List<String>,
        itemId: String,
        fullDisplayOrder: List<String> = itemOrder,
    ) {
        if (itemId !in itemOrder) return
        initialCenters = measuredCenters.toMap()
        totalDrag = 0f
        draggedItemId = itemId
        startingOrder = itemOrder
        reorderedItemIds = itemOrder
        startingDisplayOrder = fullDisplayOrder
        displayOrder = fullDisplayOrder
        dragOffsetPx = 0f
    }

    fun dragBy(
        delta: Float,
        rowCenters: Map<String, Float> = initialCenters,
        fullDisplayOrderFor: (List<String>, List<String>) -> List<String> = { order, _ -> order },
    ) {
        val itemId = draggedItemId ?: return
        totalDrag += delta
        val update = updateDragReorder(startingOrder, itemId, totalDrag, rowCenters)
        reorderedItemIds = update.itemOrder
        displayOrder = fullDisplayOrderFor(update.itemOrder, startingDisplayOrder)
        dragOffsetPx = update.dragOffsetPx
    }

    fun finish(): DragReorderResult? {
        val itemId = draggedItemId ?: return null
        val result =
            DragReorderResult(
                itemId = itemId,
                startingOrder = startingOrder,
                finalOrder = reorderedItemIds ?: startingOrder,
                startingDisplayOrder = startingDisplayOrder,
                finalDisplayOrder = displayOrder ?: startingDisplayOrder,
            )
        draggedItemId = null
        reorderedItemIds = null
        displayOrder = null
        dragOffsetPx = 0f
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
