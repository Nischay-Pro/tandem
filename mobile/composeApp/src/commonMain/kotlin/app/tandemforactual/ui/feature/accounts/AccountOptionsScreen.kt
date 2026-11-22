@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.feature.currency.budgetCurrencyCode
import app.tandemforactual.ui.feature.currency.formatAccountMoney

@Composable
internal fun AccountOptionsScreen(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to manage its accounts.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
        return
    }
    val liveAccount = liveAccounts.firstOrNull { it.id == state.selectedAccountId }
    val accountName = liveAccount?.name
    val accountBalance =
        liveAccount?.balanceMinor?.let { state.formatAccountMoney(it, mobileSession.planning.accountCurrencies[liveAccount.id]) }
            ?: "Unavailable"
    val accountScope = if (liveAccount?.offBudget == true) "Off budget" else "On budget"
    var confirmClose by rememberSaveable(state.selectedAccountId) { mutableStateOf(false) }
    var renameDialog by rememberSaveable(state.selectedAccountId) { mutableStateOf(false) }
    var renameValue by rememberSaveable(state.selectedAccountId) { mutableStateOf(liveAccount?.name.orEmpty()) }
    var transferAccountId by rememberSaveable(state.selectedAccountId) { mutableStateOf("") }
    var transferCategoryId by rememberSaveable(state.selectedAccountId) { mutableStateOf("") }
    PageColumn {
        if (accountName == null) {
            EmptyState("Account unavailable", "This account isn’t saved on this device.")
        } else {
            AccountOptionsHeader(accountName, liveAccount, state, mobileSession) {
                renameValue = liveAccount.name
                mobileSession.feedback.clearBudgetActionMessage()
                renameDialog = true
            }
            AccountOptionsDetails(
                accountName,
                accountBalance,
                accountScope,
                liveAccount?.let { mobileSession.planning.accountCurrencies[it.id] } ?: state.budgetCurrencyCode,
            ) { navigation.open(AppRoute.EXCHANGE_RATES) }
            if (liveAccount != null) {
                AccountOpenCloseActions(liveAccount, state, mobileSession, navigation, scope) {
                    transferAccountId = ""
                    transferCategoryId = ""
                    mobileSession.feedback.clearBudgetActionMessage()
                    confirmClose = true
                }
            }
        }
    }
    RenameAccountSheet(
        visible = renameDialog && liveAccount != null,
        account = liveAccount,
        value = renameValue,
        onValueChange = { renameValue = it },
        onClose = { renameDialog = false },
        state = state,
        mobileSession = mobileSession,
    )
    if (confirmClose && liveAccount != null) {
        CloseAccountDialog(
            liveAccount = liveAccount,
            liveAccounts = liveAccounts,
            transferAccountId = transferAccountId,
            onTransferAccountIdChange = { transferAccountId = it },
            transferCategoryId = transferCategoryId,
            onTransferCategoryIdChange = { transferCategoryId = it },
            onClose = { confirmClose = false },
            state = state,
            mobileSession = mobileSession,
            navigation = navigation,
            scope = scope,
        )
    }
}
