@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.AccountDayAggregate
import app.tandemforactual.data.database.CategoryMonthAggregate
import app.tandemforactual.data.database.MonthlyIncomeSpendingAggregate
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.PeriodStepper
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.recordName
import app.tandemforactual.ui.reports.BudgetedSpentPoint
import app.tandemforactual.ui.reports.CategorySpendingPoint
import app.tandemforactual.ui.reports.MonthlyMoneyPoint
import app.tandemforactual.ui.reports.NetWorthPoint
import app.tandemforactual.ui.reports.ReportKind
import app.tandemforactual.ui.reports.ReportPeriodMode
import app.tandemforactual.ui.reports.ReportRangePreset
import app.tandemforactual.ui.reports.ReportWindow
import app.tandemforactual.ui.reports.budgetedSpentSummary
import app.tandemforactual.ui.reports.categoryPercentage
import app.tandemforactual.ui.reports.categorySpendingPoints
import app.tandemforactual.ui.reports.categorySpendingSummary
import app.tandemforactual.ui.reports.createReportWindow
import app.tandemforactual.ui.reports.incomeSpendingSummary
import app.tandemforactual.ui.reports.monthlyMoneyPoints
import app.tandemforactual.ui.reports.netWorthPoints
import app.tandemforactual.ui.reports.netWorthSummary
import app.tandemforactual.ui.reports.reportMonthAxisLabel
import app.tandemforactual.ui.reports.reportMonthLabel
import app.tandemforactual.ui.reports.shiftReportMonthId
import app.tandemforactual.ui.reports.spendingOverTimeSummary
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.ChartColumnIncreasing
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp
import kotlinx.coroutines.flow.flowOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
internal fun ReportsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    reportsDataCache: ReportsDataCache,
    onOpenReport: (ReportKind) -> Unit,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onOpenActivity: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    LaunchedEffect(state.selectedBudgetId) {
        if (state.reportsBudgetId != state.selectedBudgetId) {
            state.reportsBudgetId = state.selectedBudgetId
            state.reportsMonthId = state.selectedMonthId
        }
    }
    val periodMode =
        ReportPeriodMode.entries.firstOrNull { it.name == state.reportsPeriodMode }
            ?: ReportPeriodMode.MONTH
    val rangePreset = ReportRangePreset.entries.firstOrNull { it.name == state.reportsRangePreset } ?: ReportRangePreset.LAST_6_MONTHS
    val today = platformTodayIsoDate()
    val window = createReportWindow(state.reportsMonthId, periodMode, rangePreset, today)
    val netWorthWindow =
        createReportWindow(today.take(7), ReportPeriodMode.RANGE, ReportRangePreset.LAST_6_MONTHS, today)
    val dataWindow =
        window
            ?: ReportWindow(
                "2026-05-01",
                "2026-05-31",
                "2026-05",
                "2026-05",
                listOf("2026-05"),
            )
    val data = rememberReportData(state, mobileSession, accounts, dataWindow, netWorthWindow ?: dataWindow, reportsDataCache)
    val listState = LocalRouteLazyListState.current ?: rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen),
        contentPadding = PaddingValues(vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        item(key = "reports-period") {
            ReportPeriodSelector(
                monthId = state.reportsMonthId,
                mode = periodMode,
                rangePreset = rangePreset,
                onMonthChange = { state.reportsMonthId = it },
                onModeChange = { state.reportsPeriodMode = it.name },
                onRangePresetChange = {
                    state.reportsRangePreset = it.name
                    it.monthCount?.let { months -> state.reportsRangeMonths = months }
                },
            )
        }
        if (window == null) {
            item(key = "reports-invalid-period") {
                SurfaceCard {
                    EmptyState(
                        "Choose a report period",
                        "Choose a month to see your reports.",
                        Modifier.padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            ReportKind.entries.forEach { kind ->
                item(key = "report-card-${kind.name}") {
                    ReportCard(
                        kind,
                        data,
                        if (kind == ReportKind.NET_WORTH) netWorthWindow ?: window else window,
                        state.reportsNetWorthScope,
                        onNetWorthScopeChange = { state.reportsNetWorthScope = it },
                        onSeeAll = { onOpenReport(kind) },
                        onOpenCategoryActivity = onOpenCategoryActivity,
                        onEmptyAction = if (state.selectedBudgetId.isBlank()) onOpenActivity else onAddTransaction,
                        emptyActionLabel = if (state.selectedBudgetId.isBlank()) "Open activity" else "Add transaction",
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReportDetailScreen(
    kind: ReportKind,
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    reportsDataCache: ReportsDataCache,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onEmptyAction: () -> Unit,
) {
    val periodMode =
        ReportPeriodMode.entries.firstOrNull { it.name == state.reportsPeriodMode }
            ?: ReportPeriodMode.MONTH
    val rangePreset = ReportRangePreset.entries.firstOrNull { it.name == state.reportsRangePreset } ?: ReportRangePreset.LAST_6_MONTHS
    val today = platformTodayIsoDate()
    val window = createReportWindow(state.reportsMonthId, periodMode, rangePreset, today)
    if (window == null) {
        Column(Modifier.fillMaxSize().padding(TandemSpacing.screen), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            EmptyState("Choose a report period", "Choose a month to see this report.")
        }
        return
    }
    val netWorthWindow = createReportWindow(today.take(7), ReportPeriodMode.RANGE, ReportRangePreset.LAST_6_MONTHS, today) ?: window
    val data = rememberReportData(state, mobileSession, accounts, window, netWorthWindow, reportsDataCache)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen),
        contentPadding = PaddingValues(vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        item(key = "report-detail-period") {
            if (kind == ReportKind.NET_WORTH) {
                FixedReportPeriod("Last 6 months")
            } else {
                ReportPeriodSelector(
                    monthId = state.reportsMonthId,
                    mode = periodMode,
                    rangePreset = rangePreset,
                    onMonthChange = { state.reportsMonthId = it },
                    onModeChange = { state.reportsPeriodMode = it.name },
                    onRangePresetChange = {
                        state.reportsRangePreset = it.name
                        it.monthCount?.let { months -> state.reportsRangeMonths = months }
                    },
                )
            }
        }
        if (reportHasNoData(kind, data)) {
            item(key = "report-detail-empty") {
                SurfaceCard {
                    EmptyState(
                        "No report data yet",
                        "Add transactions and sync your budget to build this report.",
                        Modifier.padding(TandemSpacing.large),
                        if (state.selectedBudgetId.isBlank()) "Open activity" else "Add transaction",
                        onEmptyAction,
                    )
                }
            }
        } else {
            item(key = "report-detail-chart") {
                ReportDetailChart(kind, data, state, if (kind == ReportKind.NET_WORTH) netWorthWindow else window, onOpenCategoryActivity)
            }
            item(key = "report-detail-table") {
                ReportDataTable(
                    kind,
                    data,
                    window,
                    state.budgetDisplaySettings,
                    state.reportsNetWorthScope,
                    onOpenCategoryActivity = onOpenCategoryActivity,
                )
            }
        }
    }
}

@Composable
private fun FixedReportPeriod(label: String) {
    SurfaceCard {
        Row(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Period", color = tandemPalette.muted, fontSize = TandemTypography.caption, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(label, color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
        }
    }
}

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

private data class ReportChartSelection(
    val index: Int,
    val income: Boolean = false,
)

@Composable
private fun rememberReportData(
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
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.spendingByCategory.observe(budgetId, window, categoryNames)
            } else {
                flowOf(sampleCategorySpending(window))
            }
        }
    val monthlyFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.spendingOverTime.observe(budgetId, window)
            } else {
                flowOf(sampleMonthlyMoney(window))
            }
        }
    val incomeAndSpendingFlow =
        remember(mobileSession.reportViewModels, budgetId, window) {
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.incomeAndSpending.observe(budgetId, window)
            } else {
                flowOf(sampleMonthlyMoney(window))
            }
        }
    val budgetedFlow =
        remember(mobileSession.reportViewModels, budgetId, window, categoryNames) {
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.budgetedAndSpent.observe(budgetId, window, categoryNames)
            } else {
                flowOf(sampleBudgetedSpent(window))
            }
        }
    val today = platformTodayIsoDate()
    val netWorthFlow =
        remember(mobileSession.reportViewModels, budgetId, netWorthWindow, today, accounts) {
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.netWorth.observe(budgetId, netWorthWindow.monthIds, today, accounts)
            } else {
                flowOf(sampleNetWorth(netWorthWindow.monthIds, today))
            }
        }
    val cacheKey =
        "$budgetId|${window.startDate}|${window.throughDate}|${window.allDates}|" +
            "${netWorthWindow.monthIds}|${categoryNames.hashCode()}|${accounts.hashCode()}"
    val cachedData = reportsDataCache.get(cacheKey)
    val categoryInitial = if (budgetId.isBlank()) sampleCategorySpending(window) else emptyList()
    val monthlyInitial = if (budgetId.isBlank()) sampleMonthlyMoney(window) else emptyList()
    val incomeAndSpendingInitial = if (budgetId.isBlank()) sampleMonthlyMoney(window) else emptyList()
    val budgetedInitial = if (budgetId.isBlank()) sampleBudgetedSpent(window) else emptyList()
    val netWorthInitial = if (budgetId.isBlank()) sampleNetWorth(netWorthWindow.monthIds, today) else emptyList()
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

private fun sampleCategorySpending(window: ReportWindow): List<CategorySpendingPoint> {
    val rows =
        previewTransactions
            .filter { it.dateIso in window.startDate..window.throughDate && it.amountMinorUnits < 0 && !it.isTransfer }
            .map { transaction ->
                CategoryMonthAggregate(
                    transaction.dateIso.take(7),
                    transaction.categoryId ?: transaction.category,
                    kotlin.math.abs(transaction.amountMinorUnits),
                )
            }
    val names = rows.associate { it.categoryId to it.categoryId }
    return app.tandemforactual.ui.reports
        .categorySpendingPoints(rows, names)
}

private fun sampleMonthlyMoney(window: ReportWindow): List<MonthlyMoneyPoint> {
    val rows =
        previewTransactions
            .filter { it.dateIso in window.startDate..window.throughDate && !it.isTransfer }
            .groupBy { it.dateIso.take(7) }
            .map { (monthId, transactions) ->
                MonthlyIncomeSpendingAggregate(
                    monthId,
                    transactions.filter { it.amountMinorUnits > 0 }.sumOf { it.amountMinorUnits },
                    transactions.filter { it.amountMinorUnits < 0 }.sumOf { kotlin.math.abs(it.amountMinorUnits) },
                )
            }
    return monthlyMoneyPoints(rows, window)
}

private fun sampleBudgetedSpent(window: ReportWindow): List<BudgetedSpentPoint> {
    val names = previewCategoryGroups.flatMap { it.categories }.associate { it.id to it.name }
    val spent =
        sampleCategorySpending(window).map { point ->
            point.copy(categoryId = names.entries.firstOrNull { it.value.equals(point.category, ignoreCase = true) }?.key ?: point.category)
        }
    val budgetRows =
        previewCategoryGroups
            .flatMap { it.categories }
            .map { category ->
                BudgetedSpentPoint(
                    category.id,
                    category.name,
                    category.assignedMinor * window.monthCount,
                    spent.firstOrNull { it.categoryId == category.id }?.spendingMinor ?: kotlin.math.abs(category.activityMinor),
                )
            }
    return (
        budgetRows +
            spent.filterNot { value -> budgetRows.any { it.categoryId == value.categoryId } }.map { value ->
                BudgetedSpentPoint(value.categoryId, value.category, 0, value.spendingMinor)
            }
    ).sortedByDescending(BudgetedSpentPoint::spentMinor)
}

private fun sampleNetWorth(
    monthIds: List<String>,
    today: String,
): List<NetWorthPoint> {
    val accounts =
        previewAccounts.map { account ->
            LiveAccountSummary(account.id, account.name, account.balanceMinor, null, account.offBudget)
        }
    val accountId = previewAccounts.firstOrNull()?.id ?: "sample:checking"
    val daily =
        previewTransactions
            .filter { it.dateIso.isNotBlank() && it.amountMinorUnits != 0L }
            .map { AccountDayAggregate(accountId, it.dateIso, it.amountMinorUnits) }
    return netWorthPoints(daily, accounts, monthIds, today)
}

@Composable
private fun ReportPeriodSelector(
    monthId: String,
    mode: ReportPeriodMode,
    rangePreset: ReportRangePreset,
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
            ReportSegmentedSelector(
                listOf("Month" to (mode == ReportPeriodMode.MONTH), "Range" to (mode == ReportPeriodMode.RANGE)),
            ) { index -> onModeChange(if (index == 0) ReportPeriodMode.MONTH else ReportPeriodMode.RANGE) }
            if (mode == ReportPeriodMode.RANGE) {
                ReportRangePresetDropdown(rangePreset, onRangePresetChange)
                val rangeWindow = createReportWindow(monthId, mode, rangePreset, platformTodayIsoDate())
                when (rangePreset) {
                    ReportRangePreset.LAST_3_MONTHS,
                    ReportRangePreset.LAST_6_MONTHS,
                    ReportRangePreset.LAST_12_MONTHS,
                    -> {
                        PeriodStepper(
                            title = reportMonthLabel(monthId),
                            previousLabel = "Previous end month",
                            nextLabel = "Next end month",
                            onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                            onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
                        )
                        rangeWindow?.let { window ->
                            Text(
                                "${reportMonthLabel(window.startMonthId)} – ${reportMonthLabel(window.endMonthId)}",
                                modifier = Modifier.fillMaxWidth(),
                                color = tandemPalette.muted,
                                fontSize = TandemTypography.caption,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    ReportRangePreset.YEAR_TO_DATE ->
                        Text(
                            "January ${platformTodayIsoDate().take(4)} – ${reportMonthLabel(platformTodayIsoDate().take(7))}",
                            modifier = Modifier.fillMaxWidth(),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            textAlign = TextAlign.Center,
                        )
                    ReportRangePreset.LAST_YEAR ->
                        Text(
                            "January – December ${platformTodayIsoDate().take(4).toIntOrNull()?.minus(1) ?: ""}",
                            modifier = Modifier.fillMaxWidth(),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            textAlign = TextAlign.Center,
                        )
                    ReportRangePreset.ALL_DATES ->
                        Text(
                            "All available dates",
                            modifier = Modifier.fillMaxWidth(),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            textAlign = TextAlign.Center,
                        )
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
private fun ReportRangePresetDropdown(
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
private fun ReportSegmentedSelector(
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.pill))
            .background(tandemPalette.raised)
            .padding(TandemSpacing.xxSmall),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
    ) {
        options.forEachIndexed { index, (label, selected) ->
            SelectableTile(
                selected = selected,
                contentDescription = label,
                onSelect = { onSelect(index) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(TandemShapes.pill),
                selectedBackground = tandemPalette.accentSurface,
                unselectedBackground = tandemPalette.raised,
            ) {
                Text(
                    label,
                    color = if (selected) tandemPalette.accent else tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ReportChoiceTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SelectableTile(
        selected = selected,
        contentDescription = label,
        onSelect = onClick,
        modifier = modifier.height(44.dp),
        selectedBackground = tandemPalette.accentSurface,
        unselectedBackground = tandemPalette.raised,
        selectedBorder = tandemPalette.accent,
    ) {
        Text(
            label,
            color = tandemPalette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun ReportCard(
    kind: ReportKind,
    data: ReportDataBundle,
    window: ReportWindow,
    netWorthScope: String,
    onNetWorthScopeChange: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onEmptyAction: () -> Unit,
    emptyActionLabel: String,
) {
    val empty = reportHasNoData(kind, data)
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .clickable(role = Role.Button, onClickLabel = "Open ${kind.title}", onClick = onSeeAll)
                    .padding(vertical = TandemSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Icon(reportIcon(kind), contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                Text(
                    kind.title,
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.cardTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.muted)
            }
            if (kind != ReportKind.NET_WORTH && !empty) {
                ReportSummary(kind, data, netWorthScope, compact = true)
            }
            if (kind == ReportKind.NET_WORTH && !empty) {
                Text("Last 6 months", color = tandemPalette.muted, fontSize = TandemTypography.caption)
                ReportSummary(kind, data, netWorthScope, compact = true)
            }
            if (empty) {
                EmptyState(
                    "No data for this period",
                    "Add transactions and sync to build this report.",
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                )
            } else {
                if (kind == ReportKind.NET_WORTH) {
                    NetWorthScopeSelector(netWorthScope, onNetWorthScopeChange)
                }
                ReportLegend(kind, data)
                ReportChart(
                    kind,
                    data,
                    netWorthScope,
                    150.dp,
                    compact = true,
                    window = window,
                    onOpenCategoryActivity = onOpenCategoryActivity,
                )
                if (kind == ReportKind.SPENDING_BY_CATEGORY) {
                    data.categories.take(5).forEach { point ->
                        ListRow(
                            point.category,
                            trailing = formatReportCurrency(point.spendingMinor, data.displaySettings),
                            leading = RowLeading.Icon(financeIcon(point.category)),
                            onClick = { onOpenCategoryActivity(point.categoryId, point.category, window) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportDetailChart(
    kind: ReportKind,
    data: ReportDataBundle,
    state: AppState,
    window: ReportWindow,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            if (kind == ReportKind.NET_WORTH) {
                ReportSummary(kind, data, state.reportsNetWorthScope, compact = false)
                NetWorthScopeSelector(state.reportsNetWorthScope) { state.reportsNetWorthScope = it }
            }
            if (kind != ReportKind.NET_WORTH) {
                ReportSummary(kind, data, state.reportsNetWorthScope, compact = false)
            }
            ReportLegend(kind, data)
            ReportChart(
                kind,
                data,
                state.reportsNetWorthScope,
                220.dp,
                compact = false,
                window = window,
                onOpenCategoryActivity = onOpenCategoryActivity,
            )
        }
    }
}

@Composable
private fun ReportSummary(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    compact: Boolean,
) {
    if (kind == ReportKind.NET_WORTH) {
        val point = data.netWorth.lastOrNull() ?: return
        val assets = if (netWorthScope == "On budget") point.onBudgetAssetsMinor else point.allAccountsAssetsMinor
        val debts = if (netWorthScope == "On budget") point.onBudgetDebtsMinor else point.allAccountsDebtsMinor
        val netWorth = assets - debts
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Text(
                formatReportCurrency(netWorth, data.displaySettings),
                color = netAmountColor(netWorth),
                fontSize = if (compact) TandemTypography.amount else TandemTypography.amountField,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Icon(Lucide.TrendingUp, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.positive)
                Text(
                    "Assets: ${formatReportCurrency(assets, data.displaySettings)}",
                    color = tandemPalette.positive,
                    fontSize = TandemTypography.caption,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Icon(Lucide.CreditCard, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.negative)
                Text(
                    "Debts: ${formatReportCurrency(debts, data.displaySettings)}",
                    color = tandemPalette.negative,
                    fontSize = TandemTypography.caption,
                )
            }
        }
    } else {
        Text(
            reportSummary(kind, data, netWorthScope),
            color = reportSummaryColor(kind, data),
            fontSize = if (compact) TandemTypography.body else TandemTypography.action,
            lineHeight = if (compact) TandemTypography.bodyLineHeight else TandemTypography.actionLineHeight,
            fontWeight = if (compact) FontWeight.Normal else FontWeight.Medium,
        )
    }
}

@Composable
private fun ReportChart(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    height: Dp,
    compact: Boolean,
    window: ReportWindow,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    when (kind) {
        ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING -> MonthlyReportChart(kind, data, height)
        ReportKind.BUDGETED_AND_SPENT ->
            BudgetedSpentChart(data, if (compact) 5 else null) { categoryId, category ->
                onOpenCategoryActivity(categoryId, category, window)
            }
        else -> ReportCanvas(kind, data, netWorthScope, Modifier.fillMaxWidth().height(height))
    }
}

@Composable
private fun MonthlyReportChart(
    kind: ReportKind,
    data: ReportDataBundle,
    height: Dp,
) {
    val monthly = monthlyPoints(kind, data)
    val maximum =
        monthly
            .maxOfOrNull { point ->
                if (kind == ReportKind.SPENDING_OVER_TIME) point.spendingMinor else maxOf(point.incomeMinor, point.spendingMinor)
            }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Row(
            Modifier.fillMaxWidth().height(height),
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            Column(
                Modifier.width(84.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    reportAxisValue(maximum, maximum, data.displaySettings),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    reportAxisValue(0L, maximum, data.displaySettings),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    maxLines = 1,
                )
            }
            ReportCanvas(
                kind,
                data,
                netWorthScope = "All accounts",
                modifier = Modifier.weight(1f).fillMaxHeight(),
                showMonthlyGuides = true,
            )
        }
        ReportMonthAxis(monthly)
    }
}

private fun reportAxisValue(
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

@Composable
private fun ReportMonthAxis(monthly: List<MonthlyMoneyPoint>) {
    val indexes = evenlySpacedMonthIndexes(monthly.size)
    val currentMonthId = platformTodayIsoDate().take(7)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(84.dp + TandemSpacing.small))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            indexes.forEach { index ->
                val point = monthly[index]
                val isCurrentMonth = point.monthId == currentMonthId
                Text(
                    reportMonthAxisLabel(point.monthId),
                    modifier = Modifier.weight(1f),
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
}

private fun evenlySpacedMonthIndexes(monthCount: Int): List<Int> {
    if (monthCount <= 0) return emptyList()
    val labelCount = minOf(monthCount, 6)
    if (labelCount == 1) return listOf(0)
    return (0 until labelCount).map { index -> index * (monthCount - 1) / (labelCount - 1) }
}

@Composable
private fun BudgetedSpentChart(
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
private fun BudgetedSpentValueBar(
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
                formatReportCurrency(valueMinor, displaySettings),
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
private fun ReportTableRow(
    title: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium)) {
        Text(title, color = tandemPalette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
        Text(value, color = tandemPalette.muted, fontSize = TandemTypography.compactBody, lineHeight = TandemTypography.bodyLineHeight)
    }
}

@Composable
private fun NetWorthScopeSelector(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        ReportChoiceTile("All accounts", selected == "All accounts", Modifier.weight(1f)) { onSelect("All accounts") }
        ReportChoiceTile("On budget", selected == "On budget", Modifier.weight(1f)) { onSelect("On budget") }
    }
}

private fun reportIcon(kind: ReportKind): ImageVector =
    when (kind) {
        ReportKind.SPENDING_BY_CATEGORY -> Lucide.ChartPie
        ReportKind.SPENDING_OVER_TIME -> Lucide.ChartColumnIncreasing
        ReportKind.INCOME_AND_SPENDING -> Lucide.ChartNoAxesCombined
        ReportKind.BUDGETED_AND_SPENT -> Lucide.ChartColumn
        ReportKind.NET_WORTH -> Lucide.Landmark
    }

@Composable
private fun ReportLegend(
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
                    Text(label, color = tandemPalette.muted, fontSize = TandemTypography.caption)
                }
            }
        }
    }
}

@Composable
private fun ReportCanvas(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    modifier: Modifier,
    showMonthlyGuides: Boolean = false,
) {
    val palette = tandemPalette
    val monthly = monthlyPoints(kind, data)
    var selection by remember(kind, monthly, data.categories) { mutableStateOf<ReportChartSelection?>(null) }
    val selectedCategory = selection?.takeIf { kind == ReportKind.SPENDING_BY_CATEGORY }?.let { data.categories.getOrNull(it.index) }
    val selectedValue =
        selection?.takeIf { kind == ReportKind.SPENDING_OVER_TIME || kind == ReportKind.INCOME_AND_SPENDING }?.let { selected ->
            monthly.getOrNull(selected.index)?.let { point ->
                if (kind == ReportKind.SPENDING_OVER_TIME || !selected.income) point.spendingMinor else point.incomeMinor
            }
        }
    val categoryTotal = data.categories.sumOf { it.spendingMinor.coerceAtLeast(0L) }
    val callout =
        selectedCategory?.let { point ->
            "${categoryPercentage(
                point.spendingMinor,
                categoryTotal,
            )} · ${formatReportCurrency(positiveAmount(point.spendingMinor), data.displaySettings)}"
        } ?: selectedValue?.let { formatReportCurrency(positiveAmount(it), data.displaySettings) }
    val description = reportChartDescription(kind, data, netWorthScope)
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        if (callout == null) description else "$description Selected: $callout"
                }.pointerInput(kind, monthly, data.categories) {
                    detectTapGestures { offset ->
                        selection =
                            reportSelectionAt(
                                kind,
                                data.categories,
                                monthly,
                                offset,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                density,
                            )
                    }
                },
        ) {
            val inset = 8.dp.toPx()
            val chartTop = inset
            val chartBottom = size.height - inset
            val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
            if (showMonthlyGuides) {
                drawLine(palette.border, Offset(inset, chartTop), Offset(size.width - inset, chartTop), 1.dp.toPx())
                drawLine(palette.border, Offset(inset, chartBottom), Offset(size.width - inset, chartBottom), 1.dp.toPx())
            }
            when (kind) {
                ReportKind.SPENDING_BY_CATEGORY -> {
                    val values = data.categories.map(CategorySpendingPoint::spendingMinor)
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
                    val maxValue = maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
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
                        monthly
                            .maxOfOrNull { maxOf(it.incomeMinor, it.spendingMinor) }
                            ?.coerceAtLeast(1L)
                            ?.toDouble() ?: 1.0
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
                    val values =
                        data.netWorth.map {
                            if (netWorthScope == "On budget") it.onBudgetMinor else it.allAccountsMinor
                        }
                    if (values.isNotEmpty()) {
                        val observedMin = (values.minOrNull() ?: 0L).toDouble()
                        val observedMax = (values.maxOrNull() ?: 0L).toDouble()
                        val observedSpread = observedMax - observedMin
                        val padding =
                            if (observedSpread == 0.0) {
                                maxOf(abs(observedMin) * 0.05, 1.0)
                            } else {
                                maxOf(observedSpread * 0.08, 1.0)
                            }
                        val minValue = observedMin - padding
                        val maxValue = observedMax + padding
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
                        points.forEach { drawCircle(palette.accent, 4.dp.toPx(), it) }
                    }
                }
            }
        }
        if (callout != null) {
            Text(
                callout,
                modifier =
                    Modifier
                        .align(if (selectedCategory != null) Alignment.Center else Alignment.TopCenter)
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

private fun reportSelectionAt(
    kind: ReportKind,
    categories: List<CategorySpendingPoint>,
    monthly: List<MonthlyMoneyPoint>,
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
    if (kind != ReportKind.SPENDING_OVER_TIME && kind != ReportKind.INCOME_AND_SPENDING) return null
    if (monthly.isEmpty()) return null
    val selectedMax =
        monthly
            .maxOfOrNull { point ->
                if (kind == ReportKind.SPENDING_OVER_TIME) point.spendingMinor else maxOf(point.incomeMinor, point.spendingMinor)
            }?.coerceAtLeast(1L)
            ?.toDouble() ?: 1.0
    if (kind == ReportKind.SPENDING_OVER_TIME) {
        val values = monthly.map(MonthlyMoneyPoint::spendingMinor)
        val maxValue = maxOf(values.maxOrNull() ?: 0L, values.averageLong()).coerceAtLeast(1L).toDouble()
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

private fun positiveAmount(value: Long): Long = if (value < 0L) -value else value

private fun reportHasNoData(
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

private fun monthlyPoints(
    kind: ReportKind,
    data: ReportDataBundle,
): List<MonthlyMoneyPoint> = if (kind == ReportKind.INCOME_AND_SPENDING) data.incomeAndSpending else data.monthly

private fun reportSummary(
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
private fun reportSummaryColor(
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

private fun reportChartDescription(
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
private fun ReportDataTable(
    kind: ReportKind,
    data: ReportDataBundle,
    window: ReportWindow,
    displaySettings: BudgetDisplaySettings?,
    netWorthScope: String,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    val monthly = monthlyPoints(kind, data)
    SectionCard("Data") {
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY ->
                data.categories.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    ListRow(
                        point.category,
                        trailing = formatReportCurrency(point.spendingMinor, displaySettings),
                        leading = RowLeading.Icon(financeIcon(point.category)),
                        onClick = { onOpenCategoryActivity(point.categoryId, point.category, window) },
                    )
                }
            ReportKind.SPENDING_OVER_TIME ->
                monthly.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    ReportTableRow(point.month, formatReportCurrency(point.spendingMinor, displaySettings))
                }
            ReportKind.INCOME_AND_SPENDING ->
                monthly.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    IncomeAndSpendingTableRow(point, displaySettings)
                }
            ReportKind.BUDGETED_AND_SPENT ->
                data.budgetedSpent.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    BudgetedSpentTableRow(point, displaySettings) {
                        onOpenCategoryActivity(point.categoryId, point.category, window)
                    }
                }
            ReportKind.NET_WORTH -> {
                data.netWorth.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    val assets = if (netWorthScope == "On budget") point.onBudgetAssetsMinor else point.allAccountsAssetsMinor
                    val debts = if (netWorthScope == "On budget") point.onBudgetDebtsMinor else point.allAccountsDebtsMinor
                    NetWorthTableRow(point.month, assets, debts, displaySettings)
                }
            }
        }
    }
}

@Composable
private fun NetWorthTableRow(
    month: String,
    assets: Long,
    debts: Long,
    displaySettings: BudgetDisplaySettings?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium)) {
        Text(month, color = tandemPalette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
        Text(
            formatReportCurrency(assets - debts, displaySettings),
            color = netAmountColor(assets - debts),
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Assets: ${formatReportCurrency(assets, displaySettings)}",
            color = tandemPalette.positive,
            fontSize = TandemTypography.caption,
        )
        Text("Debts: ${formatReportCurrency(debts, displaySettings)}", color = tandemPalette.negative, fontSize = TandemTypography.caption)
    }
}

@Composable
private fun IncomeAndSpendingTableRow(
    point: MonthlyMoneyPoint,
    displaySettings: BudgetDisplaySettings?,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium)) {
        Text(point.month, color = tandemPalette.text, fontSize = TandemTypography.action, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text(
                "Income ${formatReportCurrency(point.incomeMinor, displaySettings)}",
                modifier = Modifier.weight(1f),
                color = tandemPalette.positive,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                "Spending ${formatReportCurrency(point.spendingMinor, displaySettings)}",
                modifier = Modifier.weight(1f),
                color = tandemPalette.negative,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Clip,
            )
        }
        Text(
            "Net ${formatReportCurrency(point.netMinor, displaySettings)}",
            color = netAmountColor(point.netMinor),
            fontSize = TandemTypography.caption,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BudgetedSpentTableRow(
    point: BudgetedSpentPoint,
    displaySettings: BudgetDisplaySettings?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TandemShapes.control))
            .clickable(
                role = Role.Button,
                onClickLabel = "View ${point.category} activity",
                onClick = onClick,
            ).padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                point.category,
                modifier = Modifier.weight(1f),
                color = tandemPalette.text,
                fontSize = TandemTypography.action,
                fontWeight = FontWeight.Medium,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text(
                "Budgeted ${formatReportCurrency(point.budgetedMinor, displaySettings)}",
                modifier = Modifier.weight(1f),
                color = tandemPalette.positive,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                "Spent ${formatReportCurrency(point.spentMinor, displaySettings)}",
                modifier = Modifier.weight(1f),
                color = tandemPalette.negative,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Clip,
            )
        }
        if (point.isOverBudget) {
            Text("Over budget", color = tandemPalette.warning, fontSize = TandemTypography.caption)
        }
    }
}

@Composable
private fun netAmountColor(amountMinor: Long): androidx.compose.ui.graphics.Color =
    when {
        amountMinor > 0L -> tandemPalette.positive
        amountMinor < 0L -> tandemPalette.negative
        else -> tandemPalette.muted
    }

private fun List<Long>.averageLong(): Long = if (isEmpty()) 0L else sum() / size
