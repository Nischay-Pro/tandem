@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.SectionCard
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.put
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun ActivityScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    liveTransactions: List<TransactionItem>,
    sortCache: ActivityTransactionSortCache,
    isRefreshing: Boolean,
    onSyncNow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    val transactionSource = if (isLiveBudget) liveTransactions else previewTransactions
    val transactions = if (isLiveBudget) transactionSource.filterNot(TransactionItem::isSplitParent) else transactionSource
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
        val selectableIds = sortedTransactions.mapNotNull(TransactionItem::recordId).toSet()
        if (selectionMode) {
            item(key = "activity-selection-actions") {
                TransactionSelectionActions(
                    selectedCount = selectedTransactionIds.size,
                    allSelected = selectableIds.isNotEmpty() && selectedTransactionIds.containsAll(selectableIds),
                    onToggleAll = {
                        selectedTransactionIds =
                            if (selectableIds.isNotEmpty() && selectedTransactionIds.containsAll(selectableIds)) {
                                emptySet()
                            } else {
                                selectableIds
                            }
                    },
                    onDelete = { confirmBulkDelete = true },
                    onCancel = {
                        selectionMode = false
                        selectedTransactionIds = emptySet()
                    },
                )
            }
        }
        val recentDescription = if (isLiveBudget) state.activitySortOrder.label else "${transactions.size} sample entries"
        if (transactions.isEmpty()) {
            item(key = "activity-empty") {
                SectionCard("Recent transactions", recentDescription) {
                    EmptyState(
                        if (isLiveBudget) "No transactions yet" else "No sample transactions",
                        if (isLiveBudget) "Sync the budget to refresh." else null,
                        modifier = Modifier.padding(TandemSpacing.large),
                    )
                }
            }
        } else {
            item(key = "activity-recent-title") { SectionCard("Recent transactions", recentDescription) {} }
            itemsIndexed(
                sortedTransactions,
                key = { index, transaction ->
                    val prefix = if (isLiveBudget) "activity" else "sample"
                    "$prefix:${transaction.recordId ?: "$index:${transaction.dateLabel}:${transaction.payee}:${transaction.amountMinorUnits}"}"
                },
            ) { _, transaction ->
                SurfaceCard(modifier = if (isLiveBudget) tandemListItemModifier(tandemReduceMotion) else Modifier) {
                    val transactionId = transaction.recordId
                    TransactionRow(
                        transaction,
                        selectionMode = selectionMode,
                        selected = transactionId != null && transactionId in selectedTransactionIds,
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
        if (!isLiveBudget) item(key = "activity-sample-note") { PreviewNote("Transactions use sample data.") }
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
