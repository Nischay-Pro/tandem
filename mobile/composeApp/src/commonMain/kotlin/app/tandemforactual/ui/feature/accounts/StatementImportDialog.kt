@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.importing.StatementCsvColumn
import app.tandemforactual.ui.core.importing.StatementCsvColumnMapping
import app.tandemforactual.ui.core.importing.StatementCsvLayout
import app.tandemforactual.ui.core.importing.StatementDateOrder
import app.tandemforactual.ui.core.importing.StatementReview
import app.tandemforactual.ui.core.importing.StatementRowAction
import app.tandemforactual.ui.core.importing.description
import app.tandemforactual.ui.core.importing.statementApplyLabel
import app.tandemforactual.ui.core.importing.statementCount
import app.tandemforactual.ui.core.importing.summary
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.X

/** What the statement import sheet shows: the file, how it was read, and the row-by-row review. */
internal data class StatementImportContent(
    val accountName: String,
    val filename: String,
    val showDateOrder: Boolean,
    val parsed: Boolean,
    val parseError: String?,
    val skippedRows: Int,
    val firstSkippedReason: String?,
    val review: StatementReview?,
    val actions: Map<Int, StatementRowAction>,
    val flipSigns: Boolean,
    val ledgerLoading: Boolean,
    val ledgerError: String?,
    val dateOrder: StatementDateOrder,
    val csvLayout: StatementCsvLayout?,
    val csvMapping: StatementCsvColumnMapping,
    val showCsvMapping: Boolean,
)

internal class StatementImportCallbacks(
    val onDateOrderChange: (StatementDateOrder) -> Unit,
    val onCsvMappingChange: (StatementCsvColumnMapping) -> Unit,
    val onToggleCsvMapping: () -> Unit,
    val onRetryLedger: () -> Unit,
    val onFlipSigns: () -> Unit,
    val onAction: (Int, StatementRowAction) -> Unit,
    val onDismiss: () -> Unit,
    val onDismissed: () -> Unit,
    val onApply: () -> Unit,
)

@Composable
internal fun StatementImportDialog(
    visible: Boolean,
    state: AppState,
    content: StatementImportContent,
    currencyDisplaySettings: BudgetDisplaySettings?,
    actionMessage: String?,
    actionMessageKind: InlineMessageKind = InlineMessageKind.ERROR,
    busy: Boolean,
    callbacks: StatementImportCallbacks,
) {
    val review = content.review
    TandemTaskSheet(visible = visible, onDismissRequest = callbacks.onDismiss, onDismissed = callbacks.onDismissed) {
        SheetCard(
            modifier = Modifier.heightIn(max = 760.dp),
            maxWidth = 560.dp,
            header = { StatementImportHeader(busy, callbacks.onDismiss) },
            actions =
                review?.let {
                    { StatementApplyBar(it, content.actions, busy || state.readOnlyMode, busy, callbacks.onApply) }
                },
        ) {
            ListRow("File", trailing = content.filename.ifBlank { "Unavailable" }, leading = RowLeading.Icon(Lucide.Receipt))
            ListRow("Account", trailing = content.accountName, leading = RowLeading.Account(content.accountName))
            StatementParseStatus(content, busy, callbacks.onRetryLedger)
            actionMessage?.let { InlineMessage(it, actionMessageKind) }
            if (content.parsed && content.showDateOrder) {
                StatementDateOrderPicker(content.dateOrder, !busy, callbacks.onDateOrderChange)
            }
            content.csvLayout?.let { layout -> StatementCsvSection(layout, content, busy, callbacks) }
            if (content.skippedRows > 0) StatementSkippedRows(content.skippedRows, content.firstSkippedReason)
            review?.let {
                StatementReviewSection(it, content.actions, content.flipSigns, currencyDisplaySettings, !busy, callbacks)
            }
        }
    }
}

/** Progress and errors while the statement is read and compared with the account. */
@Composable
private fun StatementParseStatus(
    content: StatementImportContent,
    busy: Boolean,
    onRetryLedger: () -> Unit,
) {
    if (!content.parsed && content.parseError == null) InlineMessage("Reading statement…", InlineMessageKind.INFO)
    content.parseError?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    if (content.parsed && content.ledgerLoading) InlineMessage("Comparing with this account…", InlineMessageKind.INFO)
    if (content.parsed && content.ledgerError != null) {
        InlineMessage(content.ledgerError, InlineMessageKind.ERROR)
        TandemActionButton(
            "Try again",
            TandemActionButtonTone.NEUTRAL,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && !content.ledgerLoading,
            onClick = onRetryLedger,
        )
    }
}

@Composable
private fun StatementSkippedRows(
    skippedRows: Int,
    firstSkippedReason: String?,
) {
    val skippedCount = statementCount(skippedRows, "row")
    val pronoun = if (skippedRows == 1) "It" else "They"
    val reason = firstSkippedReason?.let { " First issue: $it" }.orEmpty()
    InlineMessage("Couldn’t read $skippedCount. $pronoun will be skipped.$reason", InlineMessageKind.INFO)
}

/** The summary of what Apply will do and the Apply button, pinned under the review. */
@Composable
private fun StatementApplyBar(
    review: StatementReview,
    actions: Map<Int, StatementRowAction>,
    disabled: Boolean,
    busy: Boolean,
    onApply: () -> Unit,
) {
    val summary = review.summary(actions)
    Text(summary.description(), color = tandemPalette.muted, fontSize = TandemTypography.compactBody)
    TandemActionButton(
        statementApplyLabel(summary, busy),
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Check,
        enabled = !disabled && summary.changes > 0,
        loading = busy,
        onClick = onApply,
    )
}

/** The CSV column summary row and, when open, the column mapping editor. */
@Composable
private fun StatementCsvSection(
    csvLayout: StatementCsvLayout,
    content: StatementImportContent,
    busy: Boolean,
    callbacks: StatementImportCallbacks,
) {
    ListRow(
        "CSV columns",
        trailing = if (content.showCsvMapping) "Done" else "Change",
        leading = RowLeading.None,
        options = ListRowOptions(dense = true),
        onClick = if (busy) null else callbacks.onToggleCsvMapping,
    )
    if (content.showCsvMapping) {
        StatementCsvColumnMappingEditor(
            layout = csvLayout,
            mapping = content.csvMapping,
            dateOrder = content.dateOrder,
            enabled = !busy,
            onMappingChange = callbacks.onCsvMappingChange,
            onDateOrderChange = callbacks.onDateOrderChange,
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
