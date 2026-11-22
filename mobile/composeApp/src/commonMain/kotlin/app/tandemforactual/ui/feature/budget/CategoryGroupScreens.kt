@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

internal fun reorderItemToIndex(
    itemIds: List<String>,
    itemId: String,
    destinationIndex: Int,
): List<String> {
    val currentIndex = itemIds.indexOf(itemId)
    if (currentIndex < 0 || itemIds.isEmpty()) return itemIds
    val destination = destinationIndex.coerceIn(0, itemIds.lastIndex)
    if (destination == currentIndex) return itemIds
    return itemIds.toMutableList().apply {
        removeAt(currentIndex)
        add(destination, itemId)
    }
}

internal fun itemTargetIdAfterMove(
    itemIds: List<String>,
    itemId: String,
): String? {
    val index = itemIds.indexOf(itemId)
    if (index < 0) return null
    return itemIds.getOrNull(index + 1)
}
