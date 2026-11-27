@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.StatementLedgerTransaction
import app.tandemforactual.ui.core.importing.StatementCsvColumnMapping
import app.tandemforactual.ui.core.importing.StatementCsvLayout
import app.tandemforactual.ui.core.importing.StatementCsvMappingPreference
import app.tandemforactual.ui.core.importing.StatementDateOrder
import app.tandemforactual.ui.core.importing.StatementFormat
import app.tandemforactual.ui.core.importing.StatementImportParseException
import app.tandemforactual.ui.core.importing.StatementParseResult
import app.tandemforactual.ui.core.importing.StatementReview
import app.tandemforactual.ui.core.importing.StatementRowAction
import app.tandemforactual.ui.core.importing.applyItems
import app.tandemforactual.ui.core.importing.choose
import app.tandemforactual.ui.core.importing.defaultActions
import app.tandemforactual.ui.core.importing.detectStatementFormat
import app.tandemforactual.ui.core.importing.inspectStatementCsv
import app.tandemforactual.ui.core.importing.parseStatementImport
import app.tandemforactual.ui.core.importing.reviewStatement
import app.tandemforactual.ui.core.importing.statementCsvMappingPreferenceFor
import app.tandemforactual.ui.core.importing.updateStatementCsvMappingPreference
import app.tandemforactual.ui.core.importing.withFlippedSigns
import app.tandemforactual.ui.core.platform.PickedStatementFile
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.importStatementTransactions
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Picked statement file, parse result, CSV mapping, the account's matching transactions and the user's choices per row. */
internal class StatementImportState(
    pickedFile: MutableState<PickedStatementFile?>,
    parseResult: MutableState<StatementParseResult?>,
    parseError: MutableState<String?>,
    dateOrderName: MutableState<String>,
    csvLayout: MutableState<StatementCsvLayout?>,
    csvMapping: MutableState<StatementCsvColumnMapping>,
    showCsvMapping: MutableState<Boolean>,
    ledger: MutableState<List<StatementLedgerTransaction>?>,
    ledgerError: MutableState<String?>,
    ledgerLoading: MutableState<Boolean>,
    ledgerRetry: MutableState<Int>,
    showDialog: MutableState<Boolean>,
    isQif: MutableState<Boolean>,
    flipSigns: MutableState<Boolean>,
    actions: MutableState<Map<Int, StatementRowAction>>,
) {
    var pickedFile by pickedFile
    var parseResult by parseResult
    var parseError by parseError
    var dateOrderName by dateOrderName
    var csvLayout by csvLayout
    var csvMapping by csvMapping
    var showCsvMapping by showCsvMapping
    var ledger by ledger
    var ledgerError by ledgerError
    var ledgerLoading by ledgerLoading
    var ledgerRetry by ledgerRetry
    var showDialog by showDialog
    var isQif by isQif
    var flipSigns by flipSigns
    var actions by actions

    val dateOrder: StatementDateOrder
        get() = StatementDateOrder.entries.firstOrNull { it.name == dateOrderName } ?: StatementDateOrder.MONTH_DAY_YEAR

    /** The parsed rows as the review uses them, signs reversed when the user asked. */
    val statement: StatementParseResult?
        get() = parseResult?.let { if (flipSigns) it.withFlippedSigns() else it }

    fun review(): StatementReview? {
        val rows = statement ?: return null
        return reviewStatement(rows.transactions, ledger ?: return null)
    }

    fun onFilePicked(
        file: PickedStatementFile,
        state: AppState,
        liveAccount: LiveAccountSummary?,
    ) {
        pickedFile = file
        parseResult = null
        ledger = null
        ledgerError = null
        flipSigns = false
        actions = emptyMap()
        csvLayout = null
        csvMapping = StatementCsvColumnMapping()
        parseError =
            file.errorMessage ?: if (file.contents == null) "Couldn’t read this file. Choose another statement." else null
        val format = file.contents?.let { runCatching { detectStatementFormat(it) }.getOrNull() }
        isQif = format == StatementFormat.QIF
        if (format == StatementFormat.DELIMITED && file.contents != null) {
            runCatching { inspectStatementCsv(file.contents) }
                .onSuccess { layout -> applyCsvLayout(layout, state, liveAccount) }
                .onFailure { error ->
                    parseError = (error as? StatementImportParseException)?.message ?: "Couldn’t read this CSV."
                    showCsvMapping = true
                }
        }
        showDialog = true
    }

    private fun applyCsvLayout(
        layout: StatementCsvLayout,
        state: AppState,
        liveAccount: LiveAccountSummary?,
    ) {
        csvLayout = layout
        val savedPreference =
            statementCsvMappingPreferenceFor(
                state.statementCsvMappingsJson,
                state.serverUrl,
                state.selectedBudgetId,
                liveAccount?.id.orEmpty(),
                layout.columns.map { it.name },
            )
        csvMapping = savedPreference?.columns ?: layout.suggestedMapping
        if (savedPreference != null) dateOrderName = savedPreference.dateOrder.name
        showCsvMapping = !(savedPreference?.columns ?: layout.suggestedMapping).isValidFor(layout.columns.size)
    }
}

@Composable
internal fun rememberStatementImportState(
    state: AppState,
    liveAccount: LiveAccountSummary?,
): StatementImportState {
    val budgetId = state.selectedBudgetId
    val accountId = liveAccount?.id
    return StatementImportState(
        pickedFile = remember(budgetId, accountId) { mutableStateOf(null) },
        parseResult = remember(budgetId, accountId) { mutableStateOf(null) },
        parseError = remember(budgetId, accountId) { mutableStateOf(null) },
        dateOrderName = rememberSaveable(budgetId, accountId) { mutableStateOf(StatementDateOrder.MONTH_DAY_YEAR.name) },
        csvLayout = remember(budgetId, accountId) { mutableStateOf(null) },
        csvMapping = remember(budgetId, accountId) { mutableStateOf(StatementCsvColumnMapping()) },
        showCsvMapping = remember(budgetId, accountId) { mutableStateOf(false) },
        ledger = remember(budgetId, accountId) { mutableStateOf(null) },
        ledgerError = remember(budgetId, accountId) { mutableStateOf(null) },
        ledgerLoading = remember(budgetId, accountId) { mutableStateOf(false) },
        ledgerRetry = remember(budgetId, accountId) { mutableStateOf(0) },
        showDialog = remember(budgetId, accountId) { mutableStateOf(false) },
        isQif = remember(budgetId, accountId) { mutableStateOf(false) },
        flipSigns = remember(budgetId, accountId) { mutableStateOf(false) },
        actions = remember(budgetId, accountId) { mutableStateOf(emptyMap()) },
    )
}

internal suspend fun parseStatement(
    statementImport: StatementImportState,
    state: AppState,
    liveAccount: LiveAccountSummary?,
    currencyFractionDigits: Int?,
    numberFormat: String?,
) {
    val file = statementImport.pickedFile ?: return
    val contents = file.contents ?: return
    statementImport.parseResult = null
    statementImport.parseError = null
    if (currencyFractionDigits == null) {
        statementImport.parseError = "Sync this budget to load its currency settings, then choose the statement again."
        return
    }
    val statementDateOrder = statementImport.dateOrder
    val mapping = statementImport.csvMapping.takeIf { statementImport.csvLayout != null }
    try {
        statementImport.parseResult =
            withContext(Dispatchers.Default) {
                parseStatementImport(file.name, contents, statementDateOrder, currencyFractionDigits, numberFormat, mapping)
            }
        val layout = statementImport.csvLayout
        if (layout != null && statementImport.csvMapping.isUsable && liveAccount != null) {
            state.statementCsvMappingsJson =
                updateStatementCsvMappingPreference(
                    state.statementCsvMappingsJson,
                    StatementCsvMappingPreference(
                        serverUrl = state.serverUrl,
                        budgetId = state.selectedBudgetId,
                        accountId = liveAccount.id,
                        headers = layout.columns.map { it.name },
                        dateOrder = statementDateOrder,
                        columns = statementImport.csvMapping,
                    ),
                )
        }
    } catch (error: StatementImportParseException) {
        statementImport.parseError = error.message ?: "Couldn’t read this statement. Choose a CSV, TSV, QIF, OFX, or QFX file."
    }
}

/** Statement import dialog for the account, wired to the import state and budget edits. */
@Composable
internal fun StatementImportFlow(
    statementImport: StatementImportState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary,
    scope: CoroutineScope,
) {
    val review = remember(statementImport.statement, statementImport.ledger) { statementImport.review() }
    val actions = remember(review, statementImport.actions) { review?.defaultActions().orEmpty() + statementImport.actions }
    StatementImportDialog(
        visible = statementImport.showDialog,
        state = state,
        content =
            StatementImportContent(
                accountName = liveAccount.name,
                filename = statementImport.pickedFile?.name.orEmpty(),
                showDateOrder = statementImport.isQif,
                parsed = statementImport.parseResult != null,
                parseError = statementImport.parseError,
                skippedRows = statementImport.parseResult?.skippedRows ?: 0,
                firstSkippedReason = statementImport.parseResult?.firstSkippedReason,
                review = review,
                actions = actions,
                flipSigns = statementImport.flipSigns,
                ledgerLoading = statementImport.ledgerLoading,
                ledgerError = statementImport.ledgerError,
                dateOrder = statementImport.dateOrder,
                csvLayout = statementImport.csvLayout,
                csvMapping = statementImport.csvMapping,
                showCsvMapping = statementImport.showCsvMapping,
            ),
        currencyDisplaySettings = mobileSession.budgetDisplaySettings,
        actionMessage = mobileSession.budgetActionMessage,
        actionMessageKind = mobileSession.feedback.budgetActionMessageKind,
        busy = mobileSession.isPerformingBudgetAction,
        callbacks =
            StatementImportCallbacks(
                onDateOrderChange = {
                    statementImport.dateOrderName = it.name
                    statementImport.showCsvMapping = true
                },
                onCsvMappingChange = {
                    statementImport.csvMapping = it
                    statementImport.showCsvMapping = true
                },
                onToggleCsvMapping = { statementImport.showCsvMapping = !statementImport.showCsvMapping },
                onRetryLedger = { statementImport.ledgerRetry++ },
                onFlipSigns = {
                    statementImport.flipSigns = !statementImport.flipSigns
                    statementImport.actions = emptyMap()
                },
                onAction = { index, action -> statementImport.actions = actions.choose(index, action) },
                onDismiss = {
                    if (!mobileSession.isPerformingBudgetAction) statementImport.showDialog = false
                },
                onDismissed = { statementImport.pickedFile = null },
                onApply = {
                    val items = review?.applyItems(actions).orEmpty()
                    scope.launch {
                        val queued =
                            mobileSession.budgetEdits.importStatementTransactions(
                                state.selectedBudgetId,
                                liveAccount.id,
                                items,
                                state.encryptedPassword.takeIf(String::isNotBlank),
                            )
                        if (queued) statementImport.showDialog = false
                    }
                },
            ),
    )
}
