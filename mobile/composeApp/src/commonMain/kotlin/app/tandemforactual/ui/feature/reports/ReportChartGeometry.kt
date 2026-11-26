package app.tandemforactual.ui.feature.reports

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

internal val ReportChartInset = 8.dp

internal fun reportSlotCenterX(
    index: Int,
    count: Int,
    width: Float,
): Float = width / count.coerceAtLeast(1) * (index + 0.5f)

internal fun reportValueY(
    value: Double,
    minimum: Double,
    maximum: Double,
    chartTop: Float,
    chartBottom: Float,
): Float {
    val spread = (maximum - minimum).coerceAtLeast(1.0)
    return chartBottom - (chartBottom - chartTop) * ((value - minimum) / spread).toFloat()
}

/** Where zero sits on the chart, pinned to the nearest edge when the range doesn't cross it. */
internal fun reportZeroY(
    minimum: Double,
    maximum: Double,
    chartTop: Float,
    chartBottom: Float,
): Float = reportValueY(0.0.coerceIn(minOf(minimum, maximum), maxOf(minimum, maximum)), minimum, maximum, chartTop, chartBottom)

internal fun netWorthScale(
    values: List<Long>,
    axisValues: List<Long>?,
): Pair<Double, Double> {
    val (observedMin, observedMax) = netWorthValueRange(values)
    return (axisValues?.lastOrNull()?.toDouble() ?: observedMin) to (axisValues?.firstOrNull()?.toDouble() ?: observedMax)
}

internal fun netWorthPointOffsets(
    values: List<Long>,
    axisValues: List<Long>?,
    width: Float,
    chartTop: Float,
    chartBottom: Float,
): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val (minimum, maximum) = netWorthScale(values, axisValues)
    return values.mapIndexed { index, value ->
        Offset(reportSlotCenterX(index, values.size, width), reportValueY(value.toDouble(), minimum, maximum, chartTop, chartBottom))
    }
}

/** The top of a y-axis label so its middle lines up with its gridline, kept inside the axis. */
internal fun reportAxisLabelTop(
    index: Int,
    count: Int,
    height: Float,
    inset: Float,
    labelHeight: Float,
): Float {
    val gridY =
        if (count <= 1) {
            height / 2f
        } else {
            inset + (height - inset * 2f).coerceAtLeast(0f) * index / (count - 1).toFloat()
        }
    return (gridY - labelHeight / 2f).coerceIn(0f, (height - labelHeight).coerceAtLeast(0f))
}
