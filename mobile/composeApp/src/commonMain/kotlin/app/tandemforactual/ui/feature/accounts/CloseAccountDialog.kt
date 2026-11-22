@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.closeBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Confirmation dialog for closing an account, with a transfer destination when it still holds a balance. */
@Composable
internal fun CloseAccountDialog(
    liveAccount: LiveAccountSummary,
    liveAccounts: List<LiveAccountSummary>,
    transferAccountId: String,
    onTransferAccountIdChange: (String) -> Unit,
    transferCategoryId: String,
    onTransferCategoryIdChange: (String) -> Unit,
    onClose: () -> Unit,
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    scope: CoroutineScope,
) {
    val nonZeroBalance = liveAccount.balanceMinor?.let { it != 0L } ?: false
    val transferAccounts =
        remember(liveAccounts, liveAccount.id) {
            liveAccounts.filter { it.id != liveAccount.id && !it.closed }
        }
    val transferCategories =
        remember(mobileSession.categoryRecords) {
            mobileSession.categoryRecords
                .mapNotNull { record -> record.recordName()?.let { SelectorItem(record.recordId, it) } }
                .distinctBy(SelectorItem::id)
        }
    DestructiveConfirmationDialog(
        title = "Close ${liveAccount.name}?",
        message = "Actual will close this account and unlink its bank connection.",
        confirmLabel = if (mobileSession.isPerformingBudgetAction) "Closing…" else "Close account",
        dismissLabel = "Cancel",
        enabled =
            !mobileSession.isPerformingBudgetAction &&
                (!nonZeroBalance || transferAccountId.isNotBlank()),
        dismissEnabled = !mobileSession.isPerformingBudgetAction,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                if (nonZeroBalance) {
                    InlineMessage(
                        "Actual requires a non-zero balance to move to another open account before it can close this account. Choose any destination account; the budget category is optional.",
                        InlineMessageKind.INFO,
                    )
                    AccountActionDropdown(
                        title = "Transfer balance to",
                        selectedLabel = transferAccounts.firstOrNull { it.id == transferAccountId }?.name ?: "Choose account",
                        options = transferAccounts.map { it.name to it.id },
                        onSelect = { onTransferAccountIdChange(it.orEmpty()) },
                    )
                    if (transferAccountId.isNotBlank()) {
                        AccountActionDropdown(
                            title = "Budget category (optional)",
                            selectedLabel = transferCategories.firstOrNull { it.id == transferCategoryId }?.label ?: "Uncategorized",
                            options = listOf("Uncategorized" to null) + transferCategories.map { it.label to it.id },
                            onSelect = { onTransferCategoryIdChange(it.orEmpty()) },
                        )
                    }
                }
                budgetActionFeedbackMessage(mobileSession)
            }
        },
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) onClose() },
        onConfirm = {
            scope.launch {
                val closed =
                    mobileSession.budgetEdits.closeBudgetAccount(
                        state.selectedBudgetId,
                        liveAccount.id,
                        transferAccountId.takeIf(String::isNotBlank),
                        transferCategoryId.takeIf(String::isNotBlank),
                        state.encryptedPassword.takeIf { it.isNotBlank() },
                    )
                if (closed) {
                    onClose()
                    navigation.selectTab(AppRoute.ACCOUNTS)
                }
            }
        },
    )
}
