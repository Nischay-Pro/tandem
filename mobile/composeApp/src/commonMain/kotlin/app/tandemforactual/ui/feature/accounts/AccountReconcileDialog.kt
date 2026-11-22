@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.parseAmountMinorUnits
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.parseAmountMinorUnits
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.BodyText
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.CheckBoxRow
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

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
    onConfirm: (onSuccess: () -> Unit) -> Unit,
) {
    val currencySettings = state.budgetDisplaySettings
    val currencySymbol = currencySettings?.currencySymbol?.takeIf { it.isNotBlank() }
    val currencySymbolIsAfter = currencySettings?.currencySymbolPosition == "after"
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
    var sheetVisible by remember { mutableStateOf(true) }
    val requestDismiss = { if (!busy) sheetVisible = false }
    TandemTaskSheet(
        visible = sheetVisible,
        onDismissRequest = requestDismiss,
        onDismissed = onDismiss,
    ) {
        SurfaceCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(TandemSpacing.large),
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
                            fontFamily = tandemAlloFamily(),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TandemIconButton(Lucide.X, "Close", sizeDp = 40, iconSizeDp = 18, enabled = !busy, onClick = requestDismiss)
                    }
                    BodyText(accountName)
                    TextFieldCard(
                        "Statement balance",
                        statementBalance,
                        onStatementBalanceChange,
                        TextFieldOptions(
                            placeholder = state.formatCurrency(0),
                            currencySymbol = currencySymbol,
                            currencySymbolAfter = currencySymbolIsAfter,
                            currencySymbolSpace = currencySettings?.currencySpaceBetweenAmountAndSymbol == true,
                            amountFractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        ),
                    )
                    if (statementMinor == null) {
                        InlineMessage("Enter your statement balance", InlineMessageKind.INFO)
                    }
                    SurfaceCard {
                        Column {
                            ListRow("Cleared balance", trailing = projectedClearedBalance?.let(state::formatCurrency) ?: "Unavailable")
                            DividerLine()
                            ListRow(
                                "Difference",
                                trailing = differenceMinor?.let(state::formatCurrency) ?: "—",
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
                }
                message?.let { InlineMessage(it, InlineMessageKind.INFO, Modifier.padding(horizontal = TandemSpacing.large)) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        Modifier.weight(1f),
                        enabled = !busy,
                        onClick = requestDismiss,
                    )
                    TandemActionButton(
                        if (busy) "Working…" else "Mark reconciled",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = canFinishReconciliation(statementMinor, differenceMinor, busy),
                        loading = busy,
                        onClick = { onConfirm(requestDismiss) },
                    )
                }
            }
        }
    }
}
