@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.money.amountAnnotatedString
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowContextMenu
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.RowMenuAction
import app.tandemforactual.ui.design.rows.SwipeToDeleteBox
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheck
import com.composables.icons.lucide.Trash2

@Composable
internal fun TransactionRow(
    transaction: TransactionItem,
    privacyModeEnabled: Boolean,
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
    selectionMenuActions: List<RowMenuAction> = emptyList(),
    runningBalance: String? = null,
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
                privacyModeEnabled,
                activityStyle,
                selectionMode,
                selected,
                showAccount,
                TrailingExtras(syncStatus, runningBalance),
                highlighted,
                onLongClick,
                onClick,
            )
        }
        RowContextMenu(transactionRowMenuActions(selectionMode, onClick, onLongClick, onSwipeDelete, selectionMenuActions)) {
            if (onSwipeDelete != null) SwipeToDeleteBox(onSwipeDelete) { row() } else row()
        }
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

/**
 * Open, select and delete: what a tap, long-press and swipe do, for a right-click, plus [selectionActions] for the
 * whole selection while selecting. Rows with only a tap get no menu.
 */
internal fun transactionRowMenuActions(
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onSelect: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    selectionActions: List<RowMenuAction> = emptyList(),
): List<RowMenuAction> {
    if (onSelect == null && onDelete == null) return emptyList()
    return listOfNotNull(
        RowMenuAction("Open", Lucide.ExternalLink, onClick = onOpen),
        onSelect?.takeUnless { selectionMode }?.let { RowMenuAction("Select", Lucide.SquareCheck, onClick = it) },
        onDelete?.let { RowMenuAction("Delete", Lucide.Trash2, destructive = true, onClick = it) },
    ) + selectionActions.takeIf { selectionMode }.orEmpty()
}

/** The highlighted list row for one transaction, without its date header and divider. */
@Composable
private fun TransactionRowContent(
    transaction: TransactionItem,
    amountText: String,
    privacyModeEnabled: Boolean,
    activityStyle: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    showAccount: Boolean,
    extras: TrailingExtras,
    highlighted: Boolean,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val secondaryLabel = activityTransactionSubtitle(transaction, showAccount)
    val customTrailing = activityStyle || extras.syncStatus != null || extras.runningBalance != null || privacyModeEnabled
    Box(
        Modifier
            .padding(horizontal = if (highlighted) TandemSpacing.extraSmall else 0.dp)
            .clip(RoundedCornerShape(TandemShapes.control))
            .background(if (highlighted) palette.accentSurface else Color.Transparent),
    ) {
        ListRow(
            maskedName(transaction.payee),
            secondaryLabel?.let { maskedName(it) },
            amountText.takeUnless { customTrailing },
            leading =
                when {
                    selectionMode -> RowLeading.Icon(if (selected) Lucide.Check else Lucide.Square)
                    activityStyle -> activityTransactionLeading(transaction)
                    transaction.isTransfer -> RowLeading.Account(maskedName(transaction.payee), fallback = Lucide.ArrowLeftRight)
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
                if (customTrailing) {
                    { TransactionRowTrailing(transaction, amountText, privacyModeEnabled, extras) }
                } else {
                    null
                },
        )
    }
}

/** What can sit under a row's amount: its sync state and the account's balance after it. */
private data class TrailingExtras(
    val syncStatus: String?,
    val runningBalance: String?,
)

/** The amount, the balance after it and the sync chip shown at the end of an activity-style transaction row. */
@Composable
private fun TransactionRowTrailing(
    transaction: TransactionItem,
    amountText: String,
    privacyModeEnabled: Boolean,
    extras: TrailingExtras,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
    ) {
        ActivityTransactionAmount(
            amountText,
            amountMinorUnits = transaction.amountMinorUnits,
            privacyModeEnabled = privacyModeEnabled,
            fractionDigits =
                transaction.displaySettings
                    ?.takeUnless { it.hideFraction }
                    ?.currencyFractionDigits ?: 0,
        )
        extras.runningBalance?.takeUnless { privacyModeEnabled }?.let {
            Text(it, color = tandemPalette.muted, fontSize = TandemTypography.micro)
        }
        extras.syncStatus?.let { TransactionSyncChip(it) }
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
    if (transaction.isTransfer) return RowLeading.Account(transaction.payee, fallback = Lucide.ArrowLeftRight)
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
    privacyModeEnabled: Boolean,
    fractionDigits: Int,
) {
    val color = if (amountMinorUnits > 0) tandemPalette.positive else tandemPalette.text
    if (privacyModeEnabled) {
        PrivacyAmountText(
            value,
            privacyModeEnabled = true,
            color = color,
            fontSize = TandemTypography.compactBody,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fractionDigits = fractionDigits,
        )
        return
    }
    Text(
        amountAnnotatedString(value, fractionDigits),
        modifier =
            Modifier.semantics {
                contentDescription = activityAmountAccessibilityDescription(value, amountMinorUnits)
            },
        color = color,
        fontSize = TandemTypography.compactBody,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
