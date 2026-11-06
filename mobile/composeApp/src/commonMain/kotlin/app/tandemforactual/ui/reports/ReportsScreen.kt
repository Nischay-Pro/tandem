@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.tandemforactual.ui.reports.ReportWindow
import app.tandemforactual.ui.reports.budgetedSpentSummary
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
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun ReportsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
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
    val window = createReportWindow(state.reportsMonthId, periodMode, state.reportsRangeMonths)
    val dataWindow =
        window
            ?: ReportWindow(
                "2026-05-01",
                "2026-05-31",
                "2026-05",
                "2026-05",
                listOf("2026-05"),
            )
    val data = rememberReportData(state, mobileSession, accounts, dataWindow)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen),
        contentPadding = PaddingValues(vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        item(key = "reports-period") {
            ReportPeriodSelector(
                monthId = state.reportsMonthId,
                mode = periodMode,
                rangeMonths = state.reportsRangeMonths,
                onMonthChange = { state.reportsMonthId = it },
                onModeChange = { state.reportsPeriodMode = it.name },
                onRangeChange = { state.reportsRangeMonths = it },
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
                        window,
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
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onEmptyAction: () -> Unit,
) {
    val periodMode =
        ReportPeriodMode.entries.firstOrNull { it.name == state.reportsPeriodMode }
            ?: ReportPeriodMode.MONTH
    val window = createReportWindow(state.reportsMonthId, periodMode, state.reportsRangeMonths)
    if (window == null) {
        Column(Modifier.fillMaxSize().padding(TandemSpacing.screen), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            EmptyState("Choose a report period", "Choose a month to see this report.")
        }
        return
    }
    val data = rememberReportData(state, mobileSession, accounts, window)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen),
        contentPadding = PaddingValues(vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        item(key = "report-detail-period") {
            ReportPeriodSelector(
                monthId = state.reportsMonthId,
                mode = periodMode,
                rangeMonths = state.reportsRangeMonths,
                onMonthChange = { state.reportsMonthId = it },
                onModeChange = { state.reportsPeriodMode = it.name },
                onRangeChange = { state.reportsRangeMonths = it },
            )
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
                ReportDetailChart(kind, data, state)
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

private data class ReportDataBundle(
    val categories: List<CategorySpendingPoint>,
    val monthly: List<MonthlyMoneyPoint>,
    val incomeAndSpending: List<MonthlyMoneyPoint>,
    val budgetedSpent: List<BudgetedSpentPoint>,
    val netWorth: List<NetWorthPoint>,
    val displaySettings: BudgetDisplaySettings?,
)

@Composable
private fun rememberReportData(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    window: ReportWindow,
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
        remember(mobileSession.reportViewModels, budgetId, window, today, accounts) {
            if (budgetId.isNotBlank() && mobileSession.reportViewModels != null) {
                mobileSession.reportViewModels.netWorth.observe(budgetId, window.monthIds, today, accounts)
            } else {
                flowOf(sampleNetWorth(window.monthIds, today))
            }
        }
    val categoryInitial = if (budgetId.isBlank()) sampleCategorySpending(window) else emptyList()
    val monthlyInitial = if (budgetId.isBlank()) sampleMonthlyMoney(window) else emptyList()
    val incomeAndSpendingInitial = if (budgetId.isBlank()) sampleMonthlyMoney(window) else emptyList()
    val budgetedInitial = if (budgetId.isBlank()) sampleBudgetedSpent(window) else emptyList()
    val netWorthInitial = if (budgetId.isBlank()) sampleNetWorth(window.monthIds, today) else emptyList()
    val categoriesBySpend by categoryFlow.collectAsState(categoryInitial)
    val monthly by monthlyFlow.collectAsState(monthlyInitial)
    val incomeAndSpending by incomeAndSpendingFlow.collectAsState(incomeAndSpendingInitial)
    val budgetedSpent by budgetedFlow.collectAsState(budgetedInitial)
    val netWorth by netWorthFlow.collectAsState(netWorthInitial)
    return ReportDataBundle(categoriesBySpend, monthly, incomeAndSpending, budgetedSpent, netWorth, state.budgetDisplaySettings)
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
    rangeMonths: Int,
    onMonthChange: (String) -> Unit,
    onModeChange: (ReportPeriodMode) -> Unit,
    onRangeChange: (Int) -> Unit,
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
                val options = listOf(3, 6, 12)
                ReportSegmentedSelector(options.map { months -> "Last $months months" to (rangeMonths == months) }) { index ->
                    onRangeChange(options[index])
                }
            }
            PeriodStepper(
                title = reportMonthLabel(monthId),
                previousLabel = "Previous month",
                nextLabel = "Next month",
                onPrevious = { shiftReportMonthId(monthId, -1)?.let(onMonthChange) },
                onNext = { shiftReportMonthId(monthId, 1)?.let(onMonthChange) },
            )
            if (mode == ReportPeriodMode.RANGE) {
                val first = shiftReportMonthId(monthId, -(rangeMonths - 1)) ?: monthId
                Text(
                    "${reportMonthLabel(first)} – ${reportMonthLabel(monthId)}",
                    modifier = Modifier.fillMaxWidth(),
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    textAlign = TextAlign.Center,
                )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                Text(
                    kind.title,
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.cardTitle,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "See all",
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "See all ${kind.title}", onClick = onSeeAll),
                    color = tandemPalette.accent,
                    fontSize = TandemTypography.action,
                    fontWeight = FontWeight.SemiBold,
                )
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
                ReportChart(kind, data, netWorthScope, 150.dp, compact = true)
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
                Text(
                    reportSummary(kind, data, netWorthScope),
                    color = reportSummaryColor(kind, data),
                    fontSize = TandemTypography.body,
                    lineHeight = TandemTypography.bodyLineHeight,
                )
            }
        }
    }
}

@Composable
private fun ReportDetailChart(
    kind: ReportKind,
    data: ReportDataBundle,
    state: AppState,
) {
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            if (kind == ReportKind.NET_WORTH) NetWorthScopeSelector(state.reportsNetWorthScope) { state.reportsNetWorthScope = it }
            ReportLegend(kind, data)
            ReportChart(kind, data, state.reportsNetWorthScope, 220.dp, compact = false)
            Text(
                reportSummary(kind, data, state.reportsNetWorthScope),
                color = reportSummaryColor(kind, data),
                fontSize = TandemTypography.action,
                lineHeight = TandemTypography.actionLineHeight,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ReportChart(
    kind: ReportKind,
    data: ReportDataBundle,
    netWorthScope: String,
    height: Dp,
    compact: Boolean,
) {
    when (kind) {
        ReportKind.SPENDING_OVER_TIME, ReportKind.INCOME_AND_SPENDING -> MonthlyReportChart(kind, data, height)
        ReportKind.BUDGETED_AND_SPENT -> BudgetedSpentChart(data, if (compact) 5 else null)
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
) {
    val points = maxRows?.let { data.budgetedSpent.take(it) } ?: data.budgetedSpent
    val maximum = points.maxOfOrNull { maxOf(it.budgetedMinor, it.spentMinor) }?.coerceAtLeast(1L) ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        points.forEach { point ->
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
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
    val description = reportChartDescription(kind, data, netWorthScope)
    val monthly = monthlyPoints(kind, data)
    Canvas(modifier.semantics { contentDescription = description }) {
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
                    val colors = listOf(palette.accent, palette.syncing, palette.positive, palette.warning, palette.negative, palette.muted)
                    var startAngle = -90f
                    values.forEachIndexed { index, value ->
                        val sweep = value.toDouble().div(total).toFloat() * 360f
                        drawArc(
                            color = colors[index % colors.size],
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
                    val barHeight = chartHeight * (value.toDouble() / maxValue).toFloat()
                    drawRoundRect(
                        palette.negative,
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
                    val incomeHeight = chartHeight * (value.incomeMinor.toDouble() / maxValue).toFloat()
                    val spendingHeight = chartHeight * (value.spendingMinor.toDouble() / maxValue).toFloat()
                    drawRoundRect(
                        palette.positive,
                        Offset(left, chartBottom - incomeHeight),
                        Size(barWidth, incomeHeight.coerceAtLeast(2f)),
                        CornerRadius(TandemShapes.small.toPx()),
                    )
                    drawRoundRect(
                        palette.negative,
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
                    val minValue = minOf(0L, values.minOrNull() ?: 0L).toDouble()
                    val maxValue = maxOf(1L, values.maxOrNull() ?: 1L).toDouble()
                    val spread = (maxValue - minValue).coerceAtLeast(1.0)
                    val points =
                        values.mapIndexed { index, value ->
                            val x = inset + (size.width - inset * 2) * index / (values.size - 1).coerceAtLeast(1)
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
}

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
                    val value = if (netWorthScope == "On budget") point.onBudgetMinor else point.allAccountsMinor
                    "${point.month}: ${formatReportCurrency(value, data.displaySettings)}"
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
                    BudgetedSpentTableRow(point, displaySettings)
                }
            ReportKind.NET_WORTH -> {
                data.netWorth.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    val value = if (netWorthScope == "On budget") point.onBudgetMinor else point.allAccountsMinor
                    ReportTableRow(point.month, formatReportCurrency(value, displaySettings))
                }
            }
        }
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
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium)) {
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
