@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.CustomReport
import app.tandemforactual.data.planning.CustomReportResult
import app.tandemforactual.data.planning.CustomReports
import app.tandemforactual.data.planning.ReportFlow
import app.tandemforactual.data.planning.ReportGrouping
import app.tandemforactual.data.planning.ReportRange
import app.tandemforactual.data.planning.ReportRow
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.wholePercent
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.RecordNames
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.incomeCategoryIds
import app.tandemforactual.ui.core.state.rememberBudgetTransactions
import app.tandemforactual.ui.core.state.rememberRecordNames
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.charts.AreaChart
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.feedback.ProgressBar
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.sheets.ChoiceSheet
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.launch

/** Builds a report from a period, a grouping and filters, shows it as a chart and a table, and saves it by name. */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun CustomReportScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val saved = mobileSession.planning.customReports.firstOrNull { it.id == state.selectedCustomReportId }
    var draft by remember(state.selectedCustomReportId) { mutableStateOf(saved ?: CustomReport("", "")) }
    var naming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<ReportFilterKind?>(null) }
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val names = mobileSession.rememberRecordNames()
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val today = platformTodayIsoDate()
    val customValid = draft.range != ReportRange.CUSTOM || (draft.startDate.isIsoDate() && draft.endDate.isIsoDate())
    val result =
        remember(draft, transactions, income, today, customValid) {
            if (customValid) transactions?.let { CustomReports.run(draft, it, income, today) } else null
        }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        ReportControls(draft, names, { draft = it }, { picking = it })
        when {
            !customValid -> InlineMessage("Enter both dates like 2026-01-31.", InlineMessageKind.INFO)
            result == null -> SkeletonRows()
            result.count == 0 -> EmptyStateCard("Nothing matches", "Try a longer period or fewer filters.")
            else -> ReportResult(state, draft, result, names)
        }
        CustomReportActions(saved, draft, onDelete = { confirmDelete = true }) {
            if (saved == null) {
                naming = true
            } else {
                scope.launch { mobileSession.planning.saveCustomReport(state.selectedBudgetId, draft) }
            }
        }
    }
    val suggestedName = draft.name.ifBlank { "${draft.flow.label} by ${draft.grouping.label.lowercase()}" }
    ReportNameSheet(naming, suggestedName, { naming = false }) { name ->
        val report = draft.copy(id = draft.id.ifBlank { Uuid.random().toString() }, name = name)
        scope.launch { mobileSession.planning.saveCustomReport(state.selectedBudgetId, report) }
        draft = report
        state.selectedCustomReportId = report.id
        state.selectedCustomReportName = report.name
        naming = false
    }
    picking?.let { kind ->
        ReportFilterChoiceSheet(kind, names, draft, { picking = null }) { draft = it }
    }
    if (confirmDelete && saved != null) {
        DestructiveConfirmationDialog(
            "Delete ${saved.name}?",
            "The report is removed from this budget on every device that shares planning data.",
            "Delete",
            "Cancel",
            onDismissRequest = { confirmDelete = false },
        ) {
            confirmDelete = false
            scope.launch { mobileSession.planning.deleteCustomReport(state.selectedBudgetId, saved.id) }
            navigation.back()
        }
    }
}

/** Lets the person pick which accounts, categories or payees a custom report includes. */
@Composable
private fun ReportFilterChoiceSheet(
    kind: ReportFilterKind,
    names: RecordNames,
    draft: CustomReport,
    onDismiss: () -> Unit,
    onDraftChange: (CustomReport) -> Unit,
) {
    val options =
        when (kind) {
            ReportFilterKind.ACCOUNTS -> names.accounts
            ReportFilterKind.CATEGORIES -> names.categories
            ReportFilterKind.PAYEES -> names.payees
        }.entries
            .sortedBy { it.value.lowercase() }
            .map { it.key to it.value }
    ChoiceSheet(kind.title, options, kind.selected(draft).toSet(), onDismiss) { chosen ->
        onDraftChange(kind.apply(draft, options.map { it.first }.filter { it in chosen }))
        onDismiss()
    }
}

@Composable
private fun ReportControls(
    draft: CustomReport,
    names: RecordNames,
    onChange: (CustomReport) -> Unit,
    onPick: (ReportFilterKind) -> Unit,
) {
    SurfaceCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = TandemSpacing.large)
                .animateContentSize(tandemMotionSpec(tandemReduceMotion, TandemMotion.REVEAL_MILLIS)),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            ControlLabel("Show")
            FilterPills(
                ReportFlow.entries.map { it to it.label },
                draft.flow,
                { onChange(draft.copy(flow = it)) },
                contentPadding = PaddingValues(horizontal = TandemSpacing.large),
            )
            ControlLabel("Group by")
            FilterPills(
                ReportGrouping.entries.map { it to it.label },
                draft.grouping,
                { onChange(draft.copy(grouping = it)) },
                contentPadding = PaddingValues(horizontal = TandemSpacing.large),
            )
            ControlLabel("Period")
            FilterPills(
                ReportRange.entries.map { it to it.label },
                draft.range,
                { onChange(draft.copy(range = it)) },
                contentPadding = PaddingValues(horizontal = TandemSpacing.large),
            )
            if (draft.range == ReportRange.CUSTOM) CustomRangeFields(draft, onChange)
            ReportFilterRows(draft, names, onPick)
        }
    }
}

/** One row per filter kind, summarising what the report includes. */
@Composable
private fun ReportFilterRows(
    draft: CustomReport,
    names: RecordNames,
    onPick: (ReportFilterKind) -> Unit,
) {
    val palette = tandemPalette
    Column {
        ReportFilterKind.entries.forEach { kind ->
            DividerLine()
            val chosen = kind.selected(draft)
            ListRow(
                kind.title,
                when {
                    chosen.isEmpty() -> "All ${kind.title.lowercase()}"
                    chosen.size <= 2 ->
                        chosen.joinToString {
                            when (kind) {
                                ReportFilterKind.ACCOUNTS -> names.group(ReportGrouping.ACCOUNT, it)
                                ReportFilterKind.CATEGORIES -> names.category(it)
                                ReportFilterKind.PAYEES -> names.group(ReportGrouping.PAYEE, it)
                            }
                        }
                    else -> "${chosen.size} ${kind.title.lowercase()}"
                },
                leading = RowLeading.None,
                options = ListRowOptions(dense = true, trailingColor = palette.muted),
                onClick = { onPick(kind) },
            )
        }
    }
}

@Composable
private fun ReportResult(
    state: AppState,
    report: CustomReport,
    result: CustomReportResult,
    names: RecordNames,
) {
    val palette = tandemPalette
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Text(
            "${report.flow.label} · ${formatLiveDate(result.from)} – ${formatLiveDate(result.to)}",
            color = palette.muted,
            fontSize = TandemTypography.body,
        )
        Text(
            state.formatCurrency(result.totalMinor),
            color = palette.text,
            fontSize = TandemTypography.heroTitle,
            fontWeight = FontWeight.SemiBold,
            fontFamily = tandemAlloFamily(),
        )
        Text(
            "${result.count} ${if (result.count == 1) "transaction" else "transactions"} across ${result.rows.size} " +
                groupNoun(report.grouping, result.rows.size),
            color = palette.muted,
            fontSize = TandemTypography.compactBody,
        )
    }
    if (result.months.size >= 3) {
        AreaChart(result.months.map { it.second }, "${report.flow.label} each month from ${formatLiveDate(result.from)}")
    }
    SectionCard("Chart", "Largest ${groupNoun(report.grouping)} first") {
        val top = result.rows.take(MAX_CHART_ROWS)
        val largest = top.maxOf { kotlin.math.abs(it.totalMinor) }.coerceAtLeast(1)
        Column(
            Modifier.fillMaxWidth().padding(TandemSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            top.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                    Row {
                        Text(
                            names.group(report.grouping, row.key),
                            Modifier.weight(1f).padding(end = TandemSpacing.small),
                            color = palette.text,
                            fontSize = TandemTypography.compactBody,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(state.formatCurrency(row.totalMinor), color = palette.text, fontSize = TandemTypography.compactBody)
                    }
                    ProgressBar(
                        kotlin.math.abs(row.totalMinor).toFloat() / largest,
                        if (row.totalMinor < 0) palette.negative else palette.accent,
                    )
                }
            }
            if (result.rows.size > top.size) {
                Text("${result.rows.size - top.size} more in the table", color = palette.muted, fontSize = TandemTypography.caption)
            }
        }
    }
    ReportTable(state, report, result, names)
}

@Composable
private fun ReportTable(
    state: AppState,
    report: CustomReport,
    result: CustomReportResult,
    names: RecordNames,
) {
    val palette = tandemPalette
    val showShare = report.flow != ReportFlow.NET && result.totalMinor > 0
    SectionCard("Table", null) {
        Column(Modifier.fillMaxWidth()) {
            TableRow(report.grouping.label, "Count", if (showShare) "Share" else null, "Total", header = true)
            result.rows.take(MAX_TABLE_ROWS).forEach { row: ReportRow ->
                DividerLine()
                TableRow(
                    names.group(report.grouping, row.key),
                    row.count.toString(),
                    if (showShare) wholePercent((row.totalMinor * 10_000 / result.totalMinor).toInt()) else null,
                    state.formatCurrency(row.totalMinor),
                )
            }
            DividerLine()
            TableRow(
                "Total",
                result.count.toString(),
                if (showShare) "100%" else null,
                state.formatCurrency(result.totalMinor),
                header = true,
            )
            if (result.rows.size > MAX_TABLE_ROWS) {
                Text(
                    "Showing the top $MAX_TABLE_ROWS of ${result.rows.size}.",
                    Modifier.padding(TandemSpacing.large),
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    name: String,
    count: String,
    share: String?,
    total: String,
    header: Boolean = false,
) {
    val palette = tandemPalette
    val color = if (header) palette.muted else palette.text
    val size = if (header) TandemTypography.caption else TandemTypography.compactBody
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        Text(name, Modifier.weight(1f), color = color, fontSize = size, fontWeight = weight, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(count, Modifier.widthIn(min = 44.dp), color = color, fontSize = size, fontWeight = weight, textAlign = TextAlign.End)
        share?.let {
            Text(
                it,
                Modifier.widthIn(min = 44.dp),
                color = color,
                fontSize = size,
                fontWeight = weight,
                textAlign = TextAlign.End,
            )
        }
        Text(
            total,
            Modifier.widthIn(min = 88.dp),
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

private const val MAX_CHART_ROWS = 8

private const val MAX_TABLE_ROWS = 50

private fun groupNoun(
    grouping: ReportGrouping,
    count: Int = 2,
): String =
    when (grouping) {
        ReportGrouping.CATEGORY -> if (count == 1) "category" else "categories"
        ReportGrouping.PAYEE -> if (count == 1) "payee" else "payees"
        ReportGrouping.ACCOUNT -> if (count == 1) "account" else "accounts"
    }
