@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatReportCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.ReportKind
import app.tandemforactual.ui.core.state.ReportPeriodMode
import app.tandemforactual.ui.core.state.ReportRangePreset
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageDashboardLazyColumn
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide

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
    val window = createReportWindow(state.reportsMonthId, periodMode, rangePreset, today, mobileSession.budgetMonthIds)
    if (state.selectedBudgetId.isBlank()) {
        Column(Modifier.fillMaxSize().padding(TandemSpacing.screen)) {
            EmptyStateCard(
                "Choose a budget",
                "Open an Actual budget to view this report.",
                actionLabel = "Choose budget",
                onAction = onEmptyAction,
            )
        }
        return
    }
    if (window == null) {
        Column(Modifier.fillMaxSize().padding(TandemSpacing.screen), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
            EmptyStateCard("Choose a report period", "Choose a month to see this report.")
        }
        return
    }
    val netWorthMonthIds = mobileSession.budgetMonthIds
    val netWorthWindow = createReportHistoryWindow(netWorthMonthIds, today) ?: window
    val selectedWindow = if (kind == ReportKind.NET_WORTH) netWorthWindow else window
    val data = rememberReportData(state, mobileSession, accounts, window, netWorthWindow, reportsDataCache)
    val chart = @Composable { ReportDetailChart(kind, data, selectedWindow, onOpenCategoryActivity) }
    val table =
        @Composable {
            ReportDataTable(kind, data, selectedWindow, state.budgetDisplaySettings, "All accounts", onOpenCategoryActivity)
        }
    PageDashboardLazyColumn(verticalSpacing = TandemSpacing.medium) { columns ->
        if (kind != ReportKind.NET_WORTH) {
            item(key = "report-detail-period") {
                ReportsPeriodItem(state, mobileSession.budgetMonthIds, periodMode, rangePreset)
            }
        }
        when {
            reportHasNoData(kind, data) -> {
                item(key = "report-detail-empty") { ReportDetailEmpty(onEmptyAction) }
            }

            columns > 1 -> {
                item(key = "report-detail-columns") { ReportDetailColumns(chart, table) }
            }

            else -> {
                item(key = "report-detail-chart") { chart() }
                item(key = "report-detail-table") { table() }
            }
        }
    }
}

/** The chart beside its table on wide windows, the chart getting the larger share. */
@Composable
private fun ReportDetailColumns(
    chart: @Composable () -> Unit,
    table: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(3f).semantics { isTraversalGroup = true }) { chart() }
        Box(Modifier.weight(2f).semantics { isTraversalGroup = true }) { table() }
    }
}

@Composable
private fun ReportDetailEmpty(onEmptyAction: () -> Unit) {
    SurfaceCard {
        EmptyState(
            "No report data yet",
            "Add transactions and sync your budget to build this report.",
            Modifier.padding(TandemSpacing.large),
            "Add transaction",
            onEmptyAction,
        )
    }
}

@Composable
private fun ReportDetailChart(
    kind: ReportKind,
    data: ReportDataBundle,
    window: ReportWindow,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    var selectedNetWorthIndex by remember(data.netWorth) { mutableStateOf<Int?>(null) }
    SurfaceCard {
        Column(
            Modifier.padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            if (kind == ReportKind.NET_WORTH) {
                ReportSummary(kind, data, compact = false, selectedNetWorthIndex = selectedNetWorthIndex)
            }
            if (kind != ReportKind.NET_WORTH) {
                ReportSummary(kind, data, compact = false)
            }
            ReportLegend(kind, data)
            ReportChart(
                kind,
                data,
                220.dp,
                compact = false,
                window = window,
                onOpenCategoryActivity = onOpenCategoryActivity,
                onNetWorthSelectionChange = { selectedNetWorthIndex = it },
            )
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
private fun ReportDataTable(
    kind: ReportKind,
    data: ReportDataBundle,
    window: ReportWindow,
    displaySettings: BudgetDisplaySettings?,
    netWorthScope: String,
    onOpenCategoryActivity: (String, String, ReportWindow) -> Unit,
) {
    val monthly = monthlyPoints(kind, data)
    SectionCard(if (kind == ReportKind.NET_WORTH) "Monthly history" else "Details") {
        when (kind) {
            ReportKind.SPENDING_BY_CATEGORY -> {
                data.categories.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    ListRow(
                        point.category,
                        trailing = formatReportCurrency(point.spendingMinor, displaySettings),
                        leading = RowLeading.Icon(financeIcon(point.category)),
                        onClick = { onOpenCategoryActivity(point.categoryId, point.category, window) },
                    )
                }
            }

            ReportKind.SPENDING_OVER_TIME -> {
                monthly.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    ReportTableRow(point.month, formatReportCurrency(point.spendingMinor, displaySettings))
                }
            }

            ReportKind.INCOME_AND_SPENDING -> {
                monthly.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    IncomeAndSpendingTableRow(point, displaySettings)
                }
            }

            ReportKind.BUDGETED_AND_SPENT -> {
                data.budgetedSpent.forEachIndexed { index, point ->
                    if (index > 0) DividerLine()
                    BudgetedSpentTableRow(point, displaySettings) {
                        onOpenCategoryActivity(point.categoryId, point.category, window)
                    }
                }
            }

            ReportKind.NET_WORTH -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = TandemSpacing.small),
                ) {
                    itemsIndexed(data.netWorth, key = { _, point -> point.monthId }) { index, point ->
                        if (index > 0) DividerLine()
                        val assets = if (netWorthScope == "On budget") point.onBudgetAssetsMinor else point.allAccountsAssetsMinor
                        val debts = if (netWorthScope == "On budget") point.onBudgetDebtsMinor else point.allAccountsDebtsMinor
                        NetWorthTableRow(point.month, assets, debts, displaySettings)
                    }
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
            amountAnnotatedString(formatReportCurrency(assets - debts, displaySettings), displaySettings?.currencyFractionDigits ?: 2),
            color = netAmountColor(assets - debts),
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            amountAnnotatedString("Assets: ${formatReportCurrency(assets, displaySettings)}", displaySettings?.currencyFractionDigits ?: 2),
            color = tandemPalette.positive,
            fontSize = TandemTypography.caption,
        )
        Text(
            amountAnnotatedString("Debts: ${formatReportCurrency(debts, displaySettings)}", displaySettings?.currencyFractionDigits ?: 2),
            color = tandemPalette.negative,
            fontSize = TandemTypography.caption,
        )
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
                amountAnnotatedString(
                    "Income ${formatReportCurrency(point.incomeMinor, displaySettings)}",
                    displaySettings?.currencyFractionDigits ?: 2,
                ),
                modifier = Modifier.weight(1f),
                color = tandemPalette.positive,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                amountAnnotatedString(
                    "Spending ${formatReportCurrency(point.spendingMinor, displaySettings)}",
                    displaySettings?.currencyFractionDigits ?: 2,
                ),
                modifier = Modifier.weight(1f),
                color = tandemPalette.negative,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Clip,
            )
        }
        Text(
            amountAnnotatedString(
                "Net ${formatReportCurrency(point.netMinor, displaySettings)}",
                displaySettings?.currencyFractionDigits ?: 2,
            ),
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
                amountAnnotatedString(
                    "Budgeted ${formatReportCurrency(point.budgetedMinor, displaySettings)}",
                    displaySettings?.currencyFractionDigits ?: 2,
                ),
                modifier = Modifier.weight(1f),
                color = tandemPalette.positive,
                fontSize = TandemTypography.caption,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                amountAnnotatedString(
                    "Spent ${formatReportCurrency(point.spentMinor, displaySettings)}",
                    displaySettings?.currencyFractionDigits ?: 2,
                ),
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
