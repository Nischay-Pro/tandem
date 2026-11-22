@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.ChartColumnIncreasing
import com.composables.icons.lucide.ChartNoAxesCombined
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TrendingUp

@Composable
internal fun ReportsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    accounts: List<LiveAccountSummary>,
    reportsDataCache: ReportsDataCache,
    onOpenReport: (ReportKind) -> Unit,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onAddTransaction: () -> Unit,
    onChooseBudget: () -> Unit,
    onOpenInsight: (AppRoute) -> Unit = {},
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
    val window = createReportWindow(state.reportsMonthId, periodMode, rangePreset, today, mobileSession.budgetMonthIds)
    if (state.selectedBudgetId.isBlank()) {
        PageColumn {
            EmptyState(
                "Choose a budget",
                "Open an Actual budget to view reports.",
                actionLabel = "Choose budget",
                onAction = onChooseBudget,
            )
        }
        return
    }
    if (window == null) {
        PageColumn { EmptyState("Choose a report period", "Choose a month to see your reports.") }
        return
    }
    val netWorthMonthIds = mobileSession.budgetMonthIds
    val netWorthWindow =
        remember(netWorthMonthIds, today) { createReportHistoryWindow(netWorthMonthIds, today) } ?: window
    val data = rememberReportData(state, mobileSession, accounts, window, netWorthWindow, reportsDataCache)
    val listState = LocalRouteLazyListState.current ?: rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen),
        contentPadding = PaddingValues(vertical = TandemSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
    ) {
        item(key = "reports-period") {
            ReportsPeriodItem(state, mobileSession.budgetMonthIds, periodMode, rangePreset)
        }
        item(key = "reports-more") {
            ReportsMoreCard(mobileSession.planning.customReports.size, onOpenInsight)
        }
        ReportKind.entries.forEach { kind ->
            item(key = "report-card-${kind.name}") {
                ReportCard(
                    kind,
                    data,
                    if (kind == ReportKind.NET_WORTH) netWorthWindow else window,
                    onSeeAll = { onOpenReport(kind) },
                    onOpenCategoryActivity = onOpenCategoryActivity,
                    onEmptyAction = onAddTransaction,
                    emptyActionLabel = "Add transaction",
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    kind: ReportKind,
    data: ReportDataBundle,
    window: ReportWindow,
    onSeeAll: () -> Unit,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
    onEmptyAction: () -> Unit,
    emptyActionLabel: String,
) {
    val empty = reportHasNoData(kind, data)
    var selectedNetWorthIndex by remember(data.netWorth) { mutableStateOf<Int?>(null) }
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            ReportCardHeader(kind, onSeeAll)
            if (kind != ReportKind.NET_WORTH && !empty) {
                ReportSummary(kind, data, compact = true)
            }
            if (kind == ReportKind.NET_WORTH && !empty) {
                ReportSummary(kind, data, compact = true, selectedNetWorthIndex = selectedNetWorthIndex)
            }
            if (empty) {
                EmptyState(
                    "No data for this period",
                    "Add transactions and sync to build this report.",
                    actionLabel = emptyActionLabel,
                    onAction = onEmptyAction,
                )
            } else {
                ReportLegend(kind, data)
                ReportChart(
                    kind,
                    data,
                    150.dp,
                    compact = true,
                    window = window,
                    onOpenCategoryActivity = onOpenCategoryActivity,
                    onNetWorthSelectionChange = { selectedNetWorthIndex = it },
                )
                if (kind == ReportKind.SPENDING_BY_CATEGORY) ReportTopCategoryRows(data, window, onSeeAll, onOpenCategoryActivity)
            }
        }
    }
}

/** The period selector at the top of the reports screen, writing its choices back to app state. */
@Composable
internal fun ReportsPeriodItem(
    state: AppState,
    availableMonthIds: List<String>,
    periodMode: ReportPeriodMode,
    rangePreset: ReportRangePreset,
) {
    ReportPeriodSelector(
        monthId = state.reportsMonthId,
        mode = periodMode,
        rangePreset = rangePreset,
        availableMonthIds = availableMonthIds,
        onMonthChange = { state.reportsMonthId = it },
        onModeChange = { state.reportsPeriodMode = it.name },
        onRangePresetChange = {
            state.reportsRangePreset = it.name
            it.monthCount?.let { months -> state.reportsRangeMonths = months }
        },
    )
}

/** Links to the custom reports, spending trends and spending forecast screens. */
@Composable
private fun ReportsMoreCard(
    saved: Int,
    onOpenInsight: (AppRoute) -> Unit,
) {
    SurfaceCard {
        ListRow(
            "Custom reports",
            if (saved == 0) "Build your own by category, payee or account" else "$saved saved",
            leading = RowLeading.Icon(Lucide.ChartBar),
            onClick = { onOpenInsight(AppRoute.CUSTOM_REPORTS) },
        )
        DividerLine()
        ListRow(
            "Spending trends",
            "Each category against past months",
            leading = RowLeading.Icon(Lucide.TrendingUp),
            onClick = { onOpenInsight(AppRoute.SPENDING_TRENDS) },
        )
        DividerLine()
        ListRow(
            "Spending forecast",
            "Where this month is heading",
            leading = RowLeading.Icon(Lucide.Gauge),
            onClick = { onOpenInsight(AppRoute.SPENDING_FORECAST) },
        )
    }
}

/** The tappable icon, title and chevron at the top of a report card. */
@Composable
private fun ReportCardHeader(
    kind: ReportKind,
    onSeeAll: () -> Unit,
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
}

/** The largest spending categories under the category chart, each opening its activity. */
@Composable
private fun ReportTopCategoryRows(
    data: ReportDataBundle,
    window: ReportWindow,
    onSeeAll: () -> Unit,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    val otherCategoryId = OTHER_CATEGORY_CHART_ID
    val listedCategories =
        data.categories.take(5) +
            categoryChartPoints(data.categories).filter { it.categoryId == otherCategoryId }
    listedCategories.forEach { point ->
        ListRow(
            point.category,
            trailing = formatReportCurrency(point.spendingMinor, data.displaySettings),
            leading = RowLeading.Icon(financeIcon(point.category)),
            onClick = {
                if (point.categoryId == otherCategoryId) {
                    onSeeAll()
                } else {
                    onOpenCategoryActivity(point.categoryId, point.category, window)
                }
            },
        )
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
