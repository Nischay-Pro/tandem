@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvColumn
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvColumnMapping
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvLayout
import app.tandemforactual.ui.feature.transactions.statement.StatementDateOrder
import app.tandemforactual.ui.feature.transactions.statement.StatementParseResult
import app.tandemforactual.ui.feature.transactions.statement.statementCount
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X

@Composable
internal fun StatementImportDialog(
    visible: Boolean,
    state: AppState,
    currencyDisplaySettings: BudgetDisplaySettings?,
    accountName: String,
    filename: String,
    parseResult: StatementParseResult?,
    parseError: String?,
    actionMessage: String?,
    actionMessageKind: InlineMessageKind = InlineMessageKind.ERROR,
    dateOrder: StatementDateOrder,
    csvLayout: StatementCsvLayout?,
    csvMapping: StatementCsvColumnMapping,
    showCsvMapping: Boolean,
    importPreview: StatementImportPreview?,
    importPreviewLoading: Boolean,
    importPreviewError: String?,
    busy: Boolean,
    onDateOrderChange: (StatementDateOrder) -> Unit,
    onCsvMappingChange: (StatementCsvColumnMapping) -> Unit,
    onToggleCsvMapping: () -> Unit,
    onRetryImportPreview: () -> Unit,
    onDismiss: () -> Unit,
    onDismissed: () -> Unit,
    onImport: (StatementParseResult) -> Unit,
) {
    TandemTaskSheet(visible = visible, onDismissRequest = onDismiss, onDismissed = onDismissed) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                StatementImportHeader(busy, onDismiss)
                ListRow("File", trailing = filename.ifBlank { "Unavailable" }, leading = RowLeading.Icon(Lucide.Receipt))
                ListRow("Account", trailing = accountName, leading = RowLeading.Icon(Lucide.WalletCards))
                StatementParseStatus(parseResult != null, parseError, importPreviewLoading, importPreviewError, busy, onRetryImportPreview)
                actionMessage?.let {
                    InlineMessage(it, actionMessageKind)
                }
                if (parseResult != null && filename.substringAfterLast('.', "").equals("qif", ignoreCase = true)) {
                    StatementDateOrderPicker(
                        dateOrder = dateOrder,
                        enabled = !busy,
                        onDateOrderChange = onDateOrderChange,
                    )
                }
                if (csvLayout != null) {
                    StatementCsvSection(
                        csvLayout,
                        csvMapping,
                        dateOrder,
                        showCsvMapping,
                        busy,
                        onToggleCsvMapping,
                        onCsvMappingChange,
                        onDateOrderChange,
                    )
                }
                parseResult?.let { result ->
                    StatementImportResult(
                        state,
                        result,
                        importPreview,
                        importPreviewLoading,
                        busy,
                        currencyDisplaySettings,
                    ) { onImport(result) }
                }
            }
        }
    }
}

/** Progress and errors while the statement is read and checked against Actual. */
@Composable
private fun StatementParseStatus(
    parsed: Boolean,
    parseError: String?,
    importPreviewLoading: Boolean,
    importPreviewError: String?,
    busy: Boolean,
    onRetryImportPreview: () -> Unit,
) {
    if (!parsed && parseError == null) {
        InlineMessage("Reading statement…", InlineMessageKind.INFO)
    }
    parseError?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    if (parsed && importPreviewLoading) {
        InlineMessage("Checking existing transactions…", InlineMessageKind.INFO)
    }
    if (parsed && importPreviewError != null) {
        InlineMessage(importPreviewError, InlineMessageKind.ERROR)
        TandemActionButton(
            "Check again",
            TandemActionButtonTone.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !importPreviewLoading,
            onClick = onRetryImportPreview,
        )
    }
}

/** The CSV column summary row and, when open, the column mapping editor. */
@Composable
private fun StatementCsvSection(
    csvLayout: StatementCsvLayout,
    csvMapping: StatementCsvColumnMapping,
    dateOrder: StatementDateOrder,
    showCsvMapping: Boolean,
    busy: Boolean,
    onToggleCsvMapping: () -> Unit,
    onCsvMappingChange: (StatementCsvColumnMapping) -> Unit,
    onDateOrderChange: (StatementDateOrder) -> Unit,
) {
    ListRow(
        "CSV columns",
        trailing = if (showCsvMapping) "Done" else "Change",
        leading = RowLeading.None,
        options =
            ListRowOptions(dense = true),
        onClick = if (busy) null else onToggleCsvMapping,
    )
    if (showCsvMapping) {
        StatementCsvColumnMappingEditor(
            layout = csvLayout,
            mapping = csvMapping,
            dateOrder = dateOrder,
            enabled = !busy,
            onMappingChange = onCsvMappingChange,
            onDateOrderChange = onDateOrderChange,
        )
    }
}

/** The import statement title and close button. */
@Composable
private fun StatementImportHeader(
    busy: Boolean,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(Lucide.Receipt, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
        Text(
            "Import statement",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
        )
        TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, enabled = !busy, onClick = onDismiss)
    }
}

/** The parsed statement's counts, skipped rows, a short preview and the import button. */
@Composable
private fun StatementImportResult(
    state: AppState,
    result: StatementParseResult,
    importPreview: StatementImportPreview?,
    importPreviewLoading: Boolean,
    busy: Boolean,
    currencyDisplaySettings: BudgetDisplaySettings?,
    onImport: () -> Unit,
) {
    ListRow("Transactions", trailing = result.transactions.size.toString(), leading = RowLeading.Icon(Lucide.Receipt))
    importPreview?.let { preview ->
        if (preview.serverChecked) {
            ListRow(
                "New in Actual",
                trailing = preview.newRowCount(result.transactions.size).toString(),
                leading = RowLeading.None,
            )
            ListRow("Already in Actual", trailing = preview.existingImportedIds.size.toString(), leading = RowLeading.None)
        } else {
            InlineMessage(STATEMENT_IMPORT_CONNECTION_REQUIRED, InlineMessageKind.ERROR)
        }
    }
    if (result.skippedRows > 0) {
        val skippedCount = statementCount(result.skippedRows, "row")
        val skippedMessage =
            if (result.skippedRows == 1) {
                "Couldn’t read $skippedCount. It will be skipped."
            } else {
                "Couldn’t read $skippedCount. They will be skipped."
            }
        val reason = result.firstSkippedReason?.let { " First issue: $it" }.orEmpty()
        InlineMessage("$skippedMessage$reason", InlineMessageKind.INFO)
    }
    Text("Preview", color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
    result.transactions.take(5).forEach { transaction ->
        ListRow(
            "${transaction.date} · ${transaction.payeeName}",
            subtitle =
                buildList {
                    if (importPreview?.existingImportedIds?.contains(transaction.importedId) == true) {
                        add("Already in Actual")
                    }
                    transaction.notes?.let { add(it) }
                }.joinToString(" · ").ifBlank { null },
            trailing = formatCurrency(transaction.amountMinor, currencyDisplaySettings),
            leading = RowLeading.Icon(Lucide.Receipt),
        )
    }
    TandemActionButton(
        if (busy) "Importing…" else "Import ${statementCount(result.transactions.size, "transaction")}",
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Check,
        enabled = !busy && !state.readOnlyMode && importPreview?.serverChecked == true && !importPreviewLoading,
        loading = busy,
        onClick = onImport,
    )
}

@Composable
private fun StatementCsvColumnMappingEditor(
    layout: StatementCsvLayout,
    mapping: StatementCsvColumnMapping,
    dateOrder: StatementDateOrder,
    enabled: Boolean,
    onMappingChange: (StatementCsvColumnMapping) -> Unit,
    onDateOrderChange: (StatementDateOrder) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        StatementCsvColumnPicker("Date", layout.columns, mapping.dateColumn, enabled) { selected ->
            onMappingChange(mapping.copy(dateColumn = selected))
        }
        StatementCsvColumnPicker("Amount", layout.columns, mapping.amountColumn, enabled) { selected ->
            onMappingChange(mapping.copy(amountColumn = selected, debitColumn = null, creditColumn = null))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            StatementCsvColumnPicker("Debit", layout.columns, mapping.debitColumn, enabled, Modifier.weight(1f)) { selected ->
                onMappingChange(mapping.copy(amountColumn = null, debitColumn = selected))
            }
            StatementCsvColumnPicker("Credit", layout.columns, mapping.creditColumn, enabled, Modifier.weight(1f)) { selected ->
                onMappingChange(mapping.copy(amountColumn = null, creditColumn = selected))
            }
        }
        StatementCsvColumnPicker("Reference", layout.columns, mapping.referenceColumn, enabled) { selected ->
            onMappingChange(mapping.copy(referenceColumn = selected))
        }
        StatementCsvColumnPicker("Payee", layout.columns, mapping.payeeColumn, enabled) { selected ->
            onMappingChange(mapping.copy(payeeColumn = selected))
        }
        StatementCsvColumnPicker("Notes", layout.columns, mapping.notesColumn, enabled) { selected ->
            onMappingChange(mapping.copy(notesColumn = selected))
        }
        StatementDateOrderPicker(dateOrder, enabled, onDateOrderChange)
    }
}

@Composable
private fun StatementDateOrderPicker(
    dateOrder: StatementDateOrder,
    enabled: Boolean,
    onDateOrderChange: (StatementDateOrder) -> Unit,
) {
    Text("Date format", color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        StatementDateOrder.entries.forEach { option ->
            TandemActionButton(
                if (option == StatementDateOrder.MONTH_DAY_YEAR) "MM/DD/YYYY" else "DD/MM/YYYY",
                if (dateOrder == option) TandemActionButtonTone.PRIMARY else TandemActionButtonTone.NEUTRAL,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onClick = { onDateOrderChange(option) },
            )
        }
    }
}

@Composable
private fun StatementCsvColumnPicker(
    title: String,
    columns: List<StatementCsvColumn>,
    selectedIndex: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember(title, columns) { mutableStateOf(false) }
    Box(modifier) {
        ListRow(
            title,
            trailing = columns.firstOrNull { it.index == selectedIndex }?.name ?: "Choose column",
            leading = RowLeading.None,
            options =
                ListRowOptions(dense = true),
            onClick = if (enabled) ({ expanded = true }) else null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(onClick = {
                expanded = false
                onSelect(null)
            }) {
                Text("Not used", color = tandemPalette.text, fontSize = TandemTypography.compactBody)
            }
            columns.forEach { column ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(column.index)
                }) {
                    Text(column.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
