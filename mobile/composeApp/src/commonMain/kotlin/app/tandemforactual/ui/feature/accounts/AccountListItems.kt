@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.accounts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.tandemforactual.ui.core.session.dragReorderItemModifier
import app.tandemforactual.ui.core.session.reorderModeConsumesItemTap
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.core.state.currencyFractionDigitsForDisplay
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.money.PrivacyAmountText
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowBadge
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

internal const val CLOSED_ACCOUNT_GROUP = "Closed"

/** Empty state shown when there are no closed accounts. */
internal fun LazyListScope.closedAccountsEmptyItem() {
    item(key = "closed-accounts-empty") {
        SurfaceCard {
            EmptyState(
                "No closed accounts",
                "Accounts you close in Tandem or Actual appear here.",
                modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
            )
        }
    }
}

/** Empty state shown when the budget has no accounts or none is open. */
internal fun LazyListScope.accountsEmptyItem(
    isLiveBudget: Boolean,
    navigation: AppNavigation,
) {
    item(key = "accounts-empty") {
        SurfaceCard {
            EmptyState(
                if (isLiveBudget) "No accounts yet" else "Choose a budget",
                if (isLiveBudget) {
                    "Sync this budget to load its account list and balances."
                } else {
                    "Open an Actual budget to view its accounts."
                },
                modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
                actionLabel = "Choose budget".takeIf { !isLiveBudget },
                onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) }.takeIf { !isLiveBudget },
            )
        }
    }
}

/** Empty state shown when search and filters hide every account. */
internal fun LazyListScope.accountsFilterEmptyItem() {
    item(key = "accounts-filter-empty") {
        SurfaceCard {
            EmptyState(
                "No matching accounts",
                "Try a different search or account type.",
                modifier = Modifier.fillMaxWidth().padding(TandemSpacing.large),
            )
        }
    }
}

/** Group name and converted total above each account group. */
@Composable
internal fun AccountGroupHeader(
    group: AccountListGroupUi,
    state: AppState,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.name,
            color = tandemPalette.text,
            fontSize = TandemTypography.label,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        PrivacyAmountText(
            group.total,
            privacyModeEnabled = state.privacyModeEnabled,
            fractionDigits = state.currencyFractionDigitsForDisplay,
            color = tandemPalette.muted,
            fontSize = TandemTypography.caption,
        )
    }
}

/** Account card in the list, with tap, long-press reorder and drag handling. */
@Composable
internal fun LazyItemScope.AccountListCard(
    account: AccountListItemUi,
    movableAccountIds: List<String>,
    reorder: AccountReorder,
    bankBadge: RowBadge?,
    state: AppState,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
    detailInPane: Boolean,
) {
    val isReordering = reorder.selectedAccountOrderId == account.id
    val currentFullOrder = reorder.drag.displayOrder ?: reorder.drag.currentOrder(reorder.queuedOrder)
    val currentGroupOrder = currentFullOrder.filter { it in movableAccountIds }
    val currentIndex = currentGroupOrder.indexOf(account.id)
    val startDrag = { reorder.startDrag(account.id, movableAccountIds) }
    val dragBy = { delta: Float -> reorder.dragBy(account.id, movableAccountIds, delta) }
    val dropDrag = { reorder.dropDrag(account.id) }
    AccountSummaryCard(
        bankBadge = bankBadge,
        account = account,
        merchantArtwork = merchantIconMatcher?.accountIconFor(account.name),
        privacyModeEnabled = state.privacyModeEnabled,
        fractionDigits = state.currencyFractionDigitsForDisplay,
        modifier =
            dragReorderItemModifier(
                itemId = account.id,
                dragState = reorder.drag,
                reduceMotion = tandemReduceMotion,
                dragEnabled = !state.readOnlyMode && movableAccountIds.size > 1,
                onDragStarted = startDrag,
                onDragBy = dragBy,
                onDragStopped = dropDrag,
            ),
        isReordering = isReordering,
        canMoveUp = currentIndex > 0,
        canMoveDown = currentIndex >= 0 && currentIndex < currentGroupOrder.lastIndex,
        reorderEnabled = !state.readOnlyMode,
        highlighted = detailInPane && account.id == state.selectedAccountId,
        onClick = {
            if (reorderModeConsumesItemTap(reorder.selectedAccountOrderId)) {
                reorder.selectedAccountOrderId = null
            } else {
                state.selectedAccountId = account.id
                if (!detailInPane) navigation.open(AppRoute.ACCOUNT_DETAIL)
            }
        },
        onLongClick = {
            if (!state.readOnlyMode &&
                (reorder.selectedAccountOrderId != null || movableAccountIds.size > 1)
            ) {
                reorder.selectedAccountOrderId = account.id
            }
        },
        onEdit = {
            reorder.selectedAccountOrderId = null
            state.selectedAccountId = account.id
            navigation.open(AppRoute.ACCOUNT_OPTIONS)
        },
        onMoveUp = { reorder.enqueueMove(account.id, -1, movableAccountIds) },
        onMoveDown = { reorder.enqueueMove(account.id, 1, movableAccountIds) },
        onDragStarted = startDrag,
        onDragBy = dragBy,
        onDragStopped = dropDrag,
    )
}

/** Link to the closed accounts list, with the closed account count. */
internal fun LazyListScope.closedAccountsEntryItem(
    closedAccountCount: Int,
    navigation: AppNavigation,
) {
    item(key = "closed-accounts-entry") {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Text(
                CLOSED_ACCOUNT_GROUP,
                modifier = Modifier.padding(horizontal = TandemSpacing.small),
                color = tandemPalette.text,
                fontSize = TandemTypography.label,
                fontWeight = FontWeight.SemiBold,
            )
            SurfaceCard {
                ListRow(
                    if (closedAccountCount == 1) "1 closed account" else "$closedAccountCount closed accounts",
                    leading = RowLeading.Icon(Lucide.Archive),
                    onClick = { navigation.open(AppRoute.CLOSED_ACCOUNTS) },
                )
            }
        }
    }
}

/** Add-account and bank-connection buttons at the bottom of the list. */
internal fun LazyListScope.accountBottomActionsItem(
    isRefreshing: Boolean,
    readOnlyMode: Boolean,
    navigation: AppNavigation,
    onAddAccount: () -> Unit,
) {
    item(key = "account-bottom-actions") {
        Column(verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            TandemActionButton(
                "Add account",
                TandemActionButtonTone.PRIMARY,
                Modifier.fillMaxWidth(),
                icon = Lucide.Plus,
                enabled = !isRefreshing && !readOnlyMode,
                onClick = onAddAccount,
            )
            TandemActionButton(
                "Manage bank connections",
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.Landmark,
                enabled = !isRefreshing,
            ) { navigation.open(AppRoute.BANK_CONNECTIONS) }
        }
    }
}
