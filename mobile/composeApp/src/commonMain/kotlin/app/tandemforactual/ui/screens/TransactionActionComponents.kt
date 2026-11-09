@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.DestructiveConfirmationDialog
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.ListRowOptions
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemLoadingIndicator
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2

@Composable
internal fun TransactionRow(
    transaction: TransactionItem,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    showDateHeader: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    Column {
        if (showDateHeader) {
            Text(
                transaction.dateLabel,
                modifier = Modifier.padding(start = TandemSpacing.large, top = TandemSpacing.medium),
                color = palette.muted,
                fontSize = TandemTypography.micro,
            )
        }
        ListRow(
            transaction.payee,
            transaction.category,
            formatSignedCurrency(transaction.amountMinorUnits, transaction.displaySettings),
            leading =
                if (selectionMode) {
                    RowLeading.Icon(if (selected) Lucide.Check else Lucide.Square)
                } else {
                    RowLeading.Merchant(transaction.merchantIcon, fallback = transaction.categoryIcon ?: Lucide.Receipt)
                },
            options = ListRowOptions(trailingColor = if (transaction.amountMinorUnits > 0) palette.positive else palette.text),
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
internal fun TransactionSelectionFloatingBar(
    allSelected: Boolean,
    canDelete: Boolean,
    isWorking: Boolean = false,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfaceCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(TandemSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
        ) {
            val label = if (allSelected) "Deselect all" else "Select all"
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .clip(RoundedCornerShape(TandemShapes.medium))
                    .background(tandemPalette.accentSurface)
                    .clickable(enabled = !isWorking, role = Role.Button, onClickLabel = label, onClick = onToggleAll)
                    .padding(horizontal = TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isWorking) {
                    TandemLoadingIndicator(Modifier.size(18.dp), tandemPalette.accent, 2.dp)
                } else {
                    Icon(
                        if (allSelected) Lucide.Check else Lucide.Square,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = tandemPalette.accent,
                    )
                }
                Text(
                    label,
                    modifier = Modifier.padding(start = TandemSpacing.small),
                    color = tandemPalette.accent,
                    fontSize = TandemTypography.label,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            TandemIconButton(
                Lucide.Trash2,
                "Delete selected transactions",
                enabled = canDelete && !isWorking,
                tint = tandemPalette.negative,
                containerColor = tandemPalette.negative.copy(alpha = 0.12f),
                onClick = onDelete,
            )
        }
    }
}

@Composable
internal fun TransactionBulkDeleteDialog(
    selectedCount: Int,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = if (selectedCount == 1) "Delete this transaction?" else "Delete $selectedCount transactions?",
        message = "Deleted transactions leave this device first and sync when the server is available.",
        confirmLabel = if (isDeleting) "Deleting…" else "Delete",
        dismissLabel = "Cancel",
        enabled = !isDeleting,
        onDismissRequest = onDismiss,
        onConfirm = onConfirm,
    )
}

internal fun openTransaction(
    state: AppState,
    navigation: AppNavigation,
    transaction: TransactionItem,
    isLive: Boolean,
) {
    state.transactionPayee = transaction.payee
    state.transactionCategory = transaction.category
    state.transactionAccount = transaction.account
    state.transactionDate = transaction.dateIso.ifBlank { transaction.dateLabel }
    state.newTransactionAccountId = transaction.accountId.orEmpty()
    state.newTransactionPayeeId = transaction.payeeId.orEmpty()
    state.newTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionAccountId = transaction.accountId.orEmpty()
    state.selectedTransactionPayeeId = transaction.payeeId.orEmpty()
    state.selectedTransactionCategoryId = transaction.categoryId.orEmpty()
    state.selectedTransactionDate = state.transactionDate
    state.selectedTransactionAccountName = transaction.account
    state.selectedTransactionPayeeName = transaction.payee
    state.selectedTransactionCategoryName = transaction.category
    state.transactionNotes = transaction.notes
    state.transactionAmount = formatAmount(kotlin.math.abs(transaction.amountMinorUnits), state.budgetDisplaySettings)
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
    state.selectedTransactionNotes = transaction.notes
    state.selectedTransactionRecordId = transaction.recordId.orEmpty()
    state.selectedTransactionIsLive = isLive
    navigation.open(AppRoute.TRANSACTION_DETAIL)
}

internal suspend fun deleteTransactionSelection(
    mobileSession: TandemMobileSession,
    budgetId: String,
    transactionIds: Set<String>,
): Boolean {
    for (transactionId in transactionIds) {
        if (!mobileSession.deleteTransaction(budgetId, transactionId)) return false
    }
    return true
}
