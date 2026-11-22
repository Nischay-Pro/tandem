@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.PrimaryButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.tandemListItemModifier
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.TriangleAlert

@Composable
internal fun ConflictInboxScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    merchantIconMatcher: MerchantIconMatcherPack?,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        PageColumn {
            EmptyState(
                "Choose a budget",
                "Open an Actual budget to review pending changes.",
                actionLabel = "Choose budget",
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
            )
        }
        return
    }
    val conflicts = conflictInboxItems(state, mobileSession, liveTransactions)
    PageLazyColumn {
        item(key = "conflicts-heading") {
            ConflictInboxHeading(conflicts.size)
        }
        items(conflicts, key = { "conflict:${it.id}" }) { conflict ->
            SurfaceCard(modifier = tandemListItemModifier(tandemReduceMotion)) {
                val merchantIcon = conflict.merchantIcon ?: merchantIconMatcher?.iconFor(conflict.title)
                ListRow(
                    conflict.title,
                    conflict.description,
                    leading =
                        RowLeading.Merchant(
                            merchantIcon,
                            fallback = conflict.categoryIcon ?: Lucide.Receipt,
                        ),
                    onClick = {
                        mobileSession.clearTransactionWriteMessage()
                        state.selectedConflictOperationId = conflict.id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    },
                )
            }
        }
        if (conflicts.isNotEmpty()) {
            item(key = "conflicts-footer") {
                Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                    ConflictNote(
                        icon = Lucide.TriangleAlert,
                        title = "No changes will be discarded",
                        detail = "until you choose a version.",
                    )
                    PrimaryButton("Review first change") {
                        mobileSession.clearTransactionWriteMessage()
                        state.selectedConflictOperationId = conflicts.first().id
                        navigation.open(AppRoute.CONFLICT_REVIEW)
                    }
                }
            }
        }
    }
}

private fun conflictInboxItems(
    state: AppState,
    mobileSession: TandemMobileSession,
    liveTransactions: List<TransactionItem>,
): List<ConflictInboxItemUi> =
    mobileSession.pendingWrites
        .filter { it.status == "conflict" }
        .map { operation ->
            val transaction = liveTransactions.firstOrNull { it.recordId == operation.recordId }
            val snapshot = conflictSnapshot(operation)
            val categoryId = snapshot?.stringValue("category")
            val categoryName =
                transaction?.category
                    ?: categoryId?.let { id ->
                        mobileSession.categoryRecords.firstOrNull { it.recordId == id }?.recordName()
                    }
                    ?: "Uncategorized"
            ConflictInboxItemUi(
                operation.operationId,
                conflictPayeeTitle(operation, liveTransactions, mobileSession) ?: "Transaction",
                listOfNotNull(transactionIdentity(operation, transaction, state, mobileSession), categoryName).joinToString(" · "),
                transaction?.merchantIcon,
                transaction?.categoryIcon,
            )
        }

/** The title, explanation and count chip above the conflicts list. */
@Composable
private fun ConflictInboxHeading(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium)) {
        SyncPageHeading(
            eyebrow = "Sync",
            title = if (count == 0) "Nothing to review." else "Needs review.",
            detail =
                when (count) {
                    0 -> "Every change on this device matches Actual."
                    1 -> "One item changed on this device and in Actual. Choose which version to keep."
                    else -> "$count items changed on this device and in Actual. Choose which version to keep."
                },
        )
        if (count > 0) {
            SyncCountChip("$count to review", tandemPalette.warning)
        }
    }
}
