@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.api.client.StatementImportPreview
import app.tandemforactual.ui.core.format.approximateInBudgetCurrency
import app.tandemforactual.ui.core.format.formatAccountMoney
import app.tandemforactual.ui.core.importing.StatementCsvColumnMapping
import app.tandemforactual.ui.core.importing.StatementCsvLayout
import app.tandemforactual.ui.core.importing.StatementCsvMappingPreference
import app.tandemforactual.ui.core.importing.StatementDateOrder
import app.tandemforactual.ui.core.importing.StatementImportParseException
import app.tandemforactual.ui.core.importing.StatementParseResult
import app.tandemforactual.ui.core.importing.inspectStatementCsv
import app.tandemforactual.ui.core.importing.parseStatementImport
import app.tandemforactual.ui.core.importing.statementCsvMappingPreferenceFor
import app.tandemforactual.ui.core.importing.toStatementImportItems
import app.tandemforactual.ui.core.importing.updateStatementCsvMappingPreference
import app.tandemforactual.ui.core.navigation.openAccountActivity
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.platform.PickedStatementFile
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.importStatementTransactions
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.SettingRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        AccountDetailChooseBudget(navigation)
        return
    }
    val liveAccount = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountName = liveAccount?.name
    val routeScrollState = LocalRouteScrollState.current
    val scrollRestore = rememberAccountDetailScrollRestore(state)
    val activity = rememberAccountActivityState(state, mobileSession, liveAccount)
    val reconcile = rememberAccountReconcileState(state, liveAccount)
    val balanceUpdate = remember { mutableStateOf(false) }
    val statementImport = rememberStatementImportState(state, liveAccount)
    val pickStatementFile = rememberStatementFilePicker { file -> statementImport.onFilePicked(file, state, liveAccount) }
    StatementImportEffects(statementImport, state, mobileSession, liveAccount)
    AccountActivityEffects(activity, state, mobileSession, liveAccount, merchantIconMatcher)
    AccountDetailScrollRestoreEffect(scrollRestore, isForegroundRoute, accountName, activity.activity.size, routeScrollState)
    val shownAccountActivity = activity.activity
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
            AccountDetailHeader(accountName, description, balance, converted, liveAccount, !state.readOnlyMode) {
                navigation.open(AppRoute.ACCOUNT_OPTIONS)
            }
            AccountQuickActions(
                showAccountActions = liveAccount != null,
                addEnabled = !state.readOnlyMode,
                reconcileEnabled =
                    !liveAccount.closed &&
                        activity.clearedBalanceMinor != null &&
                        reconciliationCandidates.isNotEmpty() &&
                        !state.readOnlyMode,
                importEnabled = !liveAccount.closed && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                onAdd = { navigation.newTransaction(liveAccount?.id) },
                onReconcile = { reconcile.open(mobileSession) },
                onImport = {
                    mobileSession.feedback.clearBudgetActionMessage()
                    pickStatementFile()
                },
                onUpdateBalance = { balanceUpdate.value = true },
            )
            if (!statementImport.showDialog) {
                budgetActionFeedbackMessage(mobileSession)
            }
            AccountRecentActivity(shownAccountActivity, state, navigation, liveAccount) {
                scrollRestore.pending = routeScrollState?.value ?: -1
            }
            AccountNoteSection(state, mobileSession, liveAccount)
            SettingRow(
                "Account options",
                "Close account",
                onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) },
            )
        }
    }
    if (reconcile.showDialog && liveAccount != null) {
        AccountReconcileFlow(reconcile, activity, state, mobileSession, liveAccount, reconciliationCandidates, scope)
    }
    if (liveAccount != null) {
        StatementImportFlow(statementImport, state, mobileSession, liveAccount, scope)
        AccountBalanceUpdate(balanceUpdate, state, mobileSession, liveAccount, scope)
    }
}

/** The account's six most recent transactions, or an empty state. */
@Composable
private fun AccountRecentActivity(
    shownAccountActivity: List<TransactionItem>,
    state: AppState,
    navigation: AppNavigation,
    liveAccount: LiveAccountSummary,
    onOpenTransaction: () -> Unit,
) {
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
                TransactionRow(item, state.privacyModeEnabled) {
                    onOpenTransaction()
                    openTransaction(state, navigation, item, isLive = true)
                }
            }
        }
    }
}

/** Picked statement file, parse result, CSV mapping and import preview for the statement import dialog. */
internal class StatementImportState(
    pickedFile: MutableState<PickedStatementFile?>,
    parseResult: MutableState<StatementParseResult?>,
    parseError: MutableState<String?>,
    dateOrderName: MutableState<String>,
    csvLayout: MutableState<StatementCsvLayout?>,
    csvMapping: MutableState<StatementCsvColumnMapping>,
    showCsvMapping: MutableState<Boolean>,
    importPreview: MutableState<StatementImportPreview?>,
    importPreviewError: MutableState<String?>,
    importPreviewLoading: MutableState<Boolean>,
    importPreviewRetry: MutableState<Int>,
    showDialog: MutableState<Boolean>,
) {
    var pickedFile by pickedFile
    var parseResult by parseResult
    var parseError by parseError
    var dateOrderName by dateOrderName
    var csvLayout by csvLayout
    var csvMapping by csvMapping
    var showCsvMapping by showCsvMapping
    var importPreview by importPreview
    var importPreviewError by importPreviewError
    var importPreviewLoading by importPreviewLoading
    var importPreviewRetry by importPreviewRetry
    var showDialog by showDialog

    val dateOrder: StatementDateOrder
        get() = StatementDateOrder.entries.firstOrNull { it.name == dateOrderName } ?: StatementDateOrder.MONTH_DAY_YEAR

    fun onFilePicked(
        file: PickedStatementFile,
        state: AppState,
        liveAccount: LiveAccountSummary?,
    ) {
        pickedFile = file
        parseResult = null
        importPreview = null
        importPreviewError = null
        csvLayout = null
        csvMapping = StatementCsvColumnMapping()
        parseError =
            file.errorMessage ?: if (file.contents == null) "Couldn’t read this file. Choose another statement." else null
        val isCsv = file.name.substringAfterLast('.', "").equals("csv", ignoreCase = true)
        if (isCsv && file.contents != null) {
            runCatching { inspectStatementCsv(file.contents) }
                .onSuccess { layout ->
                    csvLayout = layout
                    val currentHeaders = layout.columns.map { it.name }
                    val savedPreference =
                        statementCsvMappingPreferenceFor(
                            state.statementCsvMappingsJson,
                            state.serverUrl,
                            state.selectedBudgetId,
                            liveAccount?.id.orEmpty(),
                            currentHeaders,
                        )
                    csvMapping = savedPreference?.columns ?: layout.suggestedMapping
                    if (savedPreference != null) dateOrderName = savedPreference.dateOrder.name
                    showCsvMapping =
                        if (savedPreference == null) {
                            !layout.suggestedMapping.isValidFor(layout.columns.size)
                        } else {
                            !savedPreference.columns.isValidFor(layout.columns.size)
                        }
                }.onFailure { error ->
                    parseError = (error as? StatementImportParseException)?.message ?: "Couldn’t read this CSV."
                    showCsvMapping = true
                }
        }
        showDialog = true
    }
}

@Composable
private fun rememberStatementImportState(
    state: AppState,
    liveAccount: LiveAccountSummary?,
): StatementImportState {
    val budgetId = state.selectedBudgetId
    val accountId = liveAccount?.id
    return StatementImportState(
        pickedFile = remember(budgetId, accountId) { mutableStateOf(null) },
        parseResult = remember(budgetId, accountId) { mutableStateOf(null) },
        parseError = remember(budgetId, accountId) { mutableStateOf(null) },
        dateOrderName =
            rememberSaveable(budgetId, accountId) {
                mutableStateOf(StatementDateOrder.MONTH_DAY_YEAR.name)
            },
        csvLayout = remember(budgetId, accountId) { mutableStateOf(null) },
        csvMapping = remember(budgetId, accountId) { mutableStateOf(StatementCsvColumnMapping()) },
        showCsvMapping = remember(budgetId, accountId) { mutableStateOf(false) },
        importPreview = remember(budgetId, accountId) { mutableStateOf(null) },
        importPreviewError = remember(budgetId, accountId) { mutableStateOf(null) },
        importPreviewLoading = remember(budgetId, accountId) { mutableStateOf(false) },
        importPreviewRetry = remember(budgetId, accountId) { mutableStateOf(0) },
        showDialog = remember(budgetId, accountId) { mutableStateOf(false) },
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
    val fractionDigits = currencyFractionDigits
    if (fractionDigits == null) {
        statementImport.parseError = "Sync this budget to load its currency settings, then choose the statement again."
        return
    }
    val statementDateOrder = statementImport.dateOrder
    try {
        statementImport.parseResult =
            withContext(Dispatchers.Default) {
                parseStatementImport(
                    file.name,
                    contents,
                    statementDateOrder,
                    fractionDigits,
                    numberFormat,
                    statementImport.csvMapping.takeIf { statementImport.csvLayout != null },
                )
            }
        if (statementImport.csvLayout != null && statementImport.csvMapping.isUsable && liveAccount != null) {
            state.statementCsvMappingsJson =
                updateStatementCsvMappingPreference(
                    state.statementCsvMappingsJson,
                    StatementCsvMappingPreference(
                        serverUrl = state.serverUrl,
                        budgetId = state.selectedBudgetId,
                        accountId = liveAccount.id,
                        headers =
                            statementImport.csvLayout
                                ?.columns
                                ?.map { it.name }
                                .orEmpty(),
                        dateOrder = statementDateOrder,
                        columns = statementImport.csvMapping,
                    ),
                )
        }
    } catch (error: StatementImportParseException) {
        statementImport.parseError = error.message ?: "Couldn’t read this statement. Choose a CSV, QIF, OFX, or QFX file."
    }
}

/** Statement import dialog for the account, wired to the import state and budget edits. */
@Composable
private fun StatementImportFlow(
    statementImport: StatementImportState,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveAccount: LiveAccountSummary,
    scope: CoroutineScope,
) {
    StatementImportDialog(
        visible = statementImport.showDialog,
        state = state,
        currencyDisplaySettings = mobileSession.budgetDisplaySettings,
        accountName = liveAccount.name,
        filename = statementImport.pickedFile?.name.orEmpty(),
        parseResult = statementImport.parseResult,
        parseError = statementImport.parseError,
        actionMessage = mobileSession.budgetActionMessage,
        actionMessageKind = mobileSession.feedback.budgetActionMessageKind,
        dateOrder = statementImport.dateOrder,
        csvLayout = statementImport.csvLayout,
        csvMapping = statementImport.csvMapping,
        showCsvMapping = statementImport.showCsvMapping,
        importPreview = statementImport.importPreview,
        importPreviewLoading = statementImport.importPreviewLoading,
        importPreviewError = statementImport.importPreviewError,
        busy = mobileSession.isPerformingBudgetAction,
        onDateOrderChange = {
            statementImport.dateOrderName = it.name
            statementImport.showCsvMapping = true
        },
        onCsvMappingChange = {
            statementImport.csvMapping = it
            statementImport.showCsvMapping = true
        },
        onToggleCsvMapping = { statementImport.showCsvMapping = !statementImport.showCsvMapping },
        onRetryImportPreview = { statementImport.importPreviewRetry++ },
        onDismiss = {
            if (!mobileSession.isPerformingBudgetAction) {
                statementImport.showDialog = false
            }
        },
        onDismissed = { statementImport.pickedFile = null },
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
                    statementImport.showDialog = false
                }
            }
        },
    )
}
