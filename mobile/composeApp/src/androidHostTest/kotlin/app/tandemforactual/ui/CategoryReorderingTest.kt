package app.tandemforactual.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryReorderingTest {
    @Test
    fun movesAnItemDirectlyToTheTopAndTargetsTheFollowingItem() {
        val order = listOf("rent", "mortgage", "maintenance", "utilities")

        val reordered = reorderItemToIndex(order, "utilities", 0)

        assertEquals(listOf("utilities", "rent", "mortgage", "maintenance"), reordered)
        assertEquals("rent", itemTargetIdAfterMove(reordered, "utilities"))
    }

    @Test
    fun movesAnItemToTheEndUsingANullTarget() {
        val order = listOf("rent", "mortgage", "maintenance", "utilities")

        val reordered = reorderItemToIndex(order, "maintenance", order.lastIndex)

        assertEquals(listOf("rent", "mortgage", "utilities", "maintenance"), reordered)
        assertEquals(null, itemTargetIdAfterMove(reordered, "maintenance"))
    }

    @Test
    fun leavesMissingAndUnmovedItemsAlone() {
        val order = listOf("rent", "mortgage", "utilities")

        assertEquals(order, reorderItemToIndex(order, "missing", 0))
        assertEquals(order, reorderItemToIndex(order, "mortgage", 1))
        assertEquals(null, itemTargetIdAfterMove(order, "missing"))
    }
}
