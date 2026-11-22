@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.search.ActivitySortOrder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import kotlinx.coroutines.flow.collect

/** Loads the first activity page, more pages near the end of the list, and every page for a non-default sort. */
@Composable
internal fun ActivityPagingEffects(
    mobileSession: TandemMobileSession,
    budgetId: String,
    isLiveBudget: Boolean,
    activityListState: LazyListState,
    sortOrder: ActivitySortOrder,
) {
    LaunchedEffect(isLiveBudget, budgetId) {
        if (isLiveBudget) mobileSession.activity.ensureActivityTransactionsLoaded(budgetId)
    }
    LaunchedEffect(
        activityListState,
        isLiveBudget,
        budgetId,
    ) {
        if (!isLiveBudget) return@LaunchedEffect
        snapshotFlow {
            val layout = activityListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = lastVisible >= layout.totalItemsCount - 8 && layout.totalItemsCount > 0
            nearEnd && mobileSession.activity.hasMoreActivityTransactions && !mobileSession.activity.isLoadingActivityTransactions
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                mobileSession.activity.loadMoreActivityTransactions(budgetId)
            }
        }
    }
    LaunchedEffect(isLiveBudget, budgetId, sortOrder) {
        if (isLiveBudget && sortOrder != ActivitySortOrder.NEWEST_FIRST) {
            mobileSession.activity.ensureActivityTransactionsLoaded(budgetId)
            while (mobileSession.activity.hasMoreActivityTransactions && mobileSession.activity.activityTransactionPageError == null) {
                mobileSession.activity.loadMoreActivityTransactions(budgetId)
            }
        }
    }
}

/** The card that opens transaction search. */
internal fun LazyListScope.activitySearchEntryItem(onClick: () -> Unit) {
    item(key = "activity-search-entry") {
        SurfaceCard(
            Modifier.padding(bottom = TandemSpacing.small),
            onClick = onClick,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(TandemSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Icon(Lucide.Search, contentDescription = null, modifier = Modifier.size(22.dp), tint = tandemPalette.accent)
                Text(
                    "Search transactions",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.compactBody,
                    modifier = Modifier.weight(1f),
                )
                Icon(Lucide.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.muted)
            }
        }
    }
}

/** Shown when there are no transactions, or no budget is open yet. */
internal fun LazyListScope.activityEmptyItem(
    isLiveBudget: Boolean,
    navigation: AppNavigation,
) {
    item(key = "activity-empty") {
        EmptyState(
            if (isLiveBudget) "No transactions yet" else "Choose a budget",
            if (isLiveBudget) "Sync the budget to refresh." else "Open an Actual budget to view activity.",
            modifier = Modifier.padding(TandemSpacing.large),
            actionLabel = "Choose budget".takeIf { !isLiveBudget },
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) }.takeIf { !isLiveBudget },
        )
    }
}

/** A spinner while the next page loads, or a retry button when it failed. */
internal fun LazyListScope.activityPageStatusItem(
    mobileSession: TandemMobileSession,
    onRetry: () -> Unit,
) {
    item(key = "activity-page-status") {
        if (mobileSession.activity.activityTransactionPageError != null) {
            TandemActionButton(
                "Try again",
                TandemActionButtonTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
                icon = Lucide.RefreshCw,
                enabled = !mobileSession.activity.isLoadingActivityTransactions,
                loading = mobileSession.activity.isLoadingActivityTransactions,
                onClick = onRetry,
            )
        } else {
            Box(Modifier.fillMaxWidth().padding(TandemSpacing.medium), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), color = tandemPalette.accent, strokeWidth = 2.dp)
            }
        }
    }
}
