@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.LiveAccountSummary
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.WalletCards
import com.composables.icons.lucide.X
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ACCOUNT_RECONCILIATION_PAGE_SIZE = 500

@Composable
internal fun AccountDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to view its accounts.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
        return
    }
    val liveAccount = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountName = liveAccount?.name
    var accountActivity by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptyList<TransactionItem>()) }
    var accountActivityOffset by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(0) }
    var accountActivityHasMore by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var loadingOlderAccountActivity by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var clearedAccountBalanceMinor by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<Long?>(null) }
    var showReconcileDialog by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var selectedReconciliationIds by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptySet<String>()) }
    var statementBalanceText by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf("") }
    var pickedStatementFile by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<PickedStatementFile?>(null) }
    var statementParseResult by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<StatementParseResult?>(null) }
    var statementParseError by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<String?>(null) }
    var statementDateOrderName by rememberSaveable(state.selectedBudgetId, liveAccount?.id) {
        mutableStateOf(StatementDateOrder.MONTH_DAY_YEAR.name)
    }
    var statementCsvLayout by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<StatementCsvLayout?>(null) }
    var statementCsvMapping by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(StatementCsvColumnMapping()) }
    var showStatementCsvMapping by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var statementImportPreview by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<StatementImportPreview?>(null) }
    var statementImportPreviewError by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<String?>(null) }
    var statementImportPreviewLoading by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var statementImportPreviewRetry by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(0) }
    var showStatementImportDialog by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    val statementDateOrder =
        StatementDateOrder.entries.firstOrNull { it.name == statementDateOrderName } ?: StatementDateOrder.MONTH_DAY_YEAR
    val currencyFractionDigits = mobileSession.budgetDisplaySettings?.currencyFractionDigits
    val numberFormat = mobileSession.budgetDisplaySettings?.numberFormat
    val pickStatementFile =
        rememberStatementFilePicker { file ->
            pickedStatementFile = file
            statementParseResult = null
            statementImportPreview = null
            statementImportPreviewError = null
            statementCsvLayout = null
            statementCsvMapping = StatementCsvColumnMapping()
            statementParseError =
                file.errorMessage ?: if (file.contents == null) "Couldn’t read this file. Choose another statement." else null
            val isCsv = file.name.substringAfterLast('.', "").equals("csv", ignoreCase = true)
            if (isCsv && file.contents != null) {
                runCatching { inspectStatementCsv(file.contents) }
                    .onSuccess { layout ->
                        statementCsvLayout = layout
                        val currentHeaders = layout.columns.map { it.name }
                        val savedPreference =
                            statementCsvMappingPreferenceFor(
                                state.statementCsvMappingsJson,
                                state.serverUrl,
                                state.selectedBudgetId,
                                liveAccount?.id.orEmpty(),
                                currentHeaders,
                            )
                        statementCsvMapping = savedPreference?.columns ?: layout.suggestedMapping
                        if (savedPreference != null) statementDateOrderName = savedPreference.dateOrder.name
                        showStatementCsvMapping =
                            if (savedPreference == null) {
                                !layout.suggestedMapping.isValidFor(layout.columns.size)
                            } else {
                                !savedPreference.columns.isValidFor(layout.columns.size)
                            }
                    }.onFailure { error ->
                        statementParseError = (error as? StatementImportParseException)?.message ?: "Couldn’t read this CSV."
                        showStatementCsvMapping = true
                    }
            }
            showStatementImportDialog = true
        }
    LaunchedEffect(
        pickedStatementFile,
        statementDateOrder,
        currencyFractionDigits,
        numberFormat,
        statementCsvLayout,
        statementCsvMapping,
    ) {
        val file = pickedStatementFile ?: return@LaunchedEffect
        val contents = file.contents ?: return@LaunchedEffect
        statementParseResult = null
        statementParseError = null
        val fractionDigits = currencyFractionDigits
        if (fractionDigits == null) {
            statementParseError = "Sync this budget to load its currency settings, then choose the statement again."
            return@LaunchedEffect
        }
        try {
            statementParseResult =
                withContext(Dispatchers.Default) {
                    parseStatementImport(
                        file.name,
                        contents,
                        statementDateOrder,
                        fractionDigits,
                        numberFormat,
                        statementCsvMapping.takeIf { statementCsvLayout != null },
                    )
                }
            if (statementCsvLayout != null && statementCsvMapping.isUsable && liveAccount != null) {
                state.statementCsvMappingsJson =
                    updateStatementCsvMappingPreference(
                        state.statementCsvMappingsJson,
                        StatementCsvMappingPreference(
                            serverUrl = state.serverUrl,
                            budgetId = state.selectedBudgetId,
                            accountId = liveAccount.id,
                            headers = statementCsvLayout?.columns?.map { it.name }.orEmpty(),
                            dateOrder = statementDateOrder,
                            columns = statementCsvMapping,
                        ),
                    )
            }
        } catch (error: StatementImportParseException) {
            statementParseError = error.message ?: "Couldn’t read this statement. Choose a CSV, QIF, OFX, or QFX file."
        }
    }
    LaunchedEffect(
        statementParseResult,
        statementImportPreviewRetry,
        state.serverUrl,
        state.selectedBudgetId,
        state.encryptedPassword,
        liveAccount?.id,
    ) {
        val parsed = statementParseResult
        if (parsed == null || liveAccount == null) {
            statementImportPreview = null
            statementImportPreviewError = null
            statementImportPreviewLoading = false
            return@LaunchedEffect
        }
        statementImportPreview = null
        statementImportPreviewError = null
        statementImportPreviewLoading = true
        try {
            statementImportPreview =
                mobileSession.previewStatementImportTransactions(
                    state.selectedBudgetId,
                    liveAccount.id,
                    parsed.transactions.map { it.importedId },
                    state.encryptedPassword.takeIf(String::isNotBlank),
                )
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            statementImportPreviewError = error.userFacingMessage("Couldn’t check which rows are already in Actual. Try again.")
        } catch (_: Exception) {
            statementImportPreviewError = "Couldn’t check which rows are already in Actual. Try again."
        } finally {
            statementImportPreviewLoading = false
        }
    }
    LaunchedEffect(
        state.selectedBudgetId,
        liveAccount?.id,
        accountActivityOffset,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
        merchantIconMatcher,
    ) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val accountActivityFlow =
            mobileSession.observeAccountActivityRecords(
                state.selectedBudgetId,
                accountId,
                limit = ACCOUNT_RECONCILIATION_PAGE_SIZE,
                offset = accountActivityOffset,
            )
                ?: return@LaunchedEffect
        accountActivityFlow.collect { records ->
            val page =
                withContext(Dispatchers.Default) {
                    buildLiveTransactionItems(
                        records,
                        mobileSession.accountRecords,
                        mobileSession.payeeRecords,
                        mobileSession.categoryRecords,
                        mobileSession.budgetDisplaySettings,
                        merchantIconMatcher,
                    ).filterNot(TransactionItem::isSplitParent)
                }
            accountActivity =
                if (accountActivityOffset == 0) {
                    page
                } else {
                    (accountActivity + page).distinctBy(TransactionItem::recordId)
                }
            accountActivityHasMore = records.size == ACCOUNT_RECONCILIATION_PAGE_SIZE
            loadingOlderAccountActivity = false
        }
    }
    LaunchedEffect(state.selectedBudgetId, liveAccount?.id) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val clearedBalanceFlow = mobileSession.observeClearedAccountBalance(state.selectedBudgetId, accountId) ?: return@LaunchedEffect
        clearedBalanceFlow.collect { clearedAccountBalanceMinor = it }
    }
    val shownAccountActivity = accountActivity
    val offBudget = liveAccount?.offBudget ?: false
    val reconciliationCandidates =
        shownAccountActivity.filter { !it.isSplitParent && it.reconciled != true && it.recordId != null }
    val description = liveAccount?.let { if (it.offBudget) "Off budget" else "On budget" }.orEmpty()
    val balance = liveAccount?.balanceMinor?.let(state::formatCurrency) ?: "Unavailable"
    PageColumn {
        if (accountName == null) {
            Heading("Account details", "This account isn’t saved on this device.")
        } else {
            AmountHero("Available balance", balance, "$accountName · $description")
            if (liveAccount != null) {
                TandemActionButton(
                    "Reconcile",
                    TandemActionButtonTone.NEUTRAL,
                    Modifier.fillMaxWidth(),
                    icon = Lucide.Check,
                    enabled =
                        !liveAccount.closed &&
                            clearedAccountBalanceMinor != null &&
                            reconciliationCandidates.isNotEmpty() &&
                            !state.readOnlyMode,
                ) {
                    selectedReconciliationIds = emptySet()
                    statementBalanceText = ""
                    mobileSession.clearTransactionWriteMessage()
                    showReconcileDialog = true
                }
            }
            PrimaryButton(
                "Add transaction",
                enabled = !offBudget && !state.readOnlyMode,
            ) {
                navigation.newTransaction(liveAccount.id)
            }
            if (liveAccount != null) {
                TandemActionButton(
                    "Import statement",
                    TandemActionButtonTone.NEUTRAL,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Lucide.Receipt,
                    enabled = !liveAccount.closed && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    onClick = {
                        mobileSession.clearBudgetActionMessage()
                        pickStatementFile()
                    },
                )
            }
            if (!showStatementImportDialog) {
                budgetActionFeedbackMessage(mobileSession)
            }
            if (shownAccountActivity.isEmpty()) {
                SurfaceCard {
                    EmptyState("No recent activity", modifier = Modifier.padding(TandemSpacing.large))
                }
            } else {
                SectionCard("Recent activity", action = "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                    shownAccountActivity.take(6).forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = true) }
                    }
                }
            }
            SettingRow(
                "Account options",
                "Close account",
                onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) },
            )
        }
    }
    if (showReconcileDialog && liveAccount != null) {
        val didReconcile: (() -> Unit) -> Unit = { closeAfterSuccess ->
            scope.launch {
                if (
                    mobileSession.markTransactionsReconciled(
                        state.selectedBudgetId,
                        liveAccount.id,
                        reconciliationCandidates.mapNotNull { it.recordId }.toSet(),
                        selectedReconciliationIds,
                    )
                ) {
                    closeAfterSuccess()
                }
            }
        }
        AccountReconcileDialog(
            state = state,
            accountName = liveAccount.name,
            clearedBalanceMinor = clearedAccountBalanceMinor,
            transactions = reconciliationCandidates,
            hasMoreTransactions = accountActivityHasMore,
            loadingMoreTransactions = loadingOlderAccountActivity,
            onLoadMoreTransactions = {
                loadingOlderAccountActivity = true
                accountActivityOffset += ACCOUNT_RECONCILIATION_PAGE_SIZE
            },
            selectedIds = selectedReconciliationIds,
            statementBalance = statementBalanceText,
            busy = mobileSession.isSavingTransaction,
            message = mobileSession.transactionWriteMessage,
            onStatementBalanceChange = { statementBalanceText = it },
            onSelectedChange = { id, selected ->
                selectedReconciliationIds = if (selected) selectedReconciliationIds + id else selectedReconciliationIds - id
            },
            onDismiss = { if (!mobileSession.isSavingTransaction) showReconcileDialog = false },
            onConfirm = didReconcile,
        )
    }
    if (liveAccount != null) {
        StatementImportDialog(
            visible = showStatementImportDialog,
            state = state,
            currencyDisplaySettings = mobileSession.budgetDisplaySettings,
            accountName = liveAccount.name,
            filename = pickedStatementFile?.name.orEmpty(),
            parseResult = statementParseResult,
            parseError = statementParseError,
            actionMessage = mobileSession.budgetActionMessage,
            actionMessageKind = mobileSession.budgetActionMessageKind,
            dateOrder = statementDateOrder,
            csvLayout = statementCsvLayout,
            csvMapping = statementCsvMapping,
            showCsvMapping = showStatementCsvMapping,
            importPreview = statementImportPreview,
            importPreviewLoading = statementImportPreviewLoading,
            importPreviewError = statementImportPreviewError,
            busy = mobileSession.isPerformingBudgetAction,
            onDateOrderChange = {
                statementDateOrderName = it.name
                showStatementCsvMapping = true
            },
            onCsvMappingChange = {
                statementCsvMapping = it
                showStatementCsvMapping = true
            },
            onToggleCsvMapping = { showStatementCsvMapping = !showStatementCsvMapping },
            onRetryImportPreview = { statementImportPreviewRetry++ },
            onDismiss = {
                if (!mobileSession.isPerformingBudgetAction) {
                    showStatementImportDialog = false
                }
            },
            onDismissed = { pickedStatementFile = null },
            onImport = { parsed ->
                scope.launch {
                    val imported =
                        mobileSession.importStatementTransactions(
                            state.selectedBudgetId,
                            liveAccount.id,
                            parsed.toStatementImportItems(),
                            state.encryptedPassword.takeIf(String::isNotBlank),
                        )
                    if (imported) {
                        showStatementImportDialog = false
                    }
                }
            },
        )
    }
}

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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    Icon(Lucide.Receipt, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                    Text(
                        "Import statement",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                    )
                    TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, enabled = !busy, onClick = onDismiss)
                }
                ListRow("File", trailing = filename.ifBlank { "Unavailable" }, leading = RowLeading.Icon(Lucide.Receipt))
                ListRow("Account", trailing = accountName, leading = RowLeading.Icon(Lucide.WalletCards))
                if (parseResult == null && parseError == null) {
                    InlineMessage("Reading statement…", InlineMessageKind.INFO)
                }
                parseError?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                if (parseResult != null && importPreviewLoading) {
                    InlineMessage("Checking existing transactions…", InlineMessageKind.INFO)
                }
                if (parseResult != null && importPreviewError != null) {
                    InlineMessage(importPreviewError, InlineMessageKind.ERROR)
                    TandemActionButton(
                        "Check again",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && !importPreviewLoading,
                        onClick = onRetryImportPreview,
                    )
                }
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
                    ListRow(
                        "CSV columns",
                        trailing = if (showCsvMapping) "Done" else "Change",
                        leading = RowLeading.None,
                        options =
                            app.tandemforactual.ui.components
                                .ListRowOptions(dense = true),
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
                parseResult?.let { result ->
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
                            InlineMessage("Duplicates will be checked when this device syncs.", InlineMessageKind.INFO)
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
                        enabled = !busy && !state.readOnlyMode && importPreview != null && !importPreviewLoading,
                        loading = busy,
                        onClick = { onImport(result) },
                    )
                }
            }
        }
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
                app.tandemforactual.ui.components
                    .ListRowOptions(dense = true),
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
