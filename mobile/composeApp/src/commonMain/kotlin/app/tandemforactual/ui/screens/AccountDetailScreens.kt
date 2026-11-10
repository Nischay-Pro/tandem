@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
    var showStatementImportDialog by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    val statementDateOrder =
        StatementDateOrder.entries.firstOrNull { it.name == statementDateOrderName } ?: StatementDateOrder.MONTH_DAY_YEAR
    val currencyFractionDigits = mobileSession.budgetDisplaySettings?.currencyFractionDigits
    val numberFormat = mobileSession.budgetDisplaySettings?.numberFormat
    val pickStatementFile =
        rememberStatementFilePicker { file ->
            pickedStatementFile = file
            statementParseResult = null
            statementParseError =
                file.errorMessage ?: if (file.contents == null) "Couldn’t read this file. Choose another statement." else null
            showStatementImportDialog = true
        }
    LaunchedEffect(pickedStatementFile, statementDateOrder, currencyFractionDigits, numberFormat) {
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
                    parseStatementImport(file.name, contents, statementDateOrder, fractionDigits, numberFormat)
                }
        } catch (error: StatementImportParseException) {
            statementParseError = error.message ?: "Couldn’t read this statement. Choose a CSV, QIF, or OFX file."
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
            busy = mobileSession.isPerformingBudgetAction,
            onDateOrderChange = { statementDateOrderName = it.name },
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
                            parsed.transactions.map { row ->
                                app.tandemforactual.api.client.StatementImportItem(
                                    date = row.date,
                                    amountMinor = row.amountMinor,
                                    payeeName = row.payeeName,
                                    notes = row.notes,
                                    importedId = row.importedId,
                                )
                            },
                            state.encryptedPassword.takeIf(String::isNotBlank),
                        )
                    if (imported != null) {
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
    busy: Boolean,
    onDateOrderChange: (StatementDateOrder) -> Unit,
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
                actionMessage?.let {
                    InlineMessage(it, actionMessageKind)
                }
                parseResult?.let { result ->
                    Text("Date format", color = tandemPalette.text, fontSize = TandemTypography.body, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        StatementDateOrder.entries.forEach { option ->
                            TandemActionButton(
                                if (option == StatementDateOrder.MONTH_DAY_YEAR) "MM/DD/YYYY" else "DD/MM/YYYY",
                                if (dateOrder == option) TandemActionButtonTone.PRIMARY else TandemActionButtonTone.NEUTRAL,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                onClick = { onDateOrderChange(option) },
                            )
                        }
                    }
                    ListRow("Transactions", trailing = result.transactions.size.toString(), leading = RowLeading.Icon(Lucide.Receipt))
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
                            subtitle = transaction.notes,
                            trailing = formatCurrency(transaction.amountMinor, currencyDisplaySettings),
                            leading = RowLeading.Icon(Lucide.Receipt),
                        )
                    }
                    TandemActionButton(
                        if (busy) "Importing…" else "Import ${statementCount(result.transactions.size, "transaction")}",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Lucide.Check,
                        enabled = !busy && !state.readOnlyMode,
                        loading = busy,
                        onClick = { onImport(result) },
                    )
                }
            }
        }
    }
}
