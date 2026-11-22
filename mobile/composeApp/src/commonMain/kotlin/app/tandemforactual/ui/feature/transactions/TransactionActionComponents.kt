@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.TandemLoadingIndicator
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SwipeToDeleteBox
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.Trash2

@Composable
internal fun TransactionRow(
    transaction: TransactionItem,
    activityStyle: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    showDateHeader: Boolean = true,
    showAccount: Boolean = false,
    showDivider: Boolean = false,
    syncStatus: String? = null,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onSwipeDelete: (() -> Unit)? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val amountText = formatSignedCurrency(transaction.amountMinorUnits, transaction.displaySettings)
    Column(modifier) {
        if (showDateHeader) {
            Text(
                transaction.dateLabel,
                modifier = Modifier.padding(start = TandemSpacing.large, top = TandemSpacing.medium),
                color = palette.muted,
                fontSize = TandemTypography.micro,
            )
        }
        val row: @Composable () -> Unit = {
            TransactionRowContent(
                transaction,
                amountText,
                activityStyle,
                selectionMode,
                selected,
                showAccount,
                syncStatus,
                highlighted,
                onLongClick,
                onClick,
            )
        }
        if (onSwipeDelete != null) SwipeToDeleteBox(onSwipeDelete) { row() } else row()
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TandemSpacing.large)
                    .height(1.dp)
                    .background(palette.border.copy(alpha = 0.35f)),
            )
        }
    }
}

/** The highlighted list row for one transaction, without its date header and divider. */
@Composable
private fun TransactionRowContent(
    transaction: TransactionItem,
    amountText: String,
    activityStyle: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    showAccount: Boolean,
    syncStatus: String?,
    highlighted: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val secondaryLabel = activityTransactionSubtitle(transaction, showAccount)
    Box(
        Modifier
            .padding(horizontal = if (highlighted) TandemSpacing.extraSmall else 0.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .background(if (highlighted) palette.accentSurface else Color.Transparent),
    ) {
        ListRow(
            transaction.payee,
            secondaryLabel,
            amountText.takeUnless { activityStyle || syncStatus != null },
            leading =
                when {
                    selectionMode -> RowLeading.Icon(if (selected) Lucide.Check else Lucide.Square)
                    activityStyle -> activityTransactionLeading(transaction)
                    else -> RowLeading.Merchant(transaction.merchantIcon, fallback = transaction.categoryIcon ?: Lucide.Receipt)
                },
            options =
                ListRowOptions(
                    trailingColor = if (transaction.amountMinorUnits > 0) palette.positive else palette.text,
                    subtitleMaxLines = 1,
                ),
            onClick = onClick,
            onLongClick = onLongClick,
            trailingContent =
                if (activityStyle || syncStatus != null) {
                    { TransactionRowTrailing(transaction, amountText, syncStatus) }
                } else {
                    null
                },
        )
    }
}

/** The amount and sync chip shown at the end of an activity-style transaction row. */
@Composable
private fun TransactionRowTrailing(
    transaction: TransactionItem,
    amountText: String,
    syncStatus: String?,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        ActivityTransactionAmount(
            amountText,
            amountMinorUnits = transaction.amountMinorUnits,
            fractionDigits =
                transaction.displaySettings
                    ?.takeUnless { it.hideFraction }
                    ?.currencyFractionDigits ?: 0,
        )
        syncStatus?.let { TransactionSyncChip(it) }
    }
}

internal fun activityTransactionSubtitle(
    transaction: TransactionItem,
    showAccount: Boolean,
): String {
    val note = transaction.notes.trim()
    return listOfNotNull(
        note.takeIf(String::isNotEmpty),
        transaction.category.takeIf(String::isNotBlank),
        transaction.account.takeIf { showAccount && it.isNotBlank() },
    ).joinToString(" · ")
}

internal fun activityTransactionLeading(transaction: TransactionItem): RowLeading {
    val merchantIcon = transaction.merchantIcon
    if (merchantIcon != null) {
        return RowLeading.Merchant(merchantIcon, fallback = transaction.categoryIcon ?: Lucide.Receipt)
    }
    val payeeInitial =
        transaction.payee
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?"
    return RowLeading.Letter(transaction.icon.ifBlank { payeeInitial })
}

internal fun activityAmountAccessibilityDescription(
    value: String,
    amountMinorUnits: Long,
): String =
    when {
        amountMinorUnits > 0 -> "Income, $value"
        amountMinorUnits < 0 -> "Spending, $value"
        else -> "No money moved, $value"
    }

internal fun activityAmountFractionRange(
    value: String,
    fractionDigits: Int,
): IntRange? = amountAnnotatedString(value, fractionDigits).spanStyles.lastOrNull()?.let { it.start until it.end }

internal fun activityAmountAnnotatedString(
    value: String,
    fractionDigits: Int,
): AnnotatedString = amountAnnotatedString(value, fractionDigits)

@Composable
private fun ActivityTransactionAmount(
    value: String,
    amountMinorUnits: Long,
    fractionDigits: Int,
) {
    Text(
        amountAnnotatedString(value, fractionDigits),
        modifier =
            Modifier.semantics {
                contentDescription = activityAmountAccessibilityDescription(value, amountMinorUnits)
            },
        color = if (amountMinorUnits > 0) tandemPalette.positive else tandemPalette.text,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
    )
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

internal fun openTransaction(
    state: AppState,
    navigation: AppNavigation,
    transaction: TransactionItem,
    isLive: Boolean,
    navigate: Boolean = true,
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
    state.transactionType =
        when {
            transaction.isTransfer -> "Transfer"
            transaction.amountMinorUnits < 0 -> "Spending"
            else -> "Income"
        }
    state.selectedTransactionAmountMinor = transaction.amountMinorUnits
    state.selectedTransactionNotes = transaction.notes
    state.selectedTransactionRecordId = transaction.recordId.orEmpty()
    state.selectedTransactionIsLive = isLive
    state.selectedTransactionIsTransfer = transaction.isTransfer
    if (navigate) navigation.open(AppRoute.TRANSACTION_DETAIL)
}

internal suspend fun deleteTransactionSelection(
    mobileSession: TandemMobileSession,
    budgetId: String,
    transactionIds: Set<String>,
): Boolean = mobileSession.transactions.deleteTransactionsWithUndo(budgetId, transactionIds)

/** A past transaction copied into a new one dated today. */
internal data class AddAgainDraft(
    val accountId: String,
    val payeeId: String,
    val payeeName: String,
    val categoryId: String,
    val categoryName: String,
    val amountMinor: Long,
    val notes: String,
)

internal fun AppState.addAgainDraft(): AddAgainDraft? {
    val amount = selectedTransactionAmountMinor ?: return null
    if (selectedTransactionIsTransfer || selectedTransactionRecordId.isBlank()) return null
    return AddAgainDraft(
        accountId = selectedTransactionAccountId,
        payeeId = selectedTransactionPayeeId,
        payeeName = selectedTransactionPayeeName.ifBlank { transactionPayee },
        categoryId = selectedTransactionCategoryId,
        categoryName = selectedTransactionCategoryName.ifBlank { transactionCategory },
        amountMinor = amount,
        notes = selectedTransactionNotes,
    )
}

internal fun AppState.applyAddAgain(draft: AddAgainDraft) {
    transactionAmount = formatAmount(kotlin.math.abs(draft.amountMinor))
    transactionType = if (draft.amountMinor < 0) "Spending" else "Income"
    newTransactionPayeeId = draft.payeeId
    transactionPayee = draft.payeeName
    newTransactionCategoryId = draft.categoryId
    transactionCategory = draft.categoryName
    transactionNotes = draft.notes
}
