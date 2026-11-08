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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandemforactual.ui.components.BodyText
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.model.LiveAccountSummary
import app.tandemforactual.ui.model.recordName
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

@Composable
internal fun AccountReconcileDialog(
    state: AppState,
    accountName: String,
    clearedBalanceMinor: Long?,
    transactions: List<TransactionItem>,
    hasMoreTransactions: Boolean,
    loadingMoreTransactions: Boolean,
    onLoadMoreTransactions: () -> Unit,
    selectedIds: Set<String>,
    statementBalance: String,
    busy: Boolean,
    message: String?,
    onStatementBalanceChange: (String) -> Unit,
    onSelectedChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val statementMinor = state.parseAmountMinorUnits(statementBalance)
    val projectedClearedBalance =
        clearedBalanceMinor?.let { reconciliationClearedBalanceMinor(it, transactions, selectedIds) }
    val differenceMinor =
        if (statementMinor != null && projectedClearedBalance != null) {
            statementMinor - projectedClearedBalance
        } else {
            null
        }
    val balanceMatches = differenceMinor == 0L
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 16.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    Icon(Lucide.Check, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
                    Text(
                        "Reconcile",
                        modifier = Modifier.weight(1f),
                        color = tandemPalette.text,
                        fontSize = TandemTypography.sectionTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, enabled = !busy, onClick = onDismiss)
                }
                BodyText(accountName)
                TextFieldCard(
                    "Statement balance · ${state.formatCurrency(0)}",
                    statementBalance,
                    onStatementBalanceChange,
                    TextFieldOptions(
                        placeholder = "Enter your statement balance",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    ),
                )
                SurfaceCard {
                    Column {
                        ListRow("Cleared balance", trailing = projectedClearedBalance?.let(state::formatCurrency) ?: "Unavailable")
                        DividerLine()
                        ListRow(
                            "Difference",
                            trailing = differenceMinor?.let(state::formatCurrency) ?: "Enter a balance",
                            leading = RowLeading.Icon(Lucide.Check),
                            options =
                                ListRowOptions(
                                    trailingColor = if (balanceMatches) tandemPalette.positive else tandemPalette.warning,
                                ),
                        )
                    }
                }
                if (statementMinor != null) {
                    InlineMessage(
                        if (balanceMatches) {
                            "Cleared transactions match this statement balance."
                        } else {
                            "Adjust the selected transactions until the difference is 0."
                        },
                        if (balanceMatches) InlineMessageKind.SUCCESS else InlineMessageKind.INFO,
                    )
                }
                SectionCard("Transactions", "Select the items on this statement") {
                    transactions.forEachIndexed { index, transaction ->
                        val id = transaction.recordId ?: return@forEachIndexed
                        CheckBoxRow(
                            transaction.payee,
                            "${transaction.dateLabel} · ${state.formatCurrency(transaction.amountMinorUnits)}",
                            checked = id in selectedIds,
                            enabled = !busy,
                            onChange = { onSelectedChange(id, it) },
                        )
                        if (index < transactions.lastIndex) DividerLine()
                    }
                    if (hasMoreTransactions) {
                        if (transactions.isNotEmpty()) DividerLine()
                        TandemActionButton(
                            if (loadingMoreTransactions) "Loading…" else "Load older transactions",
                            TandemActionButtonTone.NEUTRAL,
                            Modifier.fillMaxWidth(),
                            enabled = !busy && !loadingMoreTransactions,
                            loading = loadingMoreTransactions,
                            onClick = onLoadMoreTransactions,
                        )
                    }
                }
                message?.let { InlineMessage(it, InlineMessageKind.INFO) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy, onClick = onDismiss)
                    TandemActionButton(
                        if (busy) "Working…" else "Mark reconciled",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = canFinishReconciliation(statementMinor, differenceMinor, busy),
                        loading = busy,
                        onClick = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
internal fun AccountOptionsScreen(
    state: AppState,
    liveAccounts: List<LiveAccountSummary>,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val liveAccount = if (isLiveBudget) liveAccounts.firstOrNull { it.id == state.selectedAccountId } else null
    val previewAccount = if (isLiveBudget) null else previewAccountById(state.selectedAccountId)
    val accountName = liveAccount?.name ?: previewAccount?.name
    val accountBalance =
        liveAccount?.balanceMinor?.let(::formatCurrency)
            ?: previewAccount?.balanceMinor?.let(::formatCurrency)
            ?: "Unavailable"
    val accountScope = if (liveAccount?.offBudget ?: previewAccount?.offBudget ?: false) "Off budget" else "On budget"
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
    val reorderableAccounts =
        remember(liveAccounts, liveAccount?.id) {
            liveAccount
                ?.let { selected ->
                    liveAccounts
                        .filter { it.closed == selected.closed && it.offBudget == selected.offBudget }
                        .sortedWith(compareBy({ it.sortOrder ?: Long.MAX_VALUE }, { it.name.lowercase() }))
                }.orEmpty()
        }
    val accountOrderIndex = reorderableAccounts.indexOfFirst { it.id == liveAccount?.id }
    val canClose = isLiveBudget && liveAccount != null && !liveAccount.closed && liveAccount.balanceMinor != null && !state.readOnlyMode
    PageColumn {
        if (!isLiveBudget) SampleBanner()
        if (accountName == null) {
            Heading("Account options")
            EmptyState("Account unavailable", "This account isn’t saved on this device.")
        } else {
            Heading(accountName, "Account options")
            SectionCard("Account", "Read-only details") {
                ListRow("Name", "Account name", accountName)
                DividerLine()
                ListRow("Balance", "Current balance", accountBalance)
                DividerLine()
                ListRow("Budget status", "Account scope", accountScope)
            }
            if (isLiveBudget && liveAccount != null) {
                SectionCard(
                    "Position in ${if (liveAccount.closed) {
                        "closed accounts"
                    } else if (liveAccount.offBudget) {
                        "off-budget accounts"
                    } else {
                        "on-budget accounts"
                    }}",
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                        TandemIconButton(
                            Lucide.ChevronUp,
                            "Move account up",
                            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction && accountOrderIndex > 0,
                        ) {
                            scope.launch {
                                mobileSession.moveBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    reorderableAccounts.getOrNull(accountOrderIndex - 1)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                        TandemIconButton(
                            Lucide.ChevronDown,
                            "Move account down",
                            enabled =
                                !state.readOnlyMode &&
                                    !mobileSession.isPerformingBudgetAction &&
                                    accountOrderIndex >= 0 &&
                                    accountOrderIndex < reorderableAccounts.lastIndex,
                        ) {
                            scope.launch {
                                mobileSession.moveBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    reorderableAccounts.getOrNull(accountOrderIndex + 2)?.id,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            }
                        }
                    }
                }
                SecondaryButton(
                    "Rename account",
                    enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    onClick = {
                        renameValue = liveAccount.name
                        mobileSession.clearBudgetActionMessage()
                        renameDialog = true
                    },
                )
                if (liveAccount.closed) {
                    InlineMessage("This account is closed.", InlineMessageKind.INFO)
                    SecondaryButton(
                        "Reopen account",
                        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                        onClick = {
                            scope.launch {
                                if (mobileSession.reopenBudgetAccount(
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
                    SecondaryButton(
                        "Close account",
                        enabled = canClose && !mobileSession.isPerformingBudgetAction,
                        onClick = {
                            transferAccountId = ""
                            transferCategoryId = ""
                            mobileSession.clearBudgetActionMessage()
                            confirmClose = true
                        },
                    )
                }
                mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
            }
        }
    }
    if (renameDialog && liveAccount != null) {
        AlertDialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) renameDialog = false },
            title = { Text("Rename account", color = tandemPalette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TextFieldCard("Name", renameValue, { renameValue = it }, TextFieldOptions(placeholder = "Account name"))
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank() && !mobileSession.isPerformingBudgetAction,
                    onClick = {
                        scope.launch {
                            if (mobileSession.renameBudgetAccount(
                                    state.selectedBudgetId,
                                    liveAccount.id,
                                    renameValue,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            ) {
                                renameDialog = false
                            }
                        }
                    },
                ) {
                    Text(if (mobileSession.isPerformingBudgetAction) "Saving…" else "Save", color = tandemPalette.accent)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClick = { renameDialog = false },
                ) {
                    Text("Cancel", color = tandemPalette.muted)
                }
            },
            backgroundColor = tandemPalette.surface,
        )
    }
    if (confirmClose && liveAccount != null) {
        AlertDialog(
            onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) confirmClose = false },
            title = { Text("Close ${liveAccount.name}?", color = tandemPalette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    BodyText("Actual will close this account and unlink its bank connection.")
                    if (nonZeroBalance) {
                        AccountActionDropdown(
                            title = "Move balance to",
                            selectedLabel = transferAccounts.firstOrNull { it.id == transferAccountId }?.name ?: "Choose account",
                            options = transferAccounts.map { it.name to it.id },
                            onSelect = { transferAccountId = it.orEmpty() },
                        )
                        AccountActionDropdown(
                            title = "Category",
                            selectedLabel = transferCategories.firstOrNull { it.id == transferCategoryId }?.label ?: "Uncategorized",
                            options = listOf("Uncategorized" to null) + transferCategories.map { it.label to it.id },
                            onSelect = { transferCategoryId = it.orEmpty() },
                        )
                    }
                    mobileSession.budgetActionMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled =
                        !mobileSession.isPerformingBudgetAction &&
                            (!nonZeroBalance || transferAccountId.isNotBlank()),
                    onClick = {
                        scope.launch {
                            val closed =
                                mobileSession.closeBudgetAccount(
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
                ) {
                    Text(if (mobileSession.isPerformingBudgetAction) "Closing…" else "Close account", color = tandemPalette.negative)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mobileSession.isPerformingBudgetAction,
                    onClick = { confirmClose = false },
                ) {
                    Text("Cancel", color = tandemPalette.muted)
                }
            },
            backgroundColor = tandemPalette.surface,
        )
    }
}

@Composable
internal fun AccountActionDropdown(
    title: String,
    selectedLabel: String,
    options: List<Pair<String, String?>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SurfaceCard(onClick = { expanded = true }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.medium, vertical = TandemSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall)) {
                    Text(title, color = tandemPalette.muted, fontSize = TandemTypography.caption)
                    Text(selectedLabel, color = tandemPalette.text, fontSize = TandemTypography.body, maxLines = 1)
                }
                Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (label, value) ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                ) {
                    Text(label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
