@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.transactions.currentMonthId
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import kotlinx.coroutines.flow.flowOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal data class ReportDataBundle(
    val categories: List<CategorySpendingPoint>,
    val monthly: List<MonthlyMoneyPoint>,
    val incomeAndSpending: List<MonthlyMoneyPoint>,
    val budgetedSpent: List<BudgetedSpentPoint>,
    val netWorth: List<NetWorthPoint>,
    val displaySettings: BudgetDisplaySettings?,
)

internal class ReportsDataCache {
    private val reports = mutableMapOf<String, ReportDataBundle>()

    fun get(key: String): ReportDataBundle? = reports[key]

    fun put(
        key: String,
        value: ReportDataBundle,
    ) {
        reports[key] = value
        if (reports.size > 24) reports.remove(reports.keys.first())
    }
}

internal data class ReportChartSelection(
    val index: Int,
    val income: Boolean = false,
)

@Composable
internal fun rememberReportData(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    window: ReportWindow,
    netWorthWindow: ReportWindow,
    reportsDataCache: ReportsDataCache,
): ReportDataBundle {
    val budgetId = state.selectedBudgetId
    val categoryNames =
        remember(mobileSession.categoryRecords) {
            mobileSession.categoryRecords.mapNotNull { record -> record.recordName()?.let { record.recordId to it } }.toMap()
        }
    val categoryFlow =
        remember(mobileSession.reportViewModels, budgetId, window, categoryNames) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.spendingByCategory.observe(budgetId, window, categoryNames)
            } else {
                flowOf(emptyList())
            }
        }
    val monthlyFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.spendingOverTime.observe(budgetId, window)
            } else {
                flowOf(emptyList())
            }
        }
    val incomeAndSpendingFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.incomeAndSpending.observe(budgetId, window)
            } else {
                flowOf(emptyList())
            }
        }
    val budgetedFlow =
        remember(mobileSession.reportViewModels, budgetId, window, categoryNames) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.budgetedAndSpent.observe(budgetId, window, categoryNames)
            } else {
                flowOf(emptyList())
            }
        }
    val today = platformTodayIsoDate()
    val netWorthFlow =
        remember(mobileSession.reportViewModels, budgetId, netWorthWindow, today, accounts) {
            val viewModels = mobileSession.reportViewModels
            if (budgetId.isNotBlank() && viewModels != null) {
                viewModels.netWorth.observe(budgetId, netWorthWindow.monthIds, today, accounts)
            } else {
                flowOf(emptyList())
            }
        }
    val cacheKey =
        "$budgetId|${window.startDate}|${window.throughDate}|${window.allDates}|" +
            "${netWorthWindow.monthIds}|${categoryNames.hashCode()}|${accounts.hashCode()}"
    val cachedData = reportsDataCache.get(cacheKey)
    val categoryInitial = emptyList<CategorySpendingPoint>()
    val monthlyInitial = emptyList<MonthlyMoneyPoint>()
    val incomeAndSpendingInitial = emptyList<MonthlyMoneyPoint>()
    val budgetedInitial = emptyList<BudgetedSpentPoint>()
    val netWorthInitial = emptyList<NetWorthPoint>()
    val categoriesBySpend by categoryFlow.collectAsState(cachedData?.categories ?: categoryInitial)
    val monthly by monthlyFlow.collectAsState(cachedData?.monthly ?: monthlyInitial)
    val incomeAndSpending by incomeAndSpendingFlow.collectAsState(cachedData?.incomeAndSpending ?: incomeAndSpendingInitial)
    val budgetedSpent by budgetedFlow.collectAsState(cachedData?.budgetedSpent ?: budgetedInitial)
    val netWorth by netWorthFlow.collectAsState(cachedData?.netWorth ?: netWorthInitial)
    val data = ReportDataBundle(categoriesBySpend, monthly, incomeAndSpending, budgetedSpent, netWorth, state.budgetDisplaySettings)
    SideEffect {
        if (reportsDataCache.get(cacheKey) != data) reportsDataCache.put(cacheKey, data)
    }
    return data
}

@Composable
internal fun ReportPeriodSelector(
    monthId: String,
    mode: ReportPeriodMode,
    rangePreset: ReportRangePreset,
    availableMonthIds: List<String>,
    onMonthChange: (String) -> Unit,
    onModeChange: (ReportPeriodMode) -> Unit,
    onRangePresetChange: (ReportRangePreset) -> Unit,
) {
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Text("Period", color = tandemPalette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
            SegmentedSelector(
                listOf("Month" to (mode == ReportPeriodMode.MONTH), "Range" to (mode == ReportPeriodMode.RANGE)),
            ) { index -> onModeChange(if (index == 0) ReportPeriodMode.MONTH else ReportPeriodMode.RANGE) }
            if (mode == ReportPeriodMode.RANGE) {
                ReportRangePresetDropdown(rangePreset, onRangePresetChange)
                val rangeWindow = createReportWindow(monthId, mode, rangePreset, platformTodayIsoDate(), availableMonthIds)
                when (rangePreset) {
                    ReportRangePreset.LAST_3_MONTHS,
                    ReportRangePreset.LAST_6_MONTHS,
                    ReportRangePreset.LAST_12_MONTHS,
                    ReportRangePreset.LAST_3_YEARS,
                    -> {
                        Text(
                            "End month",
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            fontWeight = FontWeight.SemiBold,
                        )
                        PeriodStepper(
                            title = reportMonthLabel(monthId),
                            previousLabel = "Previous end month",
                            nextLabel = "Next end month",
                            onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                            onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
                        )
                        rangeWindow?.let { window ->
                            Text(
                                reportMonthRangeLabel(window.startMonthId, window.endMonthId),
                                modifier = Modifier.fillMaxWidth(),
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ReportRangePreset.YEAR_TO_DATE,
                    ReportRangePreset.LAST_YEAR,
                    -> {
                        rangeWindow?.let { window ->
                            Text(
                                reportMonthRangeLabel(window.startMonthId, window.endMonthId),
                                modifier = Modifier.fillMaxWidth(),
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ReportRangePreset.ALL_TIME -> Unit
                }
            } else {
                PeriodStepper(
                    title = reportMonthLabel(monthId),
                    previousLabel = "Previous month",
                    nextLabel = "Next month",
                    onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                    onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
                )
            }
        }
    }
}

@Composable
internal fun ReportRangePresetDropdown(
    selected: ReportRangePreset,
    onSelect: (ReportRangePreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(TandemShapes.control))
                .background(tandemPalette.raised)
                .clickable(role = Role.Button, onClickLabel = "Choose report range", onClick = { expanded = true })
                .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, modifier = Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
            Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReportRangePreset.entries.forEach { preset ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(preset)
                }) {
                    Text(preset.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}

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

@Composable
internal fun BudgetedSpentChart(
    data: ReportDataBundle,
    maxRows: Int?,
    onOpenCategoryActivity: (String, String) -> Unit,
) {
    val points = maxRows?.let { data.budgetedSpent.take(it) } ?: data.budgetedSpent
    val maximum = points.maxOfOrNull { maxOf(it.budgetedMinor, it.spentMinor) }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        points.forEach { point ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.control))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "View ${point.category} activity",
                        onClick = { onOpenCategoryActivity(point.categoryId, point.category) },
                    ).padding(vertical = TandemSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Text(
                        point.category,
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.body,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (point.isOverBudget) {
                        Icon(
                            Lucide.CircleAlert,
                            contentDescription = "Over budget",
                            modifier = Modifier.size(20.dp),
                            tint = tandemPalette.warning,
                        )
                    }
                    Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
                }
                BudgetedSpentValueBar("Budgeted", point.budgetedMinor, maximum, tandemPalette.positive, data.displaySettings)
                BudgetedSpentValueBar("Spent", point.spentMinor, maximum, tandemPalette.negative, data.displaySettings)
            }
        }
    }
}

@Composable
internal fun BudgetedSpentValueBar(
    label: String,
    valueMinor: Long,
    maxValueMinor: Long,
    color: androidx.compose.ui.graphics.Color,
    displaySettings: BudgetDisplaySettings?,
) {
    val trackColor = tandemPalette.raised
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = tandemPalette.muted, fontSize = TandemTypography.caption, modifier = Modifier.width(76.dp))
            Text(
                amountAnnotatedString(formatReportCurrency(valueMinor, displaySettings), displaySettings?.currencyFractionDigits ?: 2),
                color = color,
                fontSize = TandemTypography.caption,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val fraction = (valueMinor.toDouble() / maxValueMinor.coerceAtLeast(1L).toDouble()).toFloat().coerceIn(0f, 1f)
            val cornerRadius = CornerRadius(TandemShapes.small.toPx())
            drawRoundRect(trackColor, cornerRadius = cornerRadius)
            if (fraction > 0f) {
                drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = cornerRadius)
            }
        }
    }
}

@Composable
internal fun ReportLegend(
    kind: ReportKind,
    data: ReportDataBundle,
) {
    val items =
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY -> emptyList()
            ReportKind.SPENDING_OVER_TIME -> {
                val average = data.monthly.map(MonthlyMoneyPoint::spendingMinor).averageLong()
                listOf(
                    "Spending" to tandemPalette.negative,
                    "Average ${formatReportCurrency(average, data.displaySettings)}" to tandemPalette.syncing,
                )
            }
            ReportKind.INCOME_AND_SPENDING -> listOf("Income" to tandemPalette.positive, "Spending" to tandemPalette.negative)
            ReportKind.BUDGETED_AND_SPENT -> listOf("Budgeted" to tandemPalette.positive, "Spent" to tandemPalette.negative)
            ReportKind.NET_WORTH -> listOf("Net worth" to tandemPalette.accent)
        }
    if (items.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
            items.forEach { (label, color) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                ) {
                    Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                    Text(
                        amountAnnotatedString(label, data.displaySettings?.currencyFractionDigits ?: 2),
                        color = tandemPalette.muted,
                        fontSize = TandemTypography.caption,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReportCanvas(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    modifier: Modifier,
    showMonthlyGuides: Boolean = false,
    axisValues: List<Long>? = null,
    onNetWorthSelectionChange: (Int?) -> Unit = {},
    calloutViewportStartPx: Float = 0f,
    calloutViewportWidthPx: Float? = null,
) {
    val palette = tandemPalette
    val monthly = monthlyPoints(kind, data)
    val chartCategories = remember(data.categories) { categoryChartPoints(data.categories) }
    var selection by remember(kind, monthly, chartCategories, data.netWorth, calloutViewportStartPx) {
        mutableStateOf<ReportChartSelection?>(null)
    }
    val selectionCallback by rememberUpdatedState(onNetWorthSelectionChange)
    val selectedCategory = selection?.takeIf { kind == ReportKind.SPENDING_BY_CATEGORY }?.let { chartCategories.getOrNull(it.index) }
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
    val callout =
        selectedCategory?.let { point ->
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
    val description = reportChartDescription(kind, data, netWorthScope)
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var calloutSize by remember { mutableStateOf(IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val calloutAnchor =
        selection?.takeIf { selectedCategory == null }?.let {
            reportSelectionAnchor(
                kind,
                it,
                monthly,
                data.netWorth,
                axisValues,
                chartSize.width.toFloat(),
                chartSize.height.toFloat(),
                with(density) { 8.dp.toPx() },
            )
        }
    val calloutOffset =
        calloutAnchor?.let { anchor ->
            val gap = with(density) { 8.dp.toPx() }
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
            IntOffset(x.toInt(), y.toInt())
        }
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
            val inset = 8.dp.toPx()
            val chartTop = inset
            val chartBottom = size.height - inset
            val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
            if (showMonthlyGuides) {
                val guideCount = axisValues?.size?.coerceAtLeast(2) ?: 5
                (0 until guideCount).forEach { tick ->
                    val y = chartTop + chartHeight * tick / (guideCount - 1).toFloat()
                    drawLine(palette.border, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                }
            }
            when (kind) {
                ReportKind.SPENDING_BY_CATEGORY -> {
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
                                    if (selection == null || selection?.index == index) {
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
                ReportKind.SPENDING_OVER_TIME -> {
                    val values = monthly.map(MonthlyMoneyPoint::spendingMinor)
                    val maxValue =
                        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble()
                            ?: maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
                    val barSlot = size.width / values.size.coerceAtLeast(1)
                    val barWidth = barSlot * 0.5f
                    values.forEachIndexed { index, value ->
                        val barHeight = chartHeight * (value.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
                        val barColor =
                            if (selection?.let { it.index == index && !it.income } != false) {
                                palette.negative
                            } else {
                                palette.muted.copy(alpha = 0.48f)
                            }
                        drawRoundRect(
                            barColor,
                            Offset(index * barSlot + (barSlot - barWidth) / 2f, chartBottom - barHeight),
                            Size(barWidth, barHeight.coerceAtLeast(2f)),
                            CornerRadius(TandemShapes.small.toPx()),
                        )
                    }
                    val average = values.averageLong().toDouble()
                    val averageY = chartBottom - chartHeight * (average / maxValue).toFloat()
                    drawLine(palette.syncing, Offset(inset, averageY), Offset(size.width - inset, averageY), 2.dp.toPx())
                }
                ReportKind.INCOME_AND_SPENDING -> {
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
                        val incomeHeight = chartHeight * (value.incomeMinor.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
                        val spendingHeight = chartHeight * (value.spendingMinor.coerceAtLeast(0L).toDouble() / maxValue).toFloat()
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
                ReportKind.BUDGETED_AND_SPENT -> {
                    val values = data.budgetedSpent.take(8)
                    val maxValue = values.maxOfOrNull { maxOf(it.budgetedMinor, it.spentMinor) }?.coerceAtLeast(1L)?.toDouble() ?: 1.0
                    val rowHeight = chartHeight / values.size.coerceAtLeast(1)
                    val labelInset = inset + rowHeight * 0.24f
                    val trackWidth = size.width - inset * 2
                    values.forEachIndexed { index, value ->
                        val y = chartTop + index * rowHeight + labelInset
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
                ReportKind.NET_WORTH -> {
                    val values = data.netWorth.map(NetWorthPoint::allAccountsMinor)
                    if (values.isNotEmpty()) {
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
                                val y = chartBottom - chartHeight * ((value - minValue) / spread).toFloat()
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
                }
            }
        }
        if (callout != null) {
            Text(
                amountAnnotatedString(callout, data.displaySettings?.currencyFractionDigits ?: 2),
                modifier =
                    Modifier
                        .align(if (selectedCategory != null) Alignment.Center else Alignment.TopStart)
                        .then(if (calloutOffset != null) Modifier.offset { calloutOffset } else Modifier)
                        .onSizeChanged { calloutSize = it }
                        .clip(RoundedCornerShape(TandemShapes.pill))
                        .background(tandemPalette.raised)
                        .padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.extraSmall),
                color = tandemPalette.text,
                fontSize = TandemTypography.caption,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

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
    if (kind == ReportKind.SPENDING_BY_CATEGORY) {
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
    if (kind == ReportKind.NET_WORTH) {
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
    if (kind != ReportKind.SPENDING_OVER_TIME && kind != ReportKind.INCOME_AND_SPENDING) return null
    if (monthly.isEmpty()) return null
    val selectedMax =
        axisValues?.firstOrNull()?.coerceAtLeast(1L)?.toDouble() ?: monthly
            .maxOfOrNull { point ->
                if (kind == ReportKind.SPENDING_OVER_TIME) point.spendingMinor else maxOf(point.incomeMinor, point.spendingMinor)
            }?.coerceAtLeast(1L)
            ?.toDouble() ?: 1.0
    if (kind == ReportKind.SPENDING_OVER_TIME) {
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

internal fun positiveAmount(value: Long): Long = if (value < 0L) -value else value

internal fun reportHasNoData(
    kind: ReportKind,
    data: ReportDataBundle,
): Boolean {
    val monthly = monthlyPoints(kind, data)
    return when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> data.categories.isEmpty()
        ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING ->
            monthly.isEmpty() || monthly.all { it.incomeMinor == 0L && it.spendingMinor == 0L }
        ReportKind.BUDGETED_AND_SPENT -> data.budgetedSpent.isEmpty()
        ReportKind.NET_WORTH -> data.netWorth.isEmpty()
    }
}

internal fun monthlyPoints(
    kind: ReportKind,
    data: ReportDataBundle,
): List<MonthlyMoneyPoint> = if (kind == ReportKind.INCOME_AND_SPENDING) data.incomeAndSpending else data.monthly

internal fun reportSummary(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
): String {
    val format: (Long) -> String = { amount -> formatReportCurrency(amount, data.displaySettings) }
    val monthly = monthlyPoints(kind, data)
    return when (kind) {
        ReportKind.SPENDING_BY_CATEGORY ->
            categorySpendingSummary(
                data.categories.sumOf(CategorySpendingPoint::spendingMinor),
                data.categories.size,
                format,
                ::formatInteger,
            )
        ReportKind.SPENDING_OVER_TIME ->
            spendingOverTimeSummary(monthly.sumOf(MonthlyMoneyPoint::spendingMinor), monthly.size, format, ::formatInteger)
        ReportKind.INCOME_AND_SPENDING -> incomeSpendingSummary(monthly.sumOf(MonthlyMoneyPoint::netMinor))
        ReportKind.BUDGETED_AND_SPENT ->
            budgetedSpentSummary(
                data.budgetedSpent.sumOf(BudgetedSpentPoint::budgetedMinor),
                data.budgetedSpent.sumOf(BudgetedSpentPoint::spentMinor),
                format,
            )
        ReportKind.NET_WORTH -> {
            val point = data.netWorth.lastOrNull()
            if (point == null) {
                "Net worth is unavailable for this period."
            } else {
                netWorthSummary(if (netWorthScope == "On budget") point.onBudgetMinor else point.allAccountsMinor, point.month, format)
            }
        }
    }
}

@Composable
internal fun reportSummaryColor(
    kind: ReportKind,
    data: ReportDataBundle,
): androidx.compose.ui.graphics.Color =
    if (kind != ReportKind.INCOME_AND_SPENDING) {
        tandemPalette.muted
    } else {
        when (monthlyPoints(kind, data).sumOf(MonthlyMoneyPoint::netMinor).compareTo(0L)) {
            1 -> tandemPalette.positive
            -1 -> tandemPalette.negative
            else -> tandemPalette.muted
        }
    }

internal fun reportChartDescription(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
): String =
    "${kind.title}. ${reportSummary(kind, data, netWorthScope)} " +
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY ->
                data.categories.joinToString {
                    "${it.category}: ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            ReportKind.SPENDING_OVER_TIME ->
                monthlyPoints(kind, data).joinToString {
                    "${it.month}: ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            ReportKind.INCOME_AND_SPENDING ->
                monthlyPoints(kind, data).joinToString {
                    "${it.month}: income ${formatReportCurrency(
                        it.incomeMinor,
                        data.displaySettings,
                    )}, spending ${formatReportCurrency(it.spendingMinor, data.displaySettings)}"
                }
            ReportKind.BUDGETED_AND_SPENT ->
                data.budgetedSpent.joinToString {
                    "${it.category}: budgeted ${formatReportCurrency(
                        it.budgetedMinor,
                        data.displaySettings,
                    )}, spent ${formatReportCurrency(it.spentMinor, data.displaySettings)}"
                }
            ReportKind.NET_WORTH ->
                data.netWorth.joinToString { point ->
                    val assets = if (netWorthScope == "On budget") point.onBudgetAssetsMinor else point.allAccountsAssetsMinor
                    val debts = if (netWorthScope == "On budget") point.onBudgetDebtsMinor else point.allAccountsDebtsMinor
                    val value = assets - debts
                    "${point.month}: net worth ${formatReportCurrency(
                        value,
                        data.displaySettings,
                    )}, assets ${formatReportCurrency(
                        assets,
                        data.displaySettings,
                    )}, debts ${formatReportCurrency(debts, data.displaySettings)}"
                }
        }

@Composable
internal fun netAmountColor(amountMinor: Long): androidx.compose.ui.graphics.Color =
    when {
        amountMinor > 0L -> tandemPalette.positive
        amountMinor < 0L -> tandemPalette.negative
        else -> tandemPalette.muted
    }

internal fun List<Long>.averageLong(): Long = if (isEmpty()) 0L else sum() / size
