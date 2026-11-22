@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.transactions.deleteTransactionsWithUndo
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.templates.tandemListItemModifier
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemFloatingEnter
import app.tandemforactual.ui.design.theme.tandemFloatingExit
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.budget.openGeneralActivitySearch
import app.tandemforactual.ui.feature.transactions.SyncNoticeCard
import app.tandemforactual.ui.feature.transactions.TransactionRow
import app.tandemforactual.ui.feature.transactions.TransactionSelectionFloatingBar
import app.tandemforactual.ui.feature.transactions.deleteTransactionSelection
import app.tandemforactual.ui.feature.transactions.openTransaction
import app.tandemforactual.ui.feature.transactions.transactionSyncNeedsReview
import app.tandemforactual.ui.feature.transactions.transactionSyncOperations
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.TriangleAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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
    LaunchedEffect(isLiveBudget, state.selectedBudgetId) {
        if (isLiveBudget) mobileSession.activity.ensureActivityTransactionsLoaded(state.selectedBudgetId)
    }
    LaunchedEffect(
        activityListState,
        isLiveBudget,
        state.selectedBudgetId,
    ) {
        if (!isLiveBudget) return@LaunchedEffect
        snapshotFlow {
            val layout = activityListState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = lastVisible >= layout.totalItemsCount - 8 && layout.totalItemsCount > 0
            nearEnd && mobileSession.activity.hasMoreActivityTransactions && !mobileSession.activity.isLoadingActivityTransactions
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                mobileSession.activity.loadMoreActivityTransactions(state.selectedBudgetId)
            }
        }
    }
    LaunchedEffect(isLiveBudget, state.selectedBudgetId, state.activitySortOrder) {
        if (isLiveBudget && state.activitySortOrder != ActivitySortOrder.NEWEST_FIRST) {
            mobileSession.activity.ensureActivityTransactionsLoaded(state.selectedBudgetId)
            while (mobileSession.activity.hasMoreActivityTransactions && mobileSession.activity.activityTransactionPageError == null) {
                mobileSession.activity.loadMoreActivityTransactions(state.selectedBudgetId)
            }
        }
    }
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isSelectingAll by remember(state.selectedBudgetId) { mutableStateOf(false) }
    LaunchedEffect(mobileSession.activity.activityTransactionPageGeneration) {
        selectionMode = false
        selectedTransactionIds = emptySet()
        confirmBulkDelete = false
    }
    val transactionSource = liveTransactions
    val transactions = transactionSource.filterNot(TransactionItem::isSplitParent)
    val selectedSortOrder = state.activitySortOrder
    val showAccountName =
        remember(transactions) {
            transactions
                .asSequence()
                .map(TransactionItem::account)
                .distinct()
                .take(2)
                .count() > 1
        }
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
    val selectedTotalMinor =
        remember(sortedTransactions, selectedTransactionIds) {
            sortedTransactions
                .asSequence()
                .filter { it.recordId in selectedTransactionIds }
                .sumOf(TransactionItem::amountMinorUnits)
        }
    LaunchedEffect(isLiveBudget, selectionMode, selectedTransactionIds, selectedTotalMinor) {
        onActivitySelectionTotalChanged(selectedTotalMinor.takeIf { isLiveBudget && selectionMode && selectedTransactionIds.isNotEmpty() })
    }
    DisposableEffect(state.selectedBudgetId) {
        onDispose { onActivitySelectionTotalChanged(null) }
    }
    Box(Modifier.fillMaxSize()) {
        val syncOperations = remember(mobileSession.pendingWrites) { transactionSyncOperations(mobileSession.pendingWrites) }
        PageLazyColumn(
            verticalSpacing = TandemSpacing.xxSmall,
            isRefreshing = isRefreshing,
            onRefresh = onSyncNow.takeIf { isLiveBudget },
        ) {
            item(key = "activity-search-entry") {
                SurfaceCard(
                    Modifier.padding(bottom = TandemSpacing.small),
                    onClick = { openGeneralActivitySearch(state, navigation) },
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
            if (isLiveBudget) {
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
            item(key = "activity-sort") {
                ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
            }
            if (isLiveBudget &&
                mobileSession.activity.activityTransactionPageBudgetId != state.selectedBudgetId &&
                transactions.isEmpty()
            ) {
                item(key = "activity-loading") { SkeletonRows() }
            } else if (transactions.isEmpty()) {
                item(key = "activity-empty") {
                    EmptyState(
                        if (isLiveBudget) "No transactions yet" else "Choose a budget",
                        if (isLiveBudget) "Sync the budget to refresh." else "Open an Actual budget to view activity.",
                        modifier = Modifier.padding(TandemSpacing.large),
                        actionLabel = "Choose budget".takeIf { !isLiveBudget },
                        onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) }.takeIf { !isLiveBudget },
                    )
                }
            } else {
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
                        selectionMode = selectionMode,
                        selected = transactionId != null && transactionId in selectedTransactionIds,
                        showDateHeader =
                            shouldShowActivityDateHeader(
                                transaction.dateLabel,
                                sortedTransactions.getOrNull(index - 1)?.dateLabel,
                            ),
                        showAccount = showAccountName,
                        showDivider = true,
                        syncStatus = transactionId?.let { syncOperations[it]?.status },
                        modifier = if (isLiveBudget) tandemListItemModifier(tandemReduceMotion) else Modifier,
                        onLongClick =
                            if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                                {
                                    selectionMode = true
                                    selectedTransactionIds = selectedTransactionIds + transactionId
                                }
                            } else {
                                null
                            },
                        highlighted = detailInPane && transactionId != null && transactionId == state.selectedTransactionRecordId,
                        onSwipeDelete =
                            if (isLiveBudget && !state.readOnlyMode && !selectionMode && transactionId != null) {
                                {
                                    scope.launch {
                                        mobileSession.transactions.deleteTransactionsWithUndo(
                                            state.selectedBudgetId,
                                            setOf(transactionId),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    ) {
                        if (selectionMode && transactionId != null) {
                            selectedTransactionIds =
                                if (transactionId in selectedTransactionIds) {
                                    selectedTransactionIds - transactionId
                                } else {
                                    selectedTransactionIds + transactionId
                                }
                            if (selectedTransactionIds.isEmpty()) selectionMode = false
                        } else {
                            openTransaction(state, navigation, transaction, isLive = isLiveBudget, navigate = !detailInPane)
                        }
                    }
                }
            }
            if (isLiveBudget &&
                (mobileSession.activity.isLoadingActivityTransactions || mobileSession.activity.activityTransactionPageError != null)
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
                            onClick = { scope.launch { mobileSession.activity.loadMoreActivityTransactions(state.selectedBudgetId) } },
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().padding(TandemSpacing.medium), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = tandemPalette.accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (selectionMode) item(key = "activity-selection-bottom-inset") { Spacer(Modifier.height(104.dp)) }
        }
        AnimatedVisibility(
            visible = isLiveBudget && selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = tandemFloatingEnter(tandemReduceMotion),
            exit = tandemFloatingExit(tandemReduceMotion),
        ) {
            val selectableIds = sortedTransactions.mapNotNull(TransactionItem::recordId).toSet()
            val allSelected =
                selectableIds.isNotEmpty() &&
                    !mobileSession.activity.hasMoreActivityTransactions &&
                    selectedTransactionIds.containsAll(selectableIds)
            TransactionSelectionFloatingBar(
                allSelected = allSelected,
                canDelete = selectedTransactionIds.isNotEmpty() && !isDeletingSelection,
                isWorking = isSelectingAll,
                onToggleAll = {
                    if (!isSelectingAll) {
                        if (allSelected) {
                            selectedTransactionIds = emptySet()
                            selectionMode = false
                        } else {
                            scope.launch {
                                isSelectingAll = true
                                try {
                                    while (mobileSession.activity.hasMoreActivityTransactions &&
                                        mobileSession.activity.activityTransactionPageError == null
                                    ) {
                                        mobileSession.activity.loadMoreActivityTransactions(state.selectedBudgetId)
                                    }
                                    if (mobileSession.activity.activityTransactionPageError == null) {
                                        selectedTransactionIds =
                                            mobileSession.activity.activityTransactionRecords
                                                .filterNot(BudgetRecordEntity::isParent)
                                                .mapTo(mutableSetOf(), BudgetRecordEntity::recordId)
                                    }
                                } finally {
                                    isSelectingAll = false
                                }
                            }
                        }
                    }
                },
                onDelete = { confirmBulkDelete = true },
                modifier =
                    Modifier
                        .padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            )
        }
    }
    mobileSession.transactionWriteMessage?.takeIf { selectionMode }?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    LaunchedEffect(confirmBulkDelete) {
        if (confirmBulkDelete) {
            isDeletingSelection = true
            val deleted = deleteTransactionSelection(mobileSession, state.selectedBudgetId, selectedTransactionIds)
            isDeletingSelection = false
            confirmBulkDelete = false
            if (deleted) {
                selectedTransactionIds = emptySet()
                selectionMode = false
            }
        }
    }
}

internal fun shouldShowActivityDateHeader(
    currentDateLabel: String,
    previousDateLabel: String?,
): Boolean = previousDateLabel != currentDateLabel

@Composable
internal fun ActivitySortControl(
    selected: ActivitySortOrder,
    onSelect: (ActivitySortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${selected.label}", color = tandemPalette.text, fontSize = TandemTypography.caption)
            Icon(
                Lucide.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp).padding(start = 2.dp),
                tint = tandemPalette.muted,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ActivitySortOrder.entries.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                ) {
                    Text(option.label, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
                }
            }
        }
    }
}
