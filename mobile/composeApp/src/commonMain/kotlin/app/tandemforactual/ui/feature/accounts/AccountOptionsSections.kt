@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.data.planning.AccountKind
import app.tandemforactual.data.planning.CardNetwork
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.reopenBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.Heading
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Account name heading with the rename button. */
@Composable
internal fun AccountOptionsHeader(
    accountName: String,
    liveAccount: LiveAccountSummary?,
    state: AppState,
    mobileSession: TandemMobileSession,
    onRename: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Heading(accountName) }
        if (liveAccount != null) {
            TandemIconButton(
                Lucide.Pencil,
                "Rename account",
                enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                onClick = onRename,
            )
        }
    }
    liveAccount?.let { AccountIconPicker(it, state, mobileSession) }
}

/** The account's name, type, balance, budget status and currency; type and currency can be changed here. */
@Composable
internal fun AccountOptionsDetails(
    account: LiveAccountSummary,
    accountBalance: String,
    currency: String,
    onKindChange: (AccountKind) -> Unit,
    onNetworkChange: (CardNetwork?) -> Unit,
    onOpenExchangeRates: () -> Unit,
) {
    SectionCard("Account") {
        ListRow("Name", "Account name", account.name)
        DividerLine()
        AccountKindPicker(account.kind, suggested = !account.kindChosen, onKindChange)
        DividerLine()
        if (account.kind == AccountKind.CREDIT_CARD) {
            CardNetworkPicker(account.cardNetwork, onNetworkChange)
            DividerLine()
        }
        ListRow("Balance", "Current balance", accountBalance)
        DividerLine()
        ListRow("Budget status", "Account scope", if (account.offBudget) "Off budget" else "On budget")
        DividerLine()
        ListRow(
            "Currency",
            "What this account holds. Converted totals use your rates.",
            currency,
            onClick = onOpenExchangeRates,
        )
    }
}

/** Reopen button for a closed account, or close button for an open one, with action feedback. */
@Composable
internal fun AccountOpenCloseActions(
    liveAccount: LiveAccountSummary,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    scope: CoroutineScope,
    onCloseAccount: () -> Unit,
) {
    val canClose = !liveAccount.closed && liveAccount.balanceMinor != null && !state.readOnlyMode
    if (liveAccount.closed) {
        InlineMessage("This account is closed.", InlineMessageKind.INFO)
        SecondaryButton(
            "Reopen account",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
            onClick = {
                scope.launch {
                    if (mobileSession.budgetEdits.reopenBudgetAccount(
                            state.selectedBudgetId,
                            liveAccount.id,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    ) {
                        navigation.selectTab(AppRoute.ACCOUNTS)
                    }
                }
            },
        )
    } else {
        TandemActionButton(
            "Close account",
            TandemActionButtonTone.DESTRUCTIVE,
            Modifier.fillMaxWidth(),
            icon = Lucide.X,
            enabled = canClose && !mobileSession.isPerformingBudgetAction,
            onClick = onCloseAccount,
        )
    }
    budgetActionFeedbackMessage(mobileSession)
}
