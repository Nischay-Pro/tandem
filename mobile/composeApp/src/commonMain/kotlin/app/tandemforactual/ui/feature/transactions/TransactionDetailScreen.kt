@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.categoryIconFor
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.ui.ReimbursementCard
import app.tandemforactual.ui.core.ui.TransactionSyncNotice
import app.tandemforactual.ui.core.ui.transactionSyncOperations
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.CloudCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotepadText
import com.composables.icons.lucide.Tags
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun TransactionDetailScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
    showPayee: Boolean = false,
) {
    val isLive = state.selectedTransactionIsLive
    val amount = state.selectedTransactionAmountMinor
    if (!isLive || amount == null || state.selectedTransactionRecordId.isBlank()) {
        PageColumn {
            EmptyState(
                "Transaction unavailable",
                "Choose a transaction from an open Actual budget.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
            )
        }
        return
    }
    val merchantIcon = remember(merchantIconMatcher, state.transactionPayee) { merchantIconMatcher?.iconFor(state.transactionPayee) }
    val categoryIcon =
        remember(state.categoryIconOverrides, state.transactionCategory, state.newTransactionCategoryId) {
            state.categoryIconFor(state.newTransactionCategoryId, state.transactionCategory)
        }
    val syncOperation = transactionSyncOperations(mobileSession.pendingWrites)[state.selectedTransactionRecordId]
    val hasUnsentTransactionChange = syncOperation != null
    val isSyncedToActual = isLive && state.selectedTransactionRecordId.isNotBlank() && !hasUnsentTransactionChange
    val syncDescription =
        when {
            isSyncedToActual -> "Synced to Actual"
            else -> "Changes not synced to Actual"
        }
    PageColumn(verticalSpacing = 12.dp) {
        TransactionDetailHeader(state, amount, merchantIcon, categoryIcon, showPayee, isSyncedToActual, syncDescription)
        AnimatedVisibility(syncOperation != null) {
            syncOperation?.let { TransactionSyncNotice(it, state, navigation) }
        }
        TransactionDetailFields(state)
        ReimbursementCard(state, mobileSession, navigation)
        PrimaryButton("Edit transaction", enabled = !state.readOnlyMode) {
            navigation.open(AppRoute.EDIT_TRANSACTION)
        }
        state.addAgainDraft()?.let { draft ->
            SecondaryButton("Add again", enabled = !state.readOnlyMode) {
                navigation.newTransaction(draft.accountId)
                state.applyAddAgain(draft)
            }
        }
    }
}

/** The merchant or category icon, optional payee, amount and sync state at the top of a transaction. */
@Composable
private fun TransactionDetailHeader(
    state: AppState,
    amount: Long,
    merchantIcon: DrawableResource?,
    categoryIcon: ImageVector,
    showPayee: Boolean,
    isSyncedToActual: Boolean,
    syncDescription: String,
) {
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
        if (showPayee) {
            Text(
                state.selectedTransactionPayeeName.ifBlank { state.transactionPayee },
                modifier = Modifier.fillMaxWidth(),
                color = tandemPalette.text,
                fontSize = TandemTypography.sectionTitle,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
}

/** The account, category, date and notes of a transaction. */
@Composable
private fun TransactionDetailFields(state: AppState) {
    SectionCard(title = "") {
        ListRow("Account", leading = RowLeading.Account(state.transactionAccount), trailing = state.transactionAccount)
        DividerLine()
        ListRow("Category", leading = RowLeading.Icon(Lucide.Tags), trailing = state.transactionCategory)
        DividerLine()
        ListRow(
            "Date",
            leading = RowLeading.Icon(Lucide.CalendarDays),
            trailing = displayTransactionDate(state.transactionDate),
        )
        DividerLine()
        ListRow(
            "Notes",
            leading = RowLeading.Icon(Lucide.NotepadText),
            trailing = state.transactionNotes.ifBlank { "No notes" },
        )
    }
}
