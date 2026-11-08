@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.model.LiveAccountSummary
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val liveAccount = if (isLiveBudget) liveAccounts.firstOrNull { it.id == state.selectedAccountId } else null
    val previewAccount = if (isLiveBudget) null else previewAccountById(state.selectedAccountId)
    val accountName = liveAccount?.name ?: previewAccount?.name
    var accountActivity by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptyList<TransactionItem>()) }
    var clearedAccountBalanceMinor by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf<Long?>(null) }
    var showReconcileDialog by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(false) }
    var selectedReconciliationIds by remember(state.selectedBudgetId, liveAccount?.id) { mutableStateOf(emptySet<String>()) }
    var statementBalanceText by rememberSaveable(state.selectedBudgetId, liveAccount?.id) { mutableStateOf("") }
    LaunchedEffect(
        state.selectedBudgetId,
        liveAccount?.id,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
        merchantIconMatcher,
    ) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val accountActivityFlow =
            mobileSession.observeAccountActivityRecords(state.selectedBudgetId, accountId, limit = 500)
                ?: return@LaunchedEffect
        accountActivityFlow.collect { records ->
            accountActivity =
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
        }
    }
    LaunchedEffect(state.selectedBudgetId, liveAccount?.id) {
        val accountId = liveAccount?.id ?: return@LaunchedEffect
        val clearedBalanceFlow = mobileSession.observeClearedAccountBalance(state.selectedBudgetId, accountId) ?: return@LaunchedEffect
        clearedBalanceFlow.collect { clearedAccountBalanceMinor = it }
    }
    val shownAccountActivity =
        when {
            liveAccount != null -> accountActivity
            previewAccount != null -> previewTransactions.filter { it.account == previewAccount.name }
            else -> emptyList()
        }
    val offBudget = liveAccount?.offBudget ?: previewAccount?.offBudget ?: false
    val reconciliationCandidates =
        shownAccountActivity.filter { !it.isSplitParent && it.reconciled != true && it.recordId != null }
    val description =
        when {
            liveAccount != null -> if (liveAccount.offBudget) "Off budget" else "On budget"
            previewAccount != null && previewAccount.offBudget -> "Off budget"
            previewAccount != null -> "Updated ${formatShortDate(SampleDate(2026, 5, 14))}"
            else -> ""
        }
    val balance =
        liveAccount?.balanceMinor?.let(state::formatCurrency)
            ?: previewAccount?.balanceMinor?.let(state::formatCurrency)
            ?: "Unavailable"
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (accountName == null) {
            Heading("Account details", "This account isn’t saved on this device.")
        } else {
            AmountHero("Available balance", balance, "$accountName · $description")
            if (isLiveBudget && liveAccount != null) {
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
                    selectedReconciliationIds = reconciliationCandidates.filter { it.cleared == true }.mapNotNull { it.recordId }.toSet()
                    statementBalanceText = clearedAccountBalanceMinor?.let(state::formatAmount).orEmpty()
                    mobileSession.clearTransactionWriteMessage()
                    showReconcileDialog = true
                }
            }
            PrimaryButton(
                "Add transaction",
                enabled = !offBudget && !state.readOnlyMode,
            ) {
                if (isLiveBudget) {
                    navigation.newTransaction(liveAccount!!.id)
                } else {
                    navigation.open(AppRoute.NEW_TRANSACTION)
                }
            }
            if (shownAccountActivity.isEmpty()) {
                SurfaceCard {
                    EmptyState("No recent activity", modifier = Modifier.padding(TandemSpacing.large))
                }
            } else {
                SectionCard("Recent activity", action = "See all", onClick = { navigation.selectTab(AppRoute.ACTIVITY) }) {
                    shownAccountActivity.take(6).forEachIndexed { index, item ->
                        if (index > 0) DividerLine()
                        TransactionRow(item) { openTransaction(state, navigation, item, isLive = isLiveBudget) }
                    }
                }
            }
            SettingRow(
                "Account options",
                if (isLiveBudget) "Close account" else "$accountName settings",
                onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) },
            )
        }
    }
    if (showReconcileDialog && liveAccount != null) {
        val didReconcile = {
            scope.launch {
                if (
                    mobileSession.markTransactionsReconciled(
                        state.selectedBudgetId,
                        liveAccount.id,
                        reconciliationCandidates.mapNotNull { it.recordId }.toSet(),
                        selectedReconciliationIds,
                    )
                ) {
                    showReconcileDialog = false
                }
            }
        }
        AccountReconcileDialog(
            state = state,
            accountName = liveAccount.name,
            clearedBalanceMinor = clearedAccountBalanceMinor,
            transactions = reconciliationCandidates,
            activityTruncated = accountActivity.size >= 500,
            selectedIds = selectedReconciliationIds,
            statementBalance = statementBalanceText,
            busy = mobileSession.isSavingTransaction,
            message = mobileSession.transactionWriteMessage,
            onStatementBalanceChange = { statementBalanceText = it },
            onSelectedChange = { id, selected ->
                selectedReconciliationIds = if (selected) selectedReconciliationIds + id else selectedReconciliationIds - id
            },
            onDismiss = { if (!mobileSession.isSavingTransaction) showReconcileDialog = false },
            onConfirm = { didReconcile() },
        )
    }
}
