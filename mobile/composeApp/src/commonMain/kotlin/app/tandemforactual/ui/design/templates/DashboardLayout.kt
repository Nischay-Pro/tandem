package app.tandemforactual.ui.design.templates

import androidx.compose.ui.unit.Dp
import app.tandemforactual.ui.design.theme.TandemLayout
import app.tandemforactual.ui.design.theme.WindowWidthClass

/** Card columns that fit the width, capped by the window class so phones and narrow panes keep one column. */
internal fun dashboardColumnCount(
    widthClass: WindowWidthClass,
    availableWidth: Dp,
    gap: Dp,
): Int {
    val most =
        when {
            widthClass >= WindowWidthClass.LARGE -> 3
            widthClass >= WindowWidthClass.EXPANDED -> 2
            else -> 1
        }
    val fit = ((availableWidth + gap) / (TandemLayout.dashboardMinColumnWidth + gap)).toInt()
    return fit.coerceIn(1, most)
}

/** Side padding that centres one readable column, or a wider dashboard once it has more than one column. */
internal fun dashboardPadding(
    availableWidth: Dp,
    minimumPadding: Dp,
    columns: Int,
): Dp =
    readablePadding(
        availableWidth,
        minimumPadding,
        if (columns > 1) TandemLayout.dashboardContentWidth else TandemLayout.readableContentWidth,
    )

internal data class DashboardSlot(
    val lane: Int,
    val top: Int,
)

/**
 * Places each card in the shortest lane, preferring the leftmost lane that is nearly as short, so cards read
 * left to right and the columns end close together. Full-width cards start below everything before them.
 */
internal fun dashboardSlots(
    heights: List<Int>,
    fullWidth: List<Boolean>,
    lanes: Int,
    gap: Int,
): List<DashboardSlot> {
    val bottoms = IntArray(lanes.coerceAtLeast(1))
    val tolerance = gap * 3 / 2
    return heights.mapIndexed { index, height ->
        val spacing = if (height > 0) gap else 0
        if (fullWidth.getOrElse(index) { false } || bottoms.size == 1) {
            val top = bottoms.max()
            bottoms.fill(top + height + spacing)
            DashboardSlot(0, top)
        } else {
            val lowest = bottoms.min()
            val lane = bottoms.indexOfFirst { it - lowest <= tolerance }
            val top = bottoms[lane]
            bottoms[lane] = top + height + spacing
            DashboardSlot(lane, top)
        }
    }
}

internal fun dashboardHeight(
    slots: List<DashboardSlot>,
    heights: List<Int>,
): Int = slots.indices.maxOfOrNull { slots[it].top + heights[it] } ?: 0
