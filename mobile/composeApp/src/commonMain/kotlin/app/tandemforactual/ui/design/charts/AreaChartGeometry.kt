package app.tandemforactual.ui.design.charts

import kotlin.math.roundToInt

/** The point nearest to [x] when [count] points are spread edge to edge across [width]. */
internal fun areaChartIndexAt(
    x: Float,
    width: Float,
    count: Int,
): Int {
    if (count < 2 || width <= 0f) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

internal fun areaChartX(
    index: Int,
    count: Int,
    width: Float,
): Float = if (count < 2) width / 2f else width * index / (count - 1)

/** The value range the chart spans, always including zero so the fill can rest on it. */
internal fun areaChartRange(values: List<Long>): Pair<Long, Long> {
    val minimum = (values.minOrNull() ?: 0L).coerceAtMost(0L)
    val maximum = (values.maxOrNull() ?: 0L).coerceAtLeast(0L)
    return if (minimum == maximum) minimum to minimum + 1 else minimum to maximum
}

internal fun areaChartY(
    value: Long,
    range: Pair<Long, Long>,
    top: Float,
    bottom: Float,
): Float {
    val (minimum, maximum) = range
    val span = (maximum - minimum).toFloat().coerceAtLeast(1f)
    return bottom - (bottom - top) * ((value - minimum) / span)
}
