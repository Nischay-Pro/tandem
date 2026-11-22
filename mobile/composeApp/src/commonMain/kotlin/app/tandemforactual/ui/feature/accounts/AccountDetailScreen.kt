@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.ui.core.platform.PickedStatementFile
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.importStatementTransactions
import app.tandemforactual.ui.core.session.budget.markTransactionsReconciled
import app.tandemforactual.ui.core.session.budget.previewStatementImportTransactions
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.observeAccountActivityRecords
import app.tandemforactual.ui.core.session.observeClearedAccountBalance
import app.tandemforactual.ui.core.session.userFacingMessage
import app.tandemforactual.ui.core.state.AccountActivitySnapshot
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.buildLiveTransactionItems
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.SettingRow
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.openAccountActivity
import app.tandemforactual.ui.feature.currency.approximateInBudgetCurrency
import app.tandemforactual.ui.feature.currency.formatAccountMoney
import app.tandemforactual.ui.feature.transactions.TransactionRow
import app.tandemforactual.ui.feature.transactions.openTransaction
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvColumnMapping
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvLayout
import app.tandemforactual.ui.feature.transactions.statement.StatementCsvMappingPreference
import app.tandemforactual.ui.feature.transactions.statement.StatementDateOrder
import app.tandemforactual.ui.feature.transactions.statement.StatementImportParseException
import app.tandemforactual.ui.feature.transactions.statement.StatementParseResult
import app.tandemforactual.ui.feature.transactions.statement.inspectStatementCsv
import app.tandemforactual.ui.feature.transactions.statement.parseStatementImport
import app.tandemforactual.ui.feature.transactions.statement.statementCsvMappingPreferenceFor
import app.tandemforactual.ui.feature.transactions.statement.toStatementImportItems
import app.tandemforactual.ui.feature.transactions.statement.updateStatementCsvMappingPreference
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Receipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    merchantIconMatcher: MerchantIconMatcherPack?,
    isForegroundRoute: Boolean,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to view its accounts.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.SWITCH_BUDGET) },
        )
        return
    }
    val liveAccount = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountName = liveAccount?.name
    val routeScrollState = LocalRouteScrollState.current
    var pendingScrollRestore by
        rememberSaveable(state.selectedBudgetId, state.selectedAccountId) {
            mutableIntStateOf(-1)
        }
    var accountRouteWasCovered by
        rememberSaveable(state.selectedBudgetId, state.selectedAccountId) {
            mutableStateOf(false)
        }
    var accountActivity by
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(
                mobileSession.activity
                    .cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())
                    ?.transactions
                    .orEmpty(),
            )
        }
    var accountActivityOffset by
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(mobileSession.activity.cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())?.offset ?: 0)
        }
    var accountActivityHasMore by
        remember(mobileSession, state.selectedBudgetId, liveAccount?.id) {
            mutableStateOf(
                mobileSession.activity.cachedAccountActivity(state.selectedBudgetId, liveAccount?.id.orEmpty())?.hasMore ?: false,
            )
        }
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
                mobileSession.budgetEdits.previewStatementImportTransactions(
                    state.selectedBudgetId,
                    liveAccount.id,
                    parsed.transactions.map { it.importedId },
                    state.encryptedPassword.takeIf(String::isNotBlank),
                )
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            statementImportPreviewError = error.userFacingMessage(STATEMENT_IMPORT_CONNECTION_REQUIRED)
        } catch (_: Exception) {
            statementImportPreviewError = STATEMENT_IMPORT_CONNECTION_REQUIRED
        } finally {
            statementImportPreviewLoading = false
        }
    }
    LaunchedEffect(
        mobileSession.databaseGeneration,
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
            val nextActivity =
                if (accountActivityOffset == 0) {
                    page
                } else {
                    (accountActivity + page).distinctBy(TransactionItem::recordId)
                }
            val hasMore = records.size == ACCOUNT_RECONCILIATION_PAGE_SIZE
            accountActivity = nextActivity
            accountActivityHasMore = hasMore
            mobileSession.activity.cacheAccountActivity(
                state.selectedBudgetId,
                accountId,
                AccountActivitySnapshot(nextActivity, accountActivityOffset, hasMore),
            )
            loadingOlderAccountActivity = false
        }
    }
    LaunchedEffect(
        isForegroundRoute,
        pendingScrollRestore,
        accountRouteWasCovered,
        accountName,
        accountActivity.size,
        routeScrollState,
    ) {
        if (pendingScrollRestore < 0) return@LaunchedEffect
        if (!isForegroundRoute) {
            accountRouteWasCovered = true
            return@LaunchedEffect
        }
        if (!accountRouteWasCovered || accountName == null || accountActivity.isEmpty()) return@LaunchedEffect

        val scrollState = routeScrollState ?: return@LaunchedEffect
        withFrameNanos { }
        if (pendingScrollRestore > 0 && scrollState.maxValue == 0) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
        }
        withFrameNanos { }
        scrollState.scrollTo(pendingScrollRestore.coerceAtMost(scrollState.maxValue))
        pendingScrollRestore = -1
        accountRouteWasCovered = false
    }
    LaunchedEffect(state.selectedBudgetId, liveAccount?.id, mobileSession.databaseGeneration) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val clearedBalanceFlow = mobileSession.observeClearedAccountBalance(state.selectedBudgetId, accountId) ?: return@LaunchedEffect
        clearedBalanceFlow.collect { clearedAccountBalanceMinor = it }
    }
    val shownAccountActivity = accountActivity
    val offBudget = liveAccount?.offBudget ?: false
    val reconciliationCandidates =
        shownAccountActivity.filter { !it.isSplitParent && it.reconciled != true && it.recordId != null }
    val description = liveAccount?.let { if (it.offBudget) "Off budget" else "On budget" }.orEmpty()
    val accountCurrency = liveAccount?.let { mobileSession.planning.accountCurrencies[it.id] }
    val balance = liveAccount?.balanceMinor?.let { state.formatAccountMoney(it, accountCurrency) } ?: "Unavailable"
    val converted = liveAccount?.let { mobileSession.approximateInBudgetCurrency(state, it.id, it.balanceMinor) }
    PageColumn {
        if (accountName == null) {
            Heading("Account details", "This account isn’t saved on this device.")
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Box(Modifier.weight(1f)) {
                    Text(description, color = tandemPalette.muted, fontSize = TandemTypography.label)
                }
                if (liveAccount != null) {
                    TandemIconButton(
                        Lucide.Pencil,
                        "Edit $accountName",
                        enabled = !state.readOnlyMode,
                    ) { navigation.open(AppRoute.ACCOUNT_OPTIONS) }
                }
            }
            AmountHero("Available balance", balance, listOfNotNull(converted, accountName, description).joinToString(" · "))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                AccountQuickAction(
                    Lucide.Plus,
                    "Add",
                    "Add transaction",
                    enabled = !offBudget && !state.readOnlyMode,
                    emphasized = true,
                    modifier = Modifier.weight(1f),
                ) { navigation.newTransaction(liveAccount?.id) }
                if (liveAccount != null) {
                    AccountQuickAction(
                        Lucide.Check,
                        "Reconcile",
                        "Reconcile account",
                        enabled =
                            !liveAccount.closed &&
                                clearedAccountBalanceMinor != null &&
                                reconciliationCandidates.isNotEmpty() &&
                                !state.readOnlyMode,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedReconciliationIds = emptySet()
                        statementBalanceText = ""
                        mobileSession.clearTransactionWriteMessage()
                        showReconcileDialog = true
                    }
                    AccountQuickAction(
                        Lucide.Receipt,
                        "Import",
                        "Import statement",
                        enabled = !liveAccount.closed && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        modifier = Modifier.weight(1f),
                    ) {
                        mobileSession.feedback.clearBudgetActionMessage()
                        pickStatementFile()
                    }
                }
            }
            if (!showStatementImportDialog) {
                budgetActionFeedbackMessage(mobileSession)
            }
            if (shownAccountActivity.isEmpty()) {
                SurfaceCard {
                    EmptyState("No recent activity", modifier = Modifier.padding(TandemSpacing.large))
                }
            } else {
                SectionCard(
                    "Recent activity",
                    action = "See all",
                    onClick = { openAccountActivity(state, navigation, liveAccount.id, liveAccount.name) },
                ) {
                    shownAccountActivity.take(6).forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) {
                            pendingScrollRestore = routeScrollState?.value ?: -1
                            openTransaction(state, navigation, item, isLive = true)
                        }
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
                    mobileSession.budgetEdits.markTransactionsReconciled(
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
            actionMessageKind = mobileSession.feedback.budgetActionMessageKind,
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
                        mobileSession.budgetEdits.importStatementTransactions(
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

private const val ACCOUNT_RECONCILIATION_PAGE_SIZE = 500

@Composable
private fun AccountQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accessibilityLabel: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val content = if (emphasized) palette.accentContent else palette.text
    Column(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(RoundedCornerShape(TandemShapes.large))
            .background(if (emphasized) palette.accent else palette.raised)
            .then(
                if (emphasized) {
                    Modifier
                } else {
                    Modifier.border(1.dp, palette.border, RoundedCornerShape(TandemShapes.large))
                },
            ).clickable(enabled = enabled, role = Role.Button, onClickLabel = accessibilityLabel, onClick = onClick)
            .semantics { contentDescription = accessibilityLabel }
            .padding(vertical = TandemSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = content)
        Text(label, color = content, fontSize = TandemTypography.label, fontWeight = FontWeight.SemiBold)
    }
}
