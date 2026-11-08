@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.DividerLine
import app.tandemforactual.ui.components.ListRow
import app.tandemforactual.ui.components.PrimaryButton
import app.tandemforactual.ui.components.RowLeading
import app.tandemforactual.ui.components.SectionCard
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.WalletCards
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun TransactionDetailScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val isLive = state.selectedTransactionIsLive
    val amount = state.selectedTransactionAmountMinor ?: -4_218L
    val merchantIcon = remember(merchantIconMatcher, state.transactionPayee) { merchantIconMatcher?.iconFor(state.transactionPayee) }
    val categoryIcon =
        remember(state.categoryIconOverrides, state.transactionCategory, state.newTransactionCategoryId) {
            state.categoryIconFor(state.newTransactionCategoryId, state.transactionCategory)
        }
    val hasUnsentTransactionChange =
        mobileSession.pendingWrites.any { operation ->
            operation.recordType == "transaction" &&
                operation.recordId == state.selectedTransactionRecordId &&
                operation.status in UNSYNCED_TRANSACTION_WRITE_STATUSES
        }
    val isSyncedToActual = isLive && state.selectedTransactionRecordId.isNotBlank() && !hasUnsentTransactionChange
    val syncDescription =
        when {
            isSyncedToActual -> "Synced to Actual"
            isLive -> "Changes not synced to Actual"
            else -> "Sample transaction, not synced to Actual"
        }
    PageColumn(verticalSpacing = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(TandemShapes.medium))
                        .background(tandemPalette.raised),
                    contentAlignment = Alignment.Center,
                ) {
                    if (merchantIcon != null) {
                        Image(
                            painter = painterResource(merchantIcon),
                            contentDescription = null,
                            modifier = Modifier.size(38.dp).padding(TandemSpacing.extraSmall),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = tandemPalette.accent,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(28.dp))
                Text(
                    state.formatCurrency(amount),
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.text,
                    fontSize = TandemTypography.amount,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (isSyncedToActual) Lucide.CloudCheck else Lucide.CloudOff,
                    contentDescription = syncDescription,
                    modifier = Modifier.size(28.dp),
                    tint = if (isSyncedToActual) tandemPalette.positive else tandemPalette.muted,
                )
            }
        }
        SectionCard(title = "") {
            ListRow("Account", leading = RowLeading.Icon(Lucide.WalletCards), trailing = state.transactionAccount)
            DividerLine()
            ListRow("Category", leading = RowLeading.Icon(Lucide.Tags), trailing = state.transactionCategory)
            DividerLine()
            ListRow("Date", leading = RowLeading.Icon(Lucide.CalendarDays), trailing = state.transactionDate)
            DividerLine()
            ListRow(
                "Notes",
                leading = RowLeading.Icon(Lucide.NotepadText),
                trailing = state.transactionNotes.ifBlank { "No notes" },
            )
        }
        if (isLive) {
            PrimaryButton("Edit transaction", enabled = state.selectedTransactionRecordId.isNotBlank() && !state.readOnlyMode) {
                navigation.open(AppRoute.EDIT_TRANSACTION)
            }
        } else {
            PreviewNote("This sample transaction is read-only. Editing it won’t change Actual Budget.")
            PrimaryButton("Edit transaction", enabled = !state.readOnlyMode) { navigation.open(AppRoute.EDIT_TRANSACTION) }
        }
    }
}
