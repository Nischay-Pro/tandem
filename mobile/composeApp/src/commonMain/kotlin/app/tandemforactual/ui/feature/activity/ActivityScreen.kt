@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.navigation.openGeneralActivitySearch
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.deleteTransactionSelection
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.ui.SyncNoticeCard
import app.tandemforactual.ui.core.ui.TransactionRow
import app.tandemforactual.ui.core.ui.TransactionSelectionFloatingBar
import app.tandemforactual.ui.core.ui.transactionSyncNeedsReview
import app.tandemforactual.ui.core.ui.transactionSyncOperations
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.tandemListItemModifier
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemFloatingEnter
import app.tandemforactual.ui.design.theme.tandemFloatingExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.activity.components.ActivityPagingEffects
import app.tandemforactual.ui.feature.activity.components.ActivitySelectionState
import app.tandemforactual.ui.feature.activity.components.activityEmptyItem
import app.tandemforactual.ui.feature.activity.components.activityPageStatusItem
import app.tandemforactual.ui.feature.activity.components.activitySearchEntryItem
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put

@Composable
internal fun ActivityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    sortCache: ActivityTransactionSortCache,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
    isRefreshing: Boolean,
    onSyncNow: () -> Unit,
    detailInPane: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val activityListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    ActivityPagingEffects(mobileSession, state.selectedBudgetId, isLiveBudget, activityListState, state.activitySortOrder)
    val selection = remember(state.selectedBudgetId) { ActivitySelectionState() }
    LaunchedEffect(mobileSession.activity.activityTransactionPageGeneration) {
        selection.selectionMode = false
        selection.selectedTransactionIds = emptySet()
        selection.confirmBulkDelete = false
    }
    val transactions = liveTransactions.filterNot(TransactionItem::isSplitParent)
    val showAccountName =
        remember(transactions) {
            transactions
                .asSequence()
                .map(TransactionItem::account)
                .distinct()
                .take(2)
                .count() > 1
        }
    val sortedTransactions = rememberSortedActivityTransactions(state, liveTransactions, transactions, sortCache)
    ActivitySelectionTotalEffect(state.selectedBudgetId, isLiveBudget, selection, sortedTransactions, onActivitySelectionTotalChanged)
    Box(Modifier.fillMaxSize()) {
        val syncOperations = remember(mobileSession.pendingWrites) { transactionSyncOperations(mobileSession.pendingWrites) }
        PageLazyColumn(
            verticalSpacing = TandemSpacing.xxSmall,
            isRefreshing = isRefreshing,
            onRefresh = onSyncNow.takeIf { isLiveBudget },
        ) {
            activitySearchEntryItem { openGeneralActivitySearch(state, navigation) }
            if (isLiveBudget) activitySyncItem(syncOperations, navigation)
            item(key = "activity-sort") {
                ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
            }
            if (isLiveBudget &&
                mobileSession.activity.activityTransactionPageBudgetId != state.selectedBudgetId &&
                transactions.isEmpty()
            ) {
                item(key = "activity-loading") { SkeletonRows() }
            } else if (transactions.isEmpty()) {
                activityEmptyItem(isLiveBudget, navigation)
            } else {
                activityTransactionItems(
                    ActivityRowContext(state, mobileSession, navigation, scope, isLiveBudget, detailInPane, showAccountName),
                    sortedTransactions,
                    selection,
                    syncOperations,
                )
            }
            if (isLiveBudget &&
                (mobileSession.activity.isLoadingActivityTransactions || mobileSession.activity.activityTransactionPageError != null)
            ) {
                activityPageStatusItem(mobileSession) {
                    scope.launch { mobileSession.activity.loadMoreActivityTransactions(state.selectedBudgetId) }
                }
            }
            if (selection.selectionMode) item(key = "activity-selection-bottom-inset") { Spacer(Modifier.height(104.dp)) }
        }
        ActivitySelectionBar(state, mobileSession, scope, isLiveBudget, selection, sortedTransactions)
    }
    mobileSession.transactionWriteMessage?.takeIf { selection.selectionMode }?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    ActivityBulkDeleteEffect(mobileSession, state.selectedBudgetId, selection)
}

/** Deletes the selected transactions once the delete is confirmed. */
@Composable
internal fun ActivityBulkDeleteEffect(
    mobileSession: TandemMobileSession,
    budgetId: String,
    selection: ActivitySelectionState,
) {
    LaunchedEffect(selection.confirmBulkDelete) {
        if (selection.confirmBulkDelete) {
            selection.isDeletingSelection = true
            val deleted = deleteTransactionSelection(mobileSession, budgetId, selection.selectedTransactionIds)
            selection.isDeletingSelection = false
            selection.confirmBulkDelete = false
            if (deleted) {
                selection.selectedTransactionIds = emptySet()
                selection.selectionMode = false
            }
        }
    }
}

@Composable
private fun rememberSortedActivityTransactions(
    state: AppState,
    transactionSource: List<TransactionItem>,
    transactions: List<TransactionItem>,
    sortCache: ActivityTransactionSortCache,
): List<TransactionItem> {
    val selectedSortOrder = state.activitySortOrder
    val cachedSorted = sortCache.get(state.selectedBudgetId, transactionSource, selectedSortOrder)
    var sortedTransactions by
        remember(state.selectedBudgetId, transactionSource, selectedSortOrder) {
            mutableStateOf(cachedSorted ?: transactions)
        }
    LaunchedEffect(state.selectedBudgetId, transactionSource, selectedSortOrder) {
        sortedTransactions =
            sortCache.get(state.selectedBudgetId, transactionSource, selectedSortOrder)
                ?: withContext(Dispatchers.Default) {
                    sortActivityTransactions(transactions, selectedSortOrder)
                }.also { sortCache.put(state.selectedBudgetId, transactionSource, selectedSortOrder, it) }
    }
    return sortedTransactions
}

/** Reports the selected transactions' total to the shell while selecting, and clears it on leaving. */
@Composable
private fun ActivitySelectionTotalEffect(
    budgetId: String,
    isLiveBudget: Boolean,
    selection: ActivitySelectionState,
    sortedTransactions: List<TransactionItem>,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
) {
    val selectedTotalMinor =
        remember(sortedTransactions, selection.selectedTransactionIds) {
            sortedTransactions
                .asSequence()
                .filter { it.recordId in selection.selectedTransactionIds }
                .sumOf(TransactionItem::amountMinorUnits)
        }
    LaunchedEffect(isLiveBudget, selection.selectionMode, selection.selectedTransactionIds, selectedTotalMinor) {
        onActivitySelectionTotalChanged(
            selectedTotalMinor.takeIf { isLiveBudget && selection.selectionMode && selection.selectedTransactionIds.isNotEmpty() },
        )
    }
    DisposableEffect(budgetId) {
        onDispose { onActivitySelectionTotalChanged(null) }
    }
}

/** A notice for changes that are waiting to sync or need review. */
private fun LazyListScope.activitySyncItem(
    syncOperations: Map<String, OutboxOperationEntity>,
    navigation: AppNavigation,
) {
    item(key = "activity-sync") {
        val needsReview = syncOperations.values.count { transactionSyncNeedsReview(it.status) }
        val waiting = syncOperations.size - needsReview
        AnimatedVisibility(syncOperations.isNotEmpty()) {
            if (needsReview > 0) {
                SyncNoticeCard(
                    Lucide.TriangleAlert,
                    tandemPalette.warning,
                    if (needsReview == 1) "1 change needs review" else "$needsReview changes need review",
                    "Open Sync Center to choose what to keep.",
                    onClick = { navigation.open(AppRoute.SYNC_CENTER) },
                )
            } else {
                SyncNoticeCard(
                    Lucide.Clock,
                    tandemPalette.accent,
                    if (waiting == 1) "1 change waiting to sync" else "$waiting changes waiting to sync",
                    "Saved on this device. Sent on the next sync.",
                    onClick = { navigation.open(AppRoute.PENDING_CHANGES) },
                )
            }
        }
    }
}

private class ActivityRowContext(
    val state: AppState,
    val mobileSession: TandemMobileSession,
    val navigation: AppNavigation,
    val scope: CoroutineScope,
    val isLiveBudget: Boolean,
    val detailInPane: Boolean,
    val showAccountName: Boolean,
)

/** The sorted transaction rows, with date headers, selection, swipe to delete and opening. */
private fun LazyListScope.activityTransactionItems(
    context: ActivityRowContext,
    sortedTransactions: List<TransactionItem>,
    selection: ActivitySelectionState,
    syncOperations: Map<String, OutboxOperationEntity>,
) {
    val state = context.state
    val isLiveBudget = context.isLiveBudget
    itemsIndexed(
        sortedTransactions,
        key = { index, transaction ->
            "activity:${transaction.recordId ?: "$index:${transaction.dateLabel}:${transaction.payee}:${transaction.amountMinorUnits}"}"
        },
    ) { index, transaction ->
        val transactionId = transaction.recordId
        TransactionRow(
            transaction,
            activityStyle = true,
            selectionMode = selection.selectionMode,
            selected = transactionId != null && transactionId in selection.selectedTransactionIds,
            showDateHeader =
                shouldShowActivityDateHeader(
                    transaction.dateLabel,
                    sortedTransactions.getOrNull(index - 1)?.dateLabel,
                ),
            showAccount = context.showAccountName,
            showDivider = true,
            syncStatus = transactionId?.let { syncOperations[it]?.status },
            modifier = if (isLiveBudget) tandemListItemModifier(tandemReduceMotion) else Modifier,
            onLongClick =
                if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                    {
                        selection.selectionMode = true
                        selection.selectedTransactionIds = selection.selectedTransactionIds + transactionId
                    }
                } else {
                    null
                },
            highlighted = context.detailInPane && transactionId != null && transactionId == state.selectedTransactionRecordId,
            onSwipeDelete =
                if (isLiveBudget && !state.readOnlyMode && !selection.selectionMode && transactionId != null) {
                    {
                        context.scope.launch {
                            context.mobileSession.transactions.deleteTransactionsWithUndo(state.selectedBudgetId, setOf(transactionId))
                        }
                    }
                } else {
                    null
                },
        ) {
            if (selection.selectionMode && transactionId != null) {
                selection.toggle(transactionId)
            } else {
                openTransaction(state, context.navigation, transaction, isLive = isLiveBudget, navigate = !context.detailInPane)
            }
        }
    }
}

/** The floating bar for selecting every transaction or deleting the selection. */
@Composable
private fun BoxScope.ActivitySelectionBar(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    isLiveBudget: Boolean,
    selection: ActivitySelectionState,
    sortedTransactions: List<TransactionItem>,
) {
    AnimatedVisibility(
        visible = isLiveBudget && selection.selectionMode,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = tandemFloatingEnter(tandemReduceMotion),
        exit = tandemFloatingExit(tandemReduceMotion),
    ) {
        val selectableIds = sortedTransactions.mapNotNull(TransactionItem::recordId).toSet()
        val allSelected =
            selectableIds.isNotEmpty() &&
                !mobileSession.activity.hasMoreActivityTransactions &&
                selection.selectedTransactionIds.containsAll(selectableIds)
        TransactionSelectionFloatingBar(
            allSelected = allSelected,
            canDelete = selection.selectedTransactionIds.isNotEmpty() && !selection.isDeletingSelection,
            isWorking = selection.isSelectingAll,
            onToggleAll = {
                if (!selection.isSelectingAll) {
                    if (allSelected) {
                        selection.selectedTransactionIds = emptySet()
                        selection.selectionMode = false
                    } else {
                        scope.launch { selectAllActivityTransactions(mobileSession, state.selectedBudgetId, selection) }
                    }
                }
            },
            onDelete = { selection.confirmBulkDelete = true },
            modifier =
                Modifier
                    .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        )
    }
}

private suspend fun selectAllActivityTransactions(
    mobileSession: TandemMobileSession,
    budgetId: String,
    selection: ActivitySelectionState,
) {
    selection.isSelectingAll = true
    try {
        while (mobileSession.activity.hasMoreActivityTransactions &&
            mobileSession.activity.activityTransactionPageError == null
        ) {
            mobileSession.activity.loadMoreActivityTransactions(budgetId)
        }
        if (mobileSession.activity.activityTransactionPageError == null) {
            selection.selectedTransactionIds =
                mobileSession.activity.activityTransactionRecords
                    .filterNot(BudgetRecordEntity::isParent)
                    .mapTo(mutableSetOf(), BudgetRecordEntity::recordId)
        }
    } finally {
        selection.isSelectingAll = false
    }
}
