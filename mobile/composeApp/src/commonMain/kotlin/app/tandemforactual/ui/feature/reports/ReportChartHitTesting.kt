@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

internal fun reportSelectionAt(
    kind: ReportKind,
    categories: List<CategorySpendingPoint>,
    monthly: List<MonthlyMoneyPoint>,
    netWorth: List<NetWorthPoint>,
    axisValues: List<Long>?,
    tap: Offset,
    width: Float,
    height: Float,
    density: Float,
): ReportChartSelection? {
    val inset = 8f * density
    val chartTop = inset
    val chartBottom = height - inset
    val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
    if (kind == ReportKind.SPENDING_BY_CATEGORY) return categoryRingSelectionAt(categories, tap, width, height, inset, density)
    if (kind == ReportKind.NET_WORTH) {
        return netWorthSelectionAt(netWorth, axisValues, tap, width, inset, chartBottom, chartHeight, density)
    }
    if (kind != ReportKind.SPENDING_OVER_TIME && kind != ReportKind.INCOME_AND_SPENDING) return null
    if (monthly.isEmpty()) return null
    if (kind == ReportKind.SPENDING_OVER_TIME) {
        return spendingBarSelectionAt(monthly, axisValues, tap, width, chartBottom, chartHeight)
    }
    return incomeAndSpendingSelectionAt(monthly, axisValues, tap, width, chartBottom, chartHeight)
}

private fun categoryRingSelectionAt(
    categories: List<CategorySpendingPoint>,
    tap: Offset,
    width: Float,
    height: Float,
    inset: Float,
    density: Float,
): ReportChartSelection? {
    val total = categories.sumOf { it.spendingMinor.coerceAtLeast(0L) }
    if (total <= 0L) return null
    val diameter = minOf(width, height) - inset * 2f
    val centerX = width / 2f
    val centerY = height / 2f
    val dx = tap.x - centerX
    val dy = tap.y - centerY
    val radius = sqrt(dx * dx + dy * dy)
    val ringRadius = diameter / 2f
    if (radius !in (ringRadius - 28f * density / 2f - 10f * density)..(ringRadius + 28f * density / 2f + 10f * density)) return null
    val degrees = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI + 450.0) % 360.0
    var segmentStart = 0.0
    categories.forEachIndexed { index, point ->
        val amount = point.spendingMinor.coerceAtLeast(0L)
        val sweep = amount.toDouble() * 360.0 / total.toDouble()
        if (sweep > 0.0 && degrees >= segmentStart && degrees < segmentStart + sweep) {
            return ReportChartSelection(index)
        }
        segmentStart += sweep
    }
    return null
}

private fun netWorthSelectionAt(
    netWorth: List<NetWorthPoint>,
    axisValues: List<Long>?,
    tap: Offset,
    width: Float,
    inset: Float,
    chartBottom: Float,
    chartHeight: Float,
    density: Float,
): ReportChartSelection? {
    val values = netWorth.map(NetWorthPoint::allAccountsMinor)
    if (values.isEmpty()) return null
    val (observedMin, observedMax) = netWorthValueRange(values)
    val minimum = axisValues?.lastOrNull()?.toDouble() ?: observedMin
    val maximum = axisValues?.firstOrNull()?.toDouble() ?: observedMax
    val spread = (maximum - minimum).coerceAtLeast(1.0)
    val points =
        values.mapIndexed { index, value ->
            val x = if (values.size == 1) width / 2f else inset + (width - inset * 2f) * index / (values.size - 1)
            val y = chartBottom - chartHeight * ((value - minimum) / spread).toFloat()
            Offset(x, y)
        }
    val nearest =
        points.indices.minByOrNull { index ->
            val dx = tap.x - points[index].x
            val dy = tap.y - points[index].y
            dx * dx + dy * dy
        } ?: return null
    val dx = tap.x - points[nearest].x
    val dy = tap.y - points[nearest].y
    val hitRadius = 28f * density
    return if (dx * dx + dy * dy <= hitRadius * hitRadius) ReportChartSelection(nearest) else null
}

private fun spendingBarSelectionAt(
    monthly: List<MonthlyMoneyPoint>,
    axisValues: List<Long>?,
    tap: Offset,
    width: Float,
    chartBottom: Float,
    chartHeight: Float,
): ReportChartSelection? {
    val values = monthly.map(MonthlyMoneyPoint::spendingMinor)
    val maxValue =
        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble()
            ?: maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
    val slot = width / monthly.size.coerceAtLeast(1)
    val barWidth = slot * 0.5f
    val index = (tap.x / slot).toInt().coerceIn(0, monthly.lastIndex)
    val value = monthly[index].spendingMinor.coerceAtLeast(0L)
    val barHeight = (chartHeight * (value.toDouble() / maxValue).toFloat()).coerceAtLeast(2f)
    val barLeft = index * slot + (slot - barWidth) / 2f
    return if (tap.x in barLeft..(barLeft + barWidth) && tap.y in (chartBottom - barHeight)..chartBottom) {
        ReportChartSelection(index)
    } else {
        null
    }
}

private fun incomeAndSpendingSelectionAt(
    monthly: List<MonthlyMoneyPoint>,
    axisValues: List<Long>?,
    tap: Offset,
    width: Float,
    chartBottom: Float,
    chartHeight: Float,
): ReportChartSelection? {
    val selectedMax =
        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble() ?: monthly
            .maxOfOrNull { point -> maxOf(point.incomeMinor, point.spendingMinor) }
            ?.coerceAtLeast(1L)
            ?.toDouble() ?: 1.0
    val slot = width / monthly.size.coerceAtLeast(1)
    val barWidth = slot * 0.28f
    val left = (tap.x / slot).toInt().coerceIn(0, monthly.lastIndex) * slot + slot * 0.2f
    val index = (tap.x / slot).toInt().coerceIn(0, monthly.lastIndex)
    val point = monthly[index]
    val incomeHeight = (chartHeight * (point.incomeMinor.coerceAtLeast(0L).toDouble() / selectedMax).toFloat()).coerceAtLeast(2f)
    val spendingHeight = (chartHeight * (point.spendingMinor.coerceAtLeast(0L).toDouble() / selectedMax).toFloat()).coerceAtLeast(2f)
    val incomeLeft = left
    val spendingLeft = left + barWidth + slot * 0.08f
    return when {
        tap.x in incomeLeft..(incomeLeft + barWidth) && tap.y in (chartBottom - incomeHeight)..chartBottom ->
            ReportChartSelection(index, income = true)
        tap.x in spendingLeft..(spendingLeft + barWidth) && tap.y in (chartBottom - spendingHeight)..chartBottom ->
            ReportChartSelection(index, income = false)
        else -> null
    }
}

internal fun reportSelectionAnchor(
    kind: ReportKind,
    selection: ReportChartSelection,
    monthly: List<MonthlyMoneyPoint>,
    netWorth: List<NetWorthPoint>,
    axisValues: List<Long>?,
    width: Float,
    height: Float,
    inset: Float,
): Offset? {
    val chartBottom = height - inset
    val chartHeight = (chartBottom - inset).coerceAtLeast(1f)
    when (kind) {
        ReportKind.SPENDING_OVER_TIME -> {
            val values = monthly.map(MonthlyMoneyPoint::spendingMinor)
            val value = values.getOrNull(selection.index)?.coerceAtLeast(0L) ?: return null
            val maxValue =
                axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble()
                    ?: maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
            val slot = width / values.size.coerceAtLeast(1)
            val barHeight = chartHeight * (value.toDouble() / maxValue).toFloat()
            return Offset(selection.index * slot + slot / 2f, chartBottom - barHeight)
        }
        ReportKind.INCOME_AND_SPENDING -> {
            val point = monthly.getOrNull(selection.index) ?: return null
            val maxValue =
                axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble()
                    ?: (monthly.maxOfOrNull { maxOf(it.incomeMinor, it.spendingMinor) }?.coerceAtLeast(1L)?.toDouble() ?: 1.0)
            val slot = width / monthly.size.coerceAtLeast(1)
            val barWidth = slot * 0.28f
            val barLeft =
                selection.index * slot + slot * 0.2f +
                    if (selection.income) 0f else barWidth + slot * 0.08f
            val value = if (selection.income) point.incomeMinor else point.spendingMinor
            val barHeight = chartHeight * (value.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
            return Offset(barLeft + barWidth / 2f, chartBottom - barHeight)
        }
        ReportKind.NET_WORTH -> {
            val values = netWorth.map(NetWorthPoint::allAccountsMinor)
            val value = values.getOrNull(selection.index)?.toDouble() ?: return null
            val (observedMin, observedMax) = netWorthValueRange(values)
            val minimum = axisValues?.lastOrNull()?.toDouble() ?: observedMin
            val maximum = axisValues?.firstOrNull()?.toDouble() ?: observedMax
            val spread = (maximum - minimum).coerceAtLeast(1.0)
            val x = if (values.size == 1) width / 2f else inset + (width - inset * 2f) * selection.index / (values.size - 1)
            val y = chartBottom - chartHeight * ((value - minimum) / spread).toFloat()
            return Offset(x, y)
        }
        else -> return null
    }
}
