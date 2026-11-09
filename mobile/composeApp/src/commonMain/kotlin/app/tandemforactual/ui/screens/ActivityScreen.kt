@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
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
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
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
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val activityListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    LaunchedEffect(isLiveBudget, state.selectedBudgetId) {
        if (isLiveBudget) mobileSession.ensureActivityTransactionsLoaded(state.selectedBudgetId)
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
            nearEnd && mobileSession.hasMoreActivityTransactions && !mobileSession.isLoadingActivityTransactions
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                mobileSession.loadMoreActivityTransactions(state.selectedBudgetId)
            }
        }
    }
    LaunchedEffect(isLiveBudget, state.selectedBudgetId, state.activitySortOrder) {
        if (isLiveBudget && state.activitySortOrder != ActivitySortOrder.NEWEST_FIRST) {
            mobileSession.ensureActivityTransactionsLoaded(state.selectedBudgetId)
            while (mobileSession.hasMoreActivityTransactions && mobileSession.activityTransactionPageError == null) {
                mobileSession.loadMoreActivityTransactions(state.selectedBudgetId)
            }
        }
    }
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isSelectingAll by remember(state.selectedBudgetId) { mutableStateOf(false) }
    LaunchedEffect(mobileSession.activityTransactionPageGeneration) {
        selectionMode = false
        selectedTransactionIds = emptySet()
        confirmBulkDelete = false
    }
    val transactionSource = if (isLiveBudget) liveTransactions else previewTransactions
    val transactions = if (isLiveBudget) transactionSource.filterNot(TransactionItem::isSplitParent) else transactionSource
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
        PageLazyColumn(
            isRefreshing = isRefreshing,
            onRefresh = onSyncNow.takeIf { isLiveBudget },
        ) {
            if (!isLiveBudget) item(key = "activity-preview-banner") { SampleBanner() }
            item(key = "activity-search-entry") {
                SurfaceCard(onClick = { openGeneralActivitySearch(state, navigation) }) {
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
            item(key = "activity-sort") {
                ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
            }
            if (isLiveBudget && mobileSession.activityTransactionPageBudgetId != state.selectedBudgetId && transactions.isEmpty()) {
                item(key = "activity-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tandemPalette.accent)
                    }
                }
            } else if (transactions.isEmpty()) {
                item(key = "activity-empty") {
                    EmptyState(
                        if (isLiveBudget) "No transactions yet" else "No sample transactions",
                        if (isLiveBudget) "Sync the budget to refresh." else null,
                        modifier = Modifier.padding(TandemSpacing.large),
                    )
                }
            } else {
                itemsIndexed(
                    sortedTransactions,
                    key = { index, transaction ->
                        val prefix = if (isLiveBudget) "activity" else "sample"
                        "$prefix:${transaction.recordId ?: "$index:${transaction.dateLabel}:${transaction.payee}:${transaction.amountMinorUnits}"}"
                    },
                ) { index, transaction ->
                    SurfaceCard(modifier = if (isLiveBudget) tandemListItemModifier(tandemReduceMotion) else Modifier) {
                        val transactionId = transaction.recordId
                        TransactionRow(
                            transaction,
                            selectionMode = selectionMode,
                            selected = transactionId != null && transactionId in selectedTransactionIds,
                            showDateHeader =
                                shouldShowActivityDateHeader(
                                    transaction.dateLabel,
                                    sortedTransactions.getOrNull(index - 1)?.dateLabel,
                                ),
                            showAccount = showAccountName,
                            onLongClick =
                                if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                                    {
                                        selectionMode = true
                                        selectedTransactionIds = selectedTransactionIds + transactionId
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
                                openTransaction(state, navigation, transaction, isLive = isLiveBudget)
                            }
                        }
                    }
                }
            }
            if (isLiveBudget && (mobileSession.isLoadingActivityTransactions || mobileSession.activityTransactionPageError != null)) {
                item(key = "activity-page-status") {
                    if (mobileSession.activityTransactionPageError != null) {
                        TandemActionButton(
                            "Retry loading transactions",
                            TandemActionButtonTone.NEUTRAL,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Lucide.RefreshCw,
                            enabled = !mobileSession.isLoadingActivityTransactions,
                            loading = mobileSession.isLoadingActivityTransactions,
                            onClick = { scope.launch { mobileSession.loadMoreActivityTransactions(state.selectedBudgetId) } },
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().padding(TandemSpacing.medium), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = tandemPalette.accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (!isLiveBudget) item(key = "activity-sample-note") { PreviewNote("Transactions use sample data.") }
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
                    !mobileSession.hasMoreActivityTransactions &&
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
                                    while (mobileSession.hasMoreActivityTransactions &&
                                        mobileSession.activityTransactionPageError == null
                                    ) {
                                        mobileSession.loadMoreActivityTransactions(state.selectedBudgetId)
                                    }
                                    if (mobileSession.activityTransactionPageError == null) {
                                        selectedTransactionIds =
                                            mobileSession.activityTransactionRecords
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
    if (confirmBulkDelete) {
        TransactionBulkDeleteDialog(
            selectedCount = selectedTransactionIds.size,
            isDeleting = isDeletingSelection,
            onDismiss = { confirmBulkDelete = false },
            onConfirm = {
                scope.launch {
                    isDeletingSelection = true
                    val deleted = deleteTransactionSelection(mobileSession, state.selectedBudgetId, selectedTransactionIds)
                    isDeletingSelection = false
                    confirmBulkDelete = false
                    if (deleted) {
                        selectedTransactionIds = emptySet()
                        selectionMode = false
                    }
                }
            },
        )
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
