package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class DragReorderTest {
    @Test
    fun animatedRowsDoNotChangeTheTargetWhileTheFingerIsStill() {
        val state = DragReorderState()
        val order = listOf("a", "b", "c")
        state.measure("a", 100f)
        state.measure("b", 200f)
        state.measure("c", 300f)
        state.start(order, "a")
        state.dragBy(110f)
        assertEquals(listOf("b", "a", "c"), state.reorderedItemIds)
        state.measure("a", 200f)
        state.measure("b", 135f)
        state.dragBy(0f)
        assertEquals(listOf("b", "a", "c"), state.reorderedItemIds)
        state.dragBy(-110f)
        assertEquals(order, state.finish()?.finalOrder)
    }

    @Test
    fun dragMovesAcrossEveryRowPassedByThePointer() {
        val result =
            updateDragReorder(
                itemOrder = listOf("a", "b", "c", "d"),
                draggedItemId = "b",
                dragOffsetPx = 260f,
                rowCenters = mapOf("a" to 0f, "b" to 100f, "c" to 200f, "d" to 300f),
            )

        assertEquals(listOf("a", "c", "d", "b"), result.itemOrder)
        assertEquals(60f, result.dragOffsetPx)
    }

    @Test
    fun dragCanMoveBackAcrossRowsUsingTheSameRules() {
        val result =
            updateDragReorder(
                itemOrder = listOf("a", "b", "c", "d"),
                draggedItemId = "d",
                dragOffsetPx = -260f,
                rowCenters = mapOf("a" to 0f, "b" to 100f, "c" to 200f, "d" to 300f),
            )

        assertEquals(listOf("a", "d", "b", "c"), result.itemOrder)
        assertEquals(-60f, result.dragOffsetPx)
    }

    @Test
    fun missingDraggedRowKeepsTheCurrentOrderAndOffset() {
        val result =
            updateDragReorder(
                itemOrder = listOf("a", "b"),
                draggedItemId = "b",
                dragOffsetPx = 42f,
                rowCenters = mapOf("a" to 0f),
            )

        assertEquals(listOf("a", "b"), result.itemOrder)
        assertEquals(42f, result.dragOffsetPx)
    }

    @Test
    fun theDraggedRowStaysUnderTheFingerWhenTheListLayoutShifts() {
        val state = DragReorderState()
        state.measure("a", 100f)
        state.measure("b", 500f)
        state.measure("c", 900f)
        state.start(listOf("a", "b", "c"), "b")

        // Starting the drag collapses the list, so every row moves up.
        state.measure("a", 50f)
        state.measure("b", 150f)
        state.measure("c", 250f)

        // The finger has not moved, so the row is shifted back under it.
        assertEquals(350f, state.dragOffsetPx)
        // Swaps use where rows are now: the finger (500) is already past "c" (250).
        state.dragBy(0f)
        assertEquals(listOf("a", "c", "b"), state.reorderedItemIds)
    }

    @Test
    fun aDroppedOrderStaysOnScreenUntilTheListCatchesUp() {
        val state = DragReorderState()
        state.measure("a", 100f)
        state.measure("b", 200f)
        state.start(listOf("a", "b"), "a")
        state.dragBy(150f)
        state.finish(listOrder = listOf("a", "b"))

        // Saving is still in progress: keep showing the drop, not the old order.
        assertEquals(listOf("b", "a"), state.currentOrder(listOf("a", "b")))
        // The list now shows the saved order, so the held order is released.
        assertEquals(listOf("b", "a"), state.currentOrder(listOf("b", "a")))
        assertEquals(listOf("a", "b"), state.currentOrder(listOf("a", "b")))
    }

    @Test
    fun aDropThatCouldNotBeSavedCanBeReleased() {
        val state = DragReorderState()
        state.measure("a", 100f)
        state.measure("b", 200f)
        state.start(listOf("a", "b"), "a")
        state.dragBy(150f)
        state.finish(listOrder = listOf("a", "b"))

        state.releaseHeldDrop()

        assertEquals(listOf("a", "b"), state.currentOrder(listOf("a", "b")))
    }

    @Test
    fun sharedDragStateCommitsTheSameOrderAndDisplayProjection() {
        val state = DragReorderState()
        state.start(
            itemOrder = listOf("a", "b", "c"),
            itemId = "b",
            fullDisplayOrder = listOf("a", "b", "fixed", "c"),
        )

        state.dragBy(120f, mapOf("a" to 0f, "b" to 100f, "c" to 200f)) { groupOrder, fullOrder ->
            reorderAccountsWithinSlots(fullOrder, listOf("a", "b", "c"), groupOrder)
        }

        val result = state.finish()
        assertEquals(listOf("a", "c", "b"), result?.finalOrder)
        assertEquals(listOf("a", "c", "fixed", "b"), result?.finalDisplayOrder)
        assertEquals(null, state.draggedItemId)
        assertEquals(null, state.displayOrder)
    }
}
