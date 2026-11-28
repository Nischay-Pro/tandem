@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.format.approximateInBudgetCurrency
import app.tandemforactual.ui.core.format.formatAccountMoney
import app.tandemforactual.ui.core.navigation.openAccountActivity
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.platform.rememberStatementFilePicker
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.LocalRouteScrollState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.ScheduledOccurrencesCard
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.core.ui.rememberRegisterOccurrences
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.SettingRow
import app.tandemforactual.ui.design.theme.TandemSpacing

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
    val review = rememberImportReview(state, mobileSession, liveAccount?.id)
    val shownAccountActivity = activity.activity.filter { it.recordId !in review.waiting }
    val reconciliationCandidates = shownAccountActivity.filter { !it.isSplitParent && it.reconciled != true && it.recordId != null }
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
            if (!statementImport.showDialog) budgetActionFeedbackMessage(mobileSession)
            ScheduledOccurrencesCard(state, navigation, rememberRegisterOccurrences(mobileSession, liveAccount.id))
            ImportApprovalCard(review, activity.activity.filter { it.recordId in review.waiting }, state, navigation, mobileSession)
            AccountRecentActivity(shownAccountActivity, state, navigation, liveAccount) {
                scrollRestore.pending = routeScrollState?.value ?: -1
            }
            AccountDetailFooter(state, mobileSession, navigation, liveAccount)
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

/** Card statements, a loan's payoff, the account's note and the way to its options. */
@Composable
private fun AccountDetailFooter(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveAccount: LiveAccountSummary,
) {
    CardStatementsCard(state, mobileSession, liveAccount)
    LoanCard(state, mobileSession, liveAccount)
    AccountNoteSection(state, mobileSession, liveAccount)
    SettingRow("Account options", "Close account", onClick = { navigation.open(AppRoute.ACCOUNT_OPTIONS) })
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
