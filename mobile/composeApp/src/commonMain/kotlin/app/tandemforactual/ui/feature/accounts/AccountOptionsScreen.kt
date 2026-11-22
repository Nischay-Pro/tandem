@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.closeBudgetAccount
import app.tandemforactual.ui.core.session.budget.renameBudgetAccount
import app.tandemforactual.ui.core.session.budget.reopenBudgetAccount
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveAccountSummary
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.Heading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.currency.budgetCurrencyCode
import app.tandemforactual.ui.feature.currency.formatAccountMoney
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

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
    val nonZeroBalance = liveAccount?.balanceMinor?.let { it != 0L } ?: false
    val transferAccounts =
        remember(liveAccounts, liveAccount?.id) {
            liveAccounts.filter { it.id != liveAccount?.id && !it.closed }
        }
    val transferCategories =
        remember(mobileSession.categoryRecords) {
            mobileSession.categoryRecords
                .mapNotNull { record -> record.recordName()?.let { SelectorItem(record.recordId, it) } }
                .distinctBy(SelectorItem::id)
        }
    val canClose = liveAccount != null && !liveAccount.closed && liveAccount.balanceMinor != null && !state.readOnlyMode
    PageColumn {
        if (accountName == null) {
            EmptyState("Account unavailable", "This account isn’t saved on this device.")
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Heading(accountName) }
                if (liveAccount != null) {
                    TandemIconButton(
                        Lucide.Pencil,
                        "Rename account",
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    ) {
                        renameValue = liveAccount.name
                        mobileSession.feedback.clearBudgetActionMessage()
                        renameDialog = true
                    }
                }
            }
            SectionCard("Account", "Read-only details") {
                ListRow("Name", "Account name", accountName)
                DividerLine()
                ListRow("Balance", "Current balance", accountBalance)
                DividerLine()
                ListRow("Budget status", "Account scope", accountScope)
                DividerLine()
                ListRow(
                    "Currency",
                    "What this account holds. Converted totals use your rates.",
                    liveAccount?.let { mobileSession.planning.accountCurrencies[it.id] } ?: state.budgetCurrencyCode,
                    onClick = { navigation.open(AppRoute.EXCHANGE_RATES) },
                )
            }
            if (liveAccount != null) {
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
                    ) {
                        transferAccountId = ""
                        transferCategoryId = ""
                        mobileSession.feedback.clearBudgetActionMessage()
                        confirmClose = true
                    }
                }
                budgetActionFeedbackMessage(mobileSession)
            }
        }
    }
    TandemTaskSheet(
        visible = renameDialog && liveAccount != null,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) renameDialog = false },
        onDismissed = {},
    ) {
        val account = liveAccount
        if (account != null) {
            SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large)) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Rename account",
                            modifier = Modifier.weight(1f),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.sectionTitle,
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                        )
                        TandemIconButton(
                            Lucide.X,
                            "Close",
                            sizeDp = 40,
                            iconSizeDp = 18,
                            enabled = !mobileSession.isPerformingBudgetAction,
                            onClick = { renameDialog = false },
                        )
                    }
                    TextFieldCard("Name", renameValue, { renameValue = it }, TextFieldOptions(placeholder = "Account name"))
                    budgetActionFeedbackMessage(mobileSession)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemActionButton(
                            "Cancel",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.weight(1f),
                            enabled = !mobileSession.isPerformingBudgetAction,
                            onClick = { renameDialog = false },
                        )
                        TandemActionButton(
                            if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save",
                            TandemActionButtonTone.PRIMARY,
                            Modifier.weight(1f),
                            icon = Lucide.Check,
                            enabled = renameValue.isNotBlank() && !mobileSession.isPerformingBudgetAction,
                            loading = mobileSession.isPerformingBudgetAction,
                        ) {
                            scope.launch {
                                if (mobileSession.budgetEdits.renameBudgetAccount(
                                        state.selectedBudgetId,
                                        account.id,
                                        renameValue,
                                        state.encryptedPassword.takeIf { it.isNotBlank() },
                                    )
                                ) {
                                    renameDialog = false
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClose && liveAccount != null) {
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
                            onSelect = { transferAccountId = it.orEmpty() },
                        )
                        if (transferAccountId.isNotBlank()) {
                            AccountActionDropdown(
                                title = "Budget category (optional)",
                                selectedLabel = transferCategories.firstOrNull { it.id == transferCategoryId }?.label ?: "Uncategorized",
                                options = listOf("Uncategorized" to null) + transferCategories.map { it.label to it.id },
                                onSelect = { transferCategoryId = it.orEmpty() },
                            )
                        }
                    }
                    budgetActionFeedbackMessage(mobileSession)
                }
            },
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) confirmClose = false },
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
                        confirmClose = false
                        navigation.selectTab(AppRoute.ACCOUNTS)
                    }
                }
            },
        )
    }
}
