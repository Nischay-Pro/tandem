@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.theme.TandemPalette
import app.tandemforactual.ui.design.theme.TandemShapes

private class ReportChartFrame(
    val inset: Float,
    val chartTop: Float,
    val chartBottom: Float,
    val chartHeight: Float,
)

internal fun DrawScope.drawReport(
    kind: ReportKind,
    data: ReportDataBundle,
    monthly: List<MonthlyMoneyPoint>,
    chartCategories: List<CategorySpendingPoint>,
    selection: ReportChartSelection?,
    axisValues: List<Long>?,
    showMonthlyGuides: Boolean,
    palette: TandemPalette,
) {
    val inset = ReportChartInset.toPx()
    val chartTop = inset
    val chartBottom = size.height - inset
    val frame = ReportChartFrame(inset, chartTop, chartBottom, (chartBottom - chartTop).coerceAtLeast(1f))
    if (showMonthlyGuides) {
        val guideCount = axisValues?.size?.coerceAtLeast(2) ?: 5
        (0 until guideCount).forEach { tick ->
            val y = frame.chartTop + frame.chartHeight * tick / (guideCount - 1).toFloat()
            drawLine(palette.border, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
        }
    }
    when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> drawCategoryRing(chartCategories, selection, frame, palette)
        ReportKind.SPENDING_OVER_TIME -> drawSpendingBars(monthly, selection, axisValues, frame, palette)
        ReportKind.INCOME_AND_SPENDING -> drawIncomeAndSpendingBars(monthly, selection, axisValues, frame, palette)
        ReportKind.BUDGETED_AND_SPENT -> Unit
        ReportKind.NET_WORTH -> drawNetWorthLine(data, selection, axisValues, frame, palette)
    }
}

private fun DrawScope.drawCategoryRing(
    chartCategories: List<CategorySpendingPoint>,
    selection: ReportChartSelection?,
    frame: ReportChartFrame,
    palette: TandemPalette,
) {
    val inset = frame.inset
    val values = chartCategories.map { it.spendingMinor.coerceAtLeast(0L) }
    val total = values.sum()
    val diameter = minOf(size.width, size.height) - inset * 2
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    if (total <= 0L) {
        drawArc(palette.border, -90f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(26.dp.toPx()))
        return
    }
    var startAngle = -90f
    values.forEachIndexed { index, value ->
        val sweep = value.toDouble().div(total).toFloat() * 360f
        if (sweep <= 0f) return@forEachIndexed
        drawArc(
            color =
                if (selection == null || selection.index == index) {
                    categorySliceColor(index, chartCategories[index].categoryId == OTHER_CATEGORY_CHART_ID, palette)
                } else {
                    palette.muted.copy(alpha = 0.3f + (index % 4) * 0.13f)
                },
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Butt),
        )
        startAngle += sweep
    }
}

/** A distinct colour for each named slice, up to the eight the ring can show, with Other always muted. */
internal fun categorySliceColor(
    index: Int,
    isOther: Boolean,
    palette: TandemPalette,
): Color = if (isOther) palette.muted else palette.categories[index.mod(palette.categories.size)]

/** Spending bars are striped so they stay apart from income bars without relying on colour. */
internal fun DrawScope.drawHatchedRoundRect(
    color: Color,
    topLeft: Offset,
    size: Size,
    cornerRadius: CornerRadius,
) {
    drawRoundRect(color.copy(alpha = 0.3f), topLeft, size, cornerRadius)
    val outline = Path().apply { addRoundRect(RoundRect(Rect(topLeft, size), cornerRadius)) }
    val step = 5.dp.toPx()
    clipPath(outline) {
        var x = topLeft.x - size.height
        while (x < topLeft.x + size.width) {
            drawLine(color, Offset(x, topLeft.y + size.height), Offset(x + size.height, topLeft.y), 1.5.dp.toPx())
            x += step
        }
    }
    drawRoundRect(color, topLeft, size, cornerRadius, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.drawSpendingBars(
    monthly: List<MonthlyMoneyPoint>,
    selection: ReportChartSelection?,
    axisValues: List<Long>?,
    frame: ReportChartFrame,
    palette: TandemPalette,
) {
    val values = monthly.map(MonthlyMoneyPoint::spendingMinor)
    val maxValue =
        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble()
            ?: maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
    val barSlot = size.width / values.size.coerceAtLeast(1)
    val barWidth = barSlot * 0.5f
    values.forEachIndexed { index, value ->
        val barHeight = frame.chartHeight * (value.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
        val barColor =
            if (selection?.let { it.index == index && !it.income } != false) {
                palette.negative
            } else {
                palette.outline
            }
        drawRoundRect(
            barColor,
            Offset(index * barSlot + (barSlot - barWidth) / 2f, frame.chartBottom - barHeight),
            Size(barWidth, barHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
    }
    val average = monthlySpendingAverage(monthly)?.averageMinor?.toDouble() ?: return
    val averageY = frame.chartBottom - frame.chartHeight * (average / maxValue).toFloat()
    drawLine(palette.syncing, Offset(frame.inset, averageY), Offset(size.width - frame.inset, averageY), 2.dp.toPx())
}

private fun DrawScope.drawIncomeAndSpendingBars(
    monthly: List<MonthlyMoneyPoint>,
    selection: ReportChartSelection?,
    axisValues: List<Long>?,
    frame: ReportChartFrame,
    palette: TandemPalette,
) {
    val chartBottom = frame.chartBottom
    val maxValue =
        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble() ?: (
            monthly
                .maxOfOrNull { maxOf(it.incomeMinor, it.spendingMinor) }
                ?.coerceAtLeast(1L)
                ?.toDouble() ?: 1.0
        )
    val slot = size.width / monthly.size.coerceAtLeast(1)
    val barWidth = slot * 0.28f
    monthly.forEachIndexed { index, value ->
        val left = index * slot + slot * 0.2f
        val incomeHeight = frame.chartHeight * (value.incomeMinor.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
        val spendingHeight = frame.chartHeight * (value.spendingMinor.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
        val incomeSelected = selection?.let { it.index == index && it.income } != false
        val spendingSelected = selection?.let { it.index == index && !it.income } != false
        drawRoundRect(
            if (incomeSelected) palette.positive else palette.outline,
            Offset(left, chartBottom - incomeHeight),
            Size(barWidth, incomeHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
        drawHatchedRoundRect(
            if (spendingSelected) palette.negative else palette.outline,
            Offset(left + barWidth + slot * 0.08f, chartBottom - spendingHeight),
            Size(barWidth, spendingHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
    }
}

private fun DrawScope.drawNetWorthLine(
    data: ReportDataBundle,
    selection: ReportChartSelection?,
    axisValues: List<Long>?,
    frame: ReportChartFrame,
    palette: TandemPalette,
) {
    val values = data.netWorth.map(NetWorthPoint::allAccountsMinor)
    val points = netWorthPointOffsets(values, axisValues, size.width, frame.chartTop, frame.chartBottom)
    if (points.isEmpty()) return
    val (minimum, maximum) = netWorthScale(values, axisValues)
    val baseline = reportZeroY(minimum, maximum, frame.chartTop, frame.chartBottom)
    if (minimum < 0.0 && maximum > 0.0) {
        drawLine(palette.muted.copy(alpha = 0.6f), Offset(frame.inset, baseline), Offset(size.width - frame.inset, baseline), 1.dp.toPx())
    }
    val line =
        Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    val fill =
        Path().apply {
            moveTo(points.first().x, baseline)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, baseline)
            close()
        }
    drawPath(fill, palette.accent.copy(alpha = 0.18f))
    drawPath(line, palette.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    points.forEachIndexed { index, point ->
        val isSelected = selection?.index == index
        drawCircle(palette.accent, if (isSelected) 7.dp.toPx() else 4.dp.toPx(), point)
    }
}
