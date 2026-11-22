@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
    val inset = 8.dp.toPx()
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
        ReportKind.BUDGETED_AND_SPENT -> drawBudgetedSpentBars(data, frame, palette)
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
    val values = chartCategories.map(CategorySpendingPoint::spendingMinor)
    val total = values.sum()
    val diameter = minOf(size.width, size.height) - inset * 2
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    if (total <= 0L) {
        drawArc(palette.border, -90f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(26.dp.toPx()))
    } else {
        val colors =
            listOf(
                palette.accent,
                palette.syncing,
                palette.positive,
                palette.warning,
                palette.negative,
                palette.muted,
            )
        var startAngle = -90f
        values.forEachIndexed { index, value ->
            val sweep = value.toDouble().div(total).toFloat() * 360f
            drawArc(
                color =
                    if (selection == null || selection.index == index) {
                        colors[index % colors.size]
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
                palette.muted.copy(alpha = 0.48f)
            }
        drawRoundRect(
            barColor,
            Offset(index * barSlot + (barSlot - barWidth) / 2f, frame.chartBottom - barHeight),
            Size(barWidth, barHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
    }
    val average = values.averageLong().toDouble()
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
            if (incomeSelected) palette.positive else palette.muted.copy(alpha = 0.48f),
            Offset(left, chartBottom - incomeHeight),
            Size(barWidth, incomeHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
        drawRoundRect(
            if (spendingSelected) palette.negative else palette.muted.copy(alpha = 0.48f),
            Offset(left + barWidth + slot * 0.08f, chartBottom - spendingHeight),
            Size(barWidth, spendingHeight.coerceAtLeast(2f)),
            CornerRadius(TandemShapes.small.toPx()),
        )
    }
}

private fun DrawScope.drawBudgetedSpentBars(
    data: ReportDataBundle,
    frame: ReportChartFrame,
    palette: TandemPalette,
) {
    val inset = frame.inset
    val values = data.budgetedSpent.take(8)
    val maxValue = values.maxOfOrNull { maxOf(it.budgetedMinor, it.spentMinor) }?.coerceAtLeast(1L)?.toDouble() ?: 1.0
    val rowHeight = frame.chartHeight / values.size.coerceAtLeast(1)
    val labelInset = inset + rowHeight * 0.24f
    val trackWidth = size.width - inset * 2
    values.forEachIndexed { index, value ->
        val y = frame.chartTop + index * rowHeight + labelInset
        val budgetWidth = trackWidth * (value.budgetedMinor.toDouble() / maxValue).toFloat()
        val spentWidth = trackWidth * (value.spentMinor.toDouble() / maxValue).toFloat()
        drawRoundRect(
            palette.positive,
            Offset(inset, y),
            Size(budgetWidth.coerceAtLeast(1f), 5.dp.toPx()),
            CornerRadius(TandemShapes.small.toPx()),
        )
        drawRoundRect(
            palette.negative,
            Offset(inset, y + 8.dp.toPx()),
            Size(spentWidth.coerceAtLeast(1f), 7.dp.toPx()),
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
    val inset = frame.inset
    val chartBottom = frame.chartBottom
    val values = data.netWorth.map(NetWorthPoint::allAccountsMinor)
    if (values.isEmpty()) return
    val (observedMin, observedMax) = netWorthValueRange(values)
    val minValue = axisValues?.lastOrNull()?.toDouble() ?: observedMin
    val maxValue = axisValues?.firstOrNull()?.toDouble() ?: observedMax
    val spread = (maxValue - minValue).coerceAtLeast(1.0)
    val points =
        values.mapIndexed { index, value ->
            val x =
                if (values.size == 1) {
                    size.width / 2f
                } else {
                    inset + (size.width - inset * 2) * index / (values.size - 1)
                }
            val y = chartBottom - frame.chartHeight * ((value - minValue) / spread).toFloat()
            Offset(x, y)
        }
    val line =
        Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    val fill =
        Path().apply {
            moveTo(points.first().x, chartBottom)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, chartBottom)
            close()
        }
    drawPath(fill, palette.accent.copy(alpha = 0.18f))
    drawPath(line, palette.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    points.forEachIndexed { index, point ->
        val isSelected = selection?.index == index
        drawCircle(palette.accent, if (isSelected) 7.dp.toPx() else 4.dp.toPx(), point)
    }
}
