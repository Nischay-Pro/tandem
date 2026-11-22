@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.reports
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
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
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.charts.AreaChart
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.inputs.FilterPills
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.insights.ProgressBar
import app.tandemforactual.ui.feature.insights.RecordNames
import app.tandemforactual.ui.feature.insights.incomeCategoryIds
import app.tandemforactual.ui.feature.insights.rememberBudgetTransactions
import app.tandemforactual.ui.feature.insights.rememberRecordNames
import app.tandemforactual.ui.feature.insights.wholePercent
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Trash2
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal val ReportRange.label: String
    get() =
        when (this) {
            ReportRange.THIS_MONTH -> "This month"
            ReportRange.LAST_MONTH -> "Last month"
            ReportRange.LAST_3_MONTHS -> "Last 3 months"
            ReportRange.LAST_6_MONTHS -> "Last 6 months"
            ReportRange.LAST_12_MONTHS -> "Last 12 months"
            ReportRange.YEAR_TO_DATE -> "Year to date"
            ReportRange.LAST_YEAR -> "Last year"
            ReportRange.CUSTOM -> "Custom"
        }

internal val ReportFlow.label: String
    get() =
        when (this) {
            ReportFlow.SPENDING -> "Spending"
            ReportFlow.INCOME -> "Income"
            ReportFlow.NET -> "Net"
        }

internal val ReportGrouping.label: String
    get() =
        when (this) {
            ReportGrouping.CATEGORY -> "Category"
            ReportGrouping.PAYEE -> "Payee"
            ReportGrouping.ACCOUNT -> "Account"
        }

internal fun CustomReport.summary(): String {
    val filters = accountIds.size + categoryIds.size + payeeIds.size
    return listOfNotNull(
        "${flow.label} by ${grouping.label.lowercase()}",
        if (range == ReportRange.CUSTOM) "${formatLiveDate(startDate)} – ${formatLiveDate(endDate)}" else range.label,
        filters.takeIf { it > 0 }?.let { if (it == 1) "1 filter" else "$it filters" },
    ).joinToString(" · ")
}

internal fun RecordNames.group(
    grouping: ReportGrouping,
    key: String?,
): String =
    when (grouping) {
        ReportGrouping.CATEGORY -> category(key)
        ReportGrouping.PAYEE -> key?.let { payees[it] ?: "Deleted payee" } ?: "No payee"
        ReportGrouping.ACCOUNT -> key?.let { accounts[it] ?: "Deleted account" } ?: "No account"
    }

/** Every report the person has built and saved, with a way to start a new one. */
@Composable
internal fun CustomReportsScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val reports = mobileSession.planning.customReports
    val transactions = rememberBudgetTransactions(state, mobileSession)
    val income = remember(mobileSession.categoryRecords, mobileSession.categoryGroupRecords) { mobileSession.incomeCategoryIds() }
    val today = platformTodayIsoDate()
    val totals =
        remember(reports, transactions, income, today) {
            transactions?.let { list -> reports.associate { it.id to CustomReports.run(it, list, income, today).totalMinor } }
        }

    fun open(id: String) {
        state.selectedCustomReportId = id
        state.selectedCustomReportName = reports.firstOrNull { it.id == id }?.name.orEmpty()
        navigation.open(AppRoute.CUSTOM_REPORT)
    }
    PageColumn(verticalSpacing = TandemSpacing.large) {
        if (reports.isEmpty()) {
            EmptyState(
                "Build your own report",
                "Pick a period, group by category, payee or account, and filter down to what you want to see. Save it to come back later.",
                actionLabel = "New report",
                onAction = { open("") },
            )
            return@PageColumn
        }
        TandemActionButton("New report", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), icon = Lucide.Plus) { open("") }
        SectionCard("Saved reports", "${reports.size} saved") {
            reports.forEachIndexed { index, report ->
                if (index > 0) DividerLine()
                ListRow(
                    report.name,
                    report.summary(),
                    totals?.get(report.id)?.let(state::formatCurrency),
                    leading = RowLeading.Icon(Lucide.ChartBar),
                    onClick = { open(report.id) },
                )
            }
        }
    }
}

/** Builds a report from a period, a grouping and filters, shows it as a chart and a table, and saves it by name. */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun CustomReportScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val palette = tandemPalette
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
            result.count == 0 -> EmptyState("Nothing matches", "Try a longer period or fewer filters.")
            else -> ReportResult(state, draft, result, names)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            if (saved != null) {
                TandemActionButton("Delete", TandemActionButtonTone.DESTRUCTIVE, Modifier.weight(1f), icon = Lucide.Trash2) {
                    confirmDelete = true
                }
            }
            TandemActionButton(
                when {
                    saved == null -> "Save report"
                    saved == draft -> "Saved"
                    else -> "Save changes"
                },
                TandemActionButtonTone.PRIMARY,
                Modifier.weight(1f),
                icon = Lucide.Save,
                enabled = saved != draft,
            ) {
                if (saved == null) {
                    naming = true
                } else {
                    scope.launch { mobileSession.planning.saveCustomReport(state.selectedBudgetId, draft) }
                }
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
        val options =
            when (kind) {
                ReportFilterKind.ACCOUNTS -> names.accounts
                ReportFilterKind.CATEGORIES -> names.categories
                ReportFilterKind.PAYEES -> names.payees
            }.entries
                .sortedBy { it.value.lowercase() }
                .map { it.key to it.value }
        ChoiceSheet(kind.title, options, kind.selected(draft).toSet(), { picking = null }) { chosen ->
            draft = kind.apply(draft, options.map { it.first }.filter { it in chosen })
            picking = null
        }
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

internal enum class ReportFilterKind(
    val title: String,
) {
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
    PAYEES("Payees"),
    ;

    fun selected(report: CustomReport): List<String> =
        when (this) {
            ACCOUNTS -> report.accountIds
            CATEGORIES -> report.categoryIds
            PAYEES -> report.payeeIds
        }

    fun apply(
        report: CustomReport,
        ids: List<String>,
    ): CustomReport =
        when (this) {
            ACCOUNTS -> report.copy(accountIds = ids)
            CATEGORIES -> report.copy(categoryIds = ids)
            PAYEES -> report.copy(payeeIds = ids)
        }
}

@Composable
private fun ReportControls(
    draft: CustomReport,
    names: RecordNames,
    onChange: (CustomReport) -> Unit,
    onPick: (ReportFilterKind) -> Unit,
) {
    val palette = tandemPalette
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
            if (draft.range == ReportRange.CUSTOM) {
                Row(Modifier.padding(horizontal = TandemSpacing.large), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Column(Modifier.weight(1f)) {
                        TextFieldCard(
                            "From",
                            draft.startDate.orEmpty(),
                            { onChange(draft.copy(startDate = it.take(10))) },
                            TextFieldOptions(placeholder = "2026-01-01"),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        TextFieldCard(
                            "To",
                            draft.endDate.orEmpty(),
                            { onChange(draft.copy(endDate = it.take(10))) },
                            TextFieldOptions(placeholder = "2026-03-31"),
                        )
                    }
                }
            }
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
    }
}

@Composable
private fun ControlLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = TandemSpacing.large),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        fontWeight = FontWeight.SemiBold,
    )
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

@Composable
private fun ReportNameSheet(
    visible: Boolean,
    suggestion: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(visible) { mutableStateOf(suggestion) }
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader("Save report", onClose = onDismiss)
                TextFieldCard("Name", name, { name = it.take(60) }, TextFieldOptions(placeholder = "Dining out this year"))
                TandemActionButton("Save", TandemActionButtonTone.PRIMARY, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) {
                    onSave(name.trim())
                }
            }
        }
    }
}

/** Picks any number of items from a list; choosing none means all of them. */
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    initial: Set<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    val palette = tandemPalette
    var chosen by remember(title) { mutableStateOf(initial) }
    var query by remember(title) { mutableStateOf("") }
    TandemTaskSheet(visible = true, onDismissRequest = onDismiss, onDismissed = {}) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 440.dp).padding(horizontal = TandemSpacing.large)) {
            Column(Modifier.fillMaxWidth().padding(TandemSpacing.large), verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
                TandemModalHeader(title, onClose = onDismiss)
                if (options.size > 8) TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Filter the list"))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    options.filter { query.isBlank() || it.second.contains(query.trim(), ignoreCase = true) }.forEach { (id, label) ->
                        val selected = id in chosen
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { chosen = if (selected) chosen - id else chosen + id }
                                .padding(vertical = TandemSpacing.medium),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, Modifier.weight(1f), color = palette.text, fontSize = TandemTypography.body)
                            if (selected) Icon(Lucide.Check, "Selected", Modifier.size(18.dp), tint = palette.accent)
                        }
                    }
                }
                Text(
                    if (chosen.isEmpty()) "Nothing picked, so all ${title.lowercase()} are included." else "${chosen.size} picked",
                    color = palette.muted,
                    fontSize = TandemTypography.caption,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Clear", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = chosen.isNotEmpty()) {
                        chosen = emptySet()
                    }
                    TandemActionButton("Done", TandemActionButtonTone.PRIMARY, Modifier.weight(1f)) { onDone(chosen) }
                }
            }
        }
    }
}

private fun groupNoun(
    grouping: ReportGrouping,
    count: Int = 2,
): String =
    when (grouping) {
        ReportGrouping.CATEGORY -> if (count == 1) "category" else "categories"
        ReportGrouping.PAYEE -> if (count == 1) "payee" else "payees"
        ReportGrouping.ACCOUNT -> if (count == 1) "account" else "accounts"
    }

private fun String?.isIsoDate() = this != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(this)

private const val MAX_CHART_ROWS = 8
private const val MAX_TABLE_ROWS = 50
