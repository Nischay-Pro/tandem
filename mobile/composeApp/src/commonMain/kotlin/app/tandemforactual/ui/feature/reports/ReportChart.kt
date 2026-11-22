@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
internal fun ReportSummary(
    kind: ReportKind,
    data: ReportDataBundle,
    compact: Boolean,
    selectedNetWorthIndex: Int? = null,
) {
    if (kind == ReportKind.NET_WORTH) {
        val point = selectedNetWorthIndex?.let(data.netWorth::getOrNull) ?: data.netWorth.lastOrNull() ?: return
        val assets = point.allAccountsAssetsMinor
        val debts = point.allAccountsDebtsMinor
        val netWorth = assets - debts
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                amountAnnotatedString(
                    formatReportCurrency(netWorth, data.displaySettings),
                    data.displaySettings?.currencyFractionDigits ?: 2,
                ),
                color = netAmountColor(netWorth),
                fontSize = if (compact) TandemTypography.amount else TandemTypography.amountField,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Icon(Lucide.TrendingUp, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.positive)
                Text(
                    amountAnnotatedString(
                        "Assets: ${formatReportCurrency(assets, data.displaySettings)}",
                        data.displaySettings?.currencyFractionDigits ?: 2,
                    ),
                    color = tandemPalette.positive,
                    fontSize = TandemTypography.caption,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Icon(Lucide.CreditCard, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.negative)
                Text(
                    amountAnnotatedString(
                        "Debts: ${formatReportCurrency(debts, data.displaySettings)}",
                        data.displaySettings?.currencyFractionDigits ?: 2,
                    ),
                    color = tandemPalette.negative,
                    fontSize = TandemTypography.caption,
                )
            }
        }
    } else {
        Text(
            amountAnnotatedString(reportSummary(kind, data, "All accounts"), data.displaySettings?.currencyFractionDigits ?: 2),
            color = reportSummaryColor(kind, data),
            fontSize = if (compact) TandemTypography.body else TandemTypography.action,
            lineHeight = if (compact) TandemTypography.bodyLineHeight else TandemTypography.actionLineHeight,
            fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
internal fun ReportChart(
    kind: ReportKind,
    data: ReportDataBundle,
    height: Dp,
    compact: Boolean,
    window: ReportWindow,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onNetWorthSelectionChange: (Int?) -> Unit = {},
) {
    when (kind) {
        ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING, ReportKind.NET_WORTH ->
            TimeSeriesReportChart(kind, data, height, onNetWorthSelectionChange)
        ReportKind.BUDGETED_AND_SPENT ->
            BudgetedSpentChart(data, if (compact) 5 else null) { categoryId, category ->
                onOpenCategoryActivity(categoryId, category, window)
            }
        else -> ReportCanvas(kind, data, "All accounts", Modifier.fillMaxWidth().height(height))
    }
}

@Composable
internal fun TimeSeriesReportChart(
    kind: ReportKind,
    data: ReportDataBundle,
    height: Dp,
    onNetWorthSelectionChange: (Int?) -> Unit = {},
) {
    val monthly = monthlyPoints(kind, data)
    val monthIds = if (kind == ReportKind.NET_WORTH) data.netWorth.map(NetWorthPoint::monthId) else monthly.map(MonthlyMoneyPoint::monthId)
    val axisValues = reportChartAxisValues(kind, data)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val yAxisWidth = 72.dp
        val axisGap = TandemSpacing.small
        val viewportWidth = maxOf(1.dp, maxWidth - yAxisWidth - axisGap)
        val slotWidth = viewportWidth / 6
        val chartWidth = maxOf(viewportWidth, slotWidth * monthIds.size.coerceAtLeast(1))
        val scrollState = rememberScrollState()
        val selectionCallback by rememberUpdatedState(onNetWorthSelectionChange)
        LaunchedEffect(monthIds, chartWidth) {
            withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
        LaunchedEffect(scrollState) {
            var previousScroll = scrollState.value
            snapshotFlow { scrollState.value }.collect { currentScroll ->
                if (currentScroll != previousScroll) {
                    previousScroll = currentScroll
                    selectionCallback(null)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(axisGap),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    Modifier.width(yAxisWidth).height(height).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    axisValues.forEach { value ->
                        Text(
                            reportAxisValue(value, axisValues.maxOfOrNull { kotlin.math.abs(it) } ?: 1L, data.displaySettings),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                ) {
                    ReportCanvas(
                        kind,
                        data,
                        netWorthScope = "All accounts",
                        modifier = Modifier.width(chartWidth).height(height),
                        showMonthlyGuides = true,
                        axisValues = axisValues,
                        onNetWorthSelectionChange = onNetWorthSelectionChange,
                        calloutViewportStartPx = scrollState.value.toFloat(),
                        calloutViewportWidthPx =
                            with(androidx.compose.ui.platform.LocalDensity.current) {
                                viewportWidth.toPx()
                            },
                    )
                    ReportMonthAxis(monthIds, chartWidth, chartWidth / monthIds.size.coerceAtLeast(1))
                }
            }
        }
    }
}

internal fun reportAxisValue(
    amountMinor: Long,
    maximumMinor: Long,
    displaySettings: BudgetDisplaySettings?,
): String {
    val fractionDigits = displaySettings?.currencyFractionDigits ?: 2
    val thousandThreshold = 1_000L * listOf(1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L).getOrElse(fractionDigits) { 100L }
    if (maximumMinor.toDouble() >= thousandThreshold.toDouble()) {
        if (amountMinor == 0L) return "0k"
        return "${formatReportCurrency(amountMinor / 1_000, displaySettings)}k"
    }
    return formatReportCurrency(amountMinor, displaySettings)
}

internal fun reportChartAxisValues(
    kind: ReportKind,
    data: ReportDataBundle,
): List<Long> {
    if (kind == ReportKind.NET_WORTH) {
        val values = data.netWorth.map(NetWorthPoint::allAccountsMinor)
        if (values.isEmpty()) return listOf(0L, 0L, 0L, 0L, 0L)
        val (minimum, maximum) = netWorthValueRange(values)
        return roundedReportAxisValues(minimum, maximum)
    }
    val values =
        monthlyPoints(kind, data).map { point ->
            if (kind == ReportKind.SPENDING_OVER_TIME) point.spendingMinor else maxOf(point.incomeMinor, point.spendingMinor)
        }
    val maximum = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    return roundedReportAxisValues(0.0, maximum.toDouble())
}

internal fun netWorthValueRange(values: List<Long>): Pair<Double, Double> {
    val observedMin = (values.minOrNull() ?: 0L).toDouble()
    val observedMax = (values.maxOrNull() ?: 0L).toDouble()
    val observedSpread = observedMax - observedMin
    val padding = if (observedSpread == 0.0) maxOf(abs(observedMin) * 0.05, 1.0) else maxOf(observedSpread * 0.08, 1.0)
    return (observedMin - padding) to (observedMax + padding)
}

@Composable
internal fun ReportMonthAxis(
    monthIds: List<String>,
    width: Dp,
    slotWidth: Dp,
) {
    val currentMonthId = platformTodayIsoDate().take(7)
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically) {
        monthIds.forEach { monthId ->
            val isCurrentMonth = monthId == currentMonthId
            Text(
                reportMonthAxisLabel(monthId),
                modifier = Modifier.width(slotWidth),
                color = if (isCurrentMonth) tandemPalette.accent else tandemPalette.muted,
                fontSize = TandemTypography.caption,
                fontWeight = if (isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

internal fun roundedReportAxisValues(
    minimum: Double,
    maximum: Double,
    intervalCount: Int = 4,
): List<Long> {
    require(intervalCount > 0)
    val low = minOf(minimum, maximum)
    val high = maxOf(minimum, maximum)
    if (low == 0.0 && high == 0.0) return List(intervalCount + 1) { 0L }

    val rawStep = ((high - low) / intervalCount).coerceAtLeast(1.0)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalizedStep = rawStep / magnitude
    val roundedStep =
        when {
            normalizedStep <= 1.0 -> 1.0
            normalizedStep <= 2.0 -> 2.0
            normalizedStep <= 2.5 -> 2.5
            normalizedStep <= 5.0 -> 5.0
            else -> 10.0
        } * magnitude
    val step = roundedStep.coerceAtLeast(1.0)
    val first = floor(low / step) * step
    val last = ceil(high / step) * step
    val tickCount = ((last - first) / step).toInt() + 1
    return (0 until tickCount).map { index -> (last - index * step).toLong() }
}
