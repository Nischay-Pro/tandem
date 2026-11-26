@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import kotlinx.coroutines.flow.drop

@Composable
internal fun ReportCanvas(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    modifier: Modifier,
    showMonthlyGuides: Boolean = false,
    axisValues: List<Long>? = null,
    onNetWorthSelectionChange: (Int?) -> Unit = {},
    calloutViewportStart: () -> Float = { 0f },
    calloutViewportWidthPx: Float? = null,
) {
    val palette = tandemPalette
    val monthly = monthlyPoints(kind, data)
    val chartCategories = remember(data.categories) { categoryChartPoints(data.categories) }
    var selection by remember(kind, monthly, chartCategories, data.netWorth) {
        mutableStateOf<ReportChartSelection?>(null)
    }
    val selectionCallback by rememberUpdatedState(onNetWorthSelectionChange)
    val viewportStart by rememberUpdatedState(calloutViewportStart)
    LaunchedEffect(kind, monthly, chartCategories, data.netWorth) { snapshotFlow { viewportStart() }.drop(1).collect { selection = null } }
    val selectedCategory = selection?.takeIf { kind == ReportKind.SPENDING_BY_CATEGORY }?.let { chartCategories.getOrNull(it.index) }
    val callout = reportCallout(kind, selection, selectedCategory, monthly, chartCategories, data)
    val description = reportChartDescription(kind, data, netWorthScope)
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val chartWidth = chartSize.width.toFloat()
    val chartHeight = chartSize.height.toFloat()
    val calloutOffset: (() -> IntOffset)? =
        selection
            ?.takeIf { selectedCategory == null }
            ?.let { reportSelectionAnchor(kind, it, monthly, data.netWorth, axisValues, chartWidth, chartHeight, gap) }
            ?.let { anchor -> { reportCalloutOffset(anchor, chartSize, calloutSize, gap, viewportStart(), calloutViewportWidthPx) } }
    Box(modifier.onSizeChanged { chartSize = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        if (callout == null) description else "$description Selected: $callout"
                }.pointerInput(kind, monthly, chartCategories, data.netWorth, axisValues) {
                    detectTapGestures { offset ->
                        val nextSelection =
                            reportSelectionAt(
                                kind,
                                chartCategories,
                                monthly,
                                data.netWorth,
                                axisValues,
                                offset,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                density.density,
                            )
                        selection = nextSelection
                        if (kind == ReportKind.NET_WORTH) selectionCallback(nextSelection?.index)
                    }
                },
        ) {
            drawReport(kind, data, monthly, chartCategories, selection, axisValues, showMonthlyGuides, palette)
        }
        if (callout != null) {
            ReportCallout(
                callout = callout,
                fractionDigits = data.displaySettings?.currencyFractionDigits ?: 2,
                offset = calloutOffset,
                onSizeChange = { calloutSize = it },
                modifier = Modifier.align(if (selectedCategory != null) Alignment.Center else Alignment.TopStart),
            )
        }
    }
}

/** The pill that shows the amount of the selected chart element. */
@Composable
private fun ReportCallout(
    callout: String,
    fractionDigits: Int,
    offset: (() -> IntOffset)?,
    onSizeChange: (IntSize) -> Unit,
    modifier: Modifier,
) {
    Text(
        amountAnnotatedString(callout, fractionDigits),
        modifier =
            modifier
                .then(if (offset != null) Modifier.offset { offset() } else Modifier)
                .onSizeChanged(onSizeChange)
                .clip(RoundedCornerShape(TandemShapes.pill))
                .background(tandemPalette.raised)
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
        color = tandemPalette.text,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

private fun reportCallout(
    kind: ReportKind,
    selection: ReportChartSelection?,
    selectedCategory: CategorySpendingPoint?,
    monthly: List<MonthlyMoneyPoint>,
    chartCategories: List<CategorySpendingPoint>,
    data: ReportDataBundle,
): String? {
    val selectedValue =
        selection?.let { selected ->
            when (kind) {
                ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING ->
                    monthly.getOrNull(selected.index)?.let { point ->
                        if (kind == ReportKind.SPENDING_OVER_TIME || !selected.income) point.spendingMinor else point.incomeMinor
                    }
                ReportKind.NET_WORTH -> data.netWorth.getOrNull(selected.index)?.allAccountsMinor
                else -> null
            }
        }
    val categoryTotal = chartCategories.sumOf { it.spendingMinor.coerceAtLeast(0L) }
    return selectedCategory?.let { point ->
        "${categoryPercentage(
            point.spendingMinor,
            categoryTotal,
        )} · ${formatReportCurrency(positiveAmount(point.spendingMinor), data.displaySettings)}"
    } ?: selectedValue?.let {
        if (kind == ReportKind.NET_WORTH) {
            formatReportCurrency(it, data.displaySettings)
        } else {
            formatReportCurrency(positiveAmount(it), data.displaySettings)
        }
    }
}

private fun reportCalloutOffset(
    anchor: Offset,
    chartSize: IntSize,
    calloutSize: IntSize,
    gap: Float,
    calloutViewportStartPx: Float,
    calloutViewportWidthPx: Float?,
): IntOffset {
    val visibleLeft = calloutViewportStartPx.coerceIn(0f, chartSize.width.toFloat())
    val visibleRight =
        (visibleLeft + (calloutViewportWidthPx ?: chartSize.width.toFloat()))
            .coerceAtMost(chartSize.width.toFloat())
            .coerceAtLeast(visibleLeft)
    val rightLimit = (visibleRight - calloutSize.width).coerceAtLeast(visibleLeft)
    val x = (anchor.x - calloutSize.width / 2f).coerceIn(visibleLeft, rightLimit)
    val above = anchor.y - calloutSize.height - gap
    val y =
        if (above >= 0f) {
            above
        } else {
            (anchor.y + gap).coerceAtMost((chartSize.height - calloutSize.height).coerceAtLeast(0).toFloat())
        }
    return IntOffset(x.toInt(), y.toInt())
}
