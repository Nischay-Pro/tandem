@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.InlineMessage
import app.tandemforactual.ui.components.InlineMessageKind
import app.tandemforactual.ui.components.SurfaceCard
import app.tandemforactual.ui.components.TandemActionButton
import app.tandemforactual.ui.components.TandemActionButtonTone
import app.tandemforactual.ui.components.TandemIconButton
import app.tandemforactual.ui.components.TandemTaskSheet
import app.tandemforactual.ui.model.recordName
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ActivitySearchScreen(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
    autoFocusSearch: Boolean,
) {
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf(state.activitySearchQuery) }
    val savedSearches =
        remember(state.savedActivitySearchesJson, state.selectedBudgetId) {
            decodeSavedActivitySearches(state.savedActivitySearchesJson)
                .filter { it.budgetId == state.selectedBudgetId }
        }
    var showSaveSearchDialog by rememberSaveable { mutableStateOf(false) }
    var savedSearchNameDraft by rememberSaveable { mutableStateOf("") }
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    var selectionMode by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var selectedTransactionIds by remember(state.selectedBudgetId) { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBulkDelete by remember(state.selectedBudgetId) { mutableStateOf(false) }
    var isDeletingSelection by remember(state.selectedBudgetId) { mutableStateOf(false) }
    val filters = state.activitySearchFilters
    val hasActiveSearch = search.isNotBlank() || filters.isNotEmpty()
    val cachedSearch = state.activitySearchCache?.takeIf { it.budgetId == state.selectedBudgetId }
    val cachedEvaluation =
        state.activitySearchResultCache?.takeIf { cached ->
            cached.budgetId == state.selectedBudgetId &&
                cached.query == search &&
                cached.filters == filters &&
                cached.sourceRecords === cachedSearch?.searchableRecords
        }
    LaunchedEffect(state.selectedBudgetId, mobileSession) {
        if (isLiveBudget) mobileSession.observeActivitySearchRecords(state.selectedBudgetId)
    }
    var searchableRecords by remember(state.selectedBudgetId) {
        mutableStateOf(cachedSearch?.searchableRecords ?: previewTransactions.map(TransactionItem::toActivitySearchRecord))
    }
    var loadedSearchBudgetId by remember(state.selectedBudgetId) {
        mutableStateOf(cachedSearch?.budgetId ?: "")
    }
    var isBuildingSearchRecords by remember(state.selectedBudgetId) { mutableStateOf(isLiveBudget && cachedSearch == null) }
    LaunchedEffect(
        state.selectedBudgetId,
        mobileSession.activitySearchRecordsBudgetId,
        mobileSession.activitySearchRecords,
        mobileSession.accountRecords,
        mobileSession.payeeRecords,
        mobileSession.categoryRecords,
        mobileSession.budgetDisplaySettings,
    ) {
        if (!isLiveBudget) {
            searchableRecords = previewTransactions.map(TransactionItem::toActivitySearchRecord)
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else if (mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId) {
            searchableRecords = emptyList()
            loadedSearchBudgetId = ""
            isBuildingSearchRecords = false
        } else {
            val records = mobileSession.activitySearchRecords
            val accountRecords = mobileSession.accountRecords
            val payeeRecords = mobileSession.payeeRecords
            val categoryRecords = mobileSession.categoryRecords
            val displaySettings = mobileSession.budgetDisplaySettings
            val currentCache = state.activitySearchCache
            if (
                currentCache?.matches(
                    state.selectedBudgetId,
                    records,
                    accountRecords,
                    payeeRecords,
                    categoryRecords,
                    displaySettings,
                ) == true
            ) {
                searchableRecords = currentCache.searchableRecords
                loadedSearchBudgetId = state.selectedBudgetId
                isBuildingSearchRecords = false
            } else {
                isBuildingSearchRecords = true
                val indexed =
                    withContext(Dispatchers.Default) {
                        buildSearchActivityRecords(
                            records = records,
                            accounts =
                                accountRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                            payees =
                                payeeRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                            categories =
                                categoryRecords
                                    .mapNotNull { record ->
                                        record.recordName()?.let { record.recordId to it }
                                    }.toMap(),
                        )
                    }
                state.activitySearchCache =
                    ActivitySearchCache(
                        state.selectedBudgetId,
                        records,
                        accountRecords,
                        payeeRecords,
                        categoryRecords,
                        displaySettings,
                        indexed,
                    )
                searchableRecords = indexed
                loadedSearchBudgetId = state.selectedBudgetId
                isBuildingSearchRecords = false
            }
        }
    }
    val quickFilters =
        remember(searchableRecords) {
            buildList {
                addAll(recentActivitySearchMonthFilters(searchableRecords))
                if (searchableRecords.any { it.cleared != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.CLEARED, "", "Cleared"))
                    add(ActivitySearchFilter(ActivitySearchField.UNCLEARED, "", "Uncleared"))
                }
                if (searchableRecords.any { it.reconciled != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.RECONCILED, "", "Reconciled"))
                    add(ActivitySearchFilter(ActivitySearchField.UNRECONCILED, "", "Unreconciled"))
                }
                if (searchableRecords.any { it.approved != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.APPROVED, "", "Approved"))
                    add(ActivitySearchFilter(ActivitySearchField.UNAPPROVED, "", "Unapproved"))
                }
                if (searchableRecords.any { it.matched != null }) {
                    add(ActivitySearchFilter(ActivitySearchField.MATCHED, "", "Matched"))
                    add(ActivitySearchFilter(ActivitySearchField.UNMATCHED, "", "Unmatched"))
                }
                if (searchableRecords.any { it.transfer }) add(ActivitySearchFilter(ActivitySearchField.TRANSFER, "", "Transfer"))
                searchableRecords
                    .asSequence()
                    .flatMap { it.tagNames.asSequence() }
                    .distinct()
                    .sortedBy(String::lowercase)
                    .take(4)
                    .forEach { tag -> add(ActivitySearchFilter(ActivitySearchField.TAG, tag, "Tag: #$tag")) }
            }
        }
    var suggestions by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.suggestions ?: emptyList())
    }
    var resultIndices by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.indices ?: emptyList())
    }
    var isEvaluatingSearch by remember(state.selectedBudgetId) { mutableStateOf(cachedEvaluation == null) }
    LaunchedEffect(state.selectedBudgetId, search, filters, searchableRecords, quickFilters, state.budgetDisplaySettings) {
        val currentEvaluation = state.activitySearchResultCache
        if (
            currentEvaluation?.budgetId == state.selectedBudgetId &&
            currentEvaluation.query == search &&
            currentEvaluation.filters == filters &&
            currentEvaluation.sourceRecords === searchableRecords
        ) {
            resultIndices = currentEvaluation.indices
            suggestions = currentEvaluation.suggestions
            isEvaluatingSearch = false
            return@LaunchedEffect
        }
        isEvaluatingSearch = true
        if (search.isNotBlank()) delay(150)
        val displaySettings = state.budgetDisplaySettings
        val evaluation =
            withContext(Dispatchers.Default) {
                val preparedSearch = prepareActivitySearch(search, filters, displaySettings?.currencyCode)
                val matchedIndices =
                    if (search.isBlank() && filters.isEmpty()) {
                        searchableRecords.indices.toList()
                    } else {
                        searchableRecords.mapIndexedNotNull { index, record ->
                            index.takeIf { matchesActivitySearch(record, preparedSearch) }
                        }
                    }
                val refinements =
                    if (search.isBlank()) {
                        quickFilters
                    } else {
                        activitySearchSuggestions(search, searchableRecords, displaySettings?.currencyCode) {
                            formatCurrency(it, displaySettings)
                        }
                    }
                matchedIndices to refinements
            }
        state.activitySearchResultCache =
            ActivitySearchResultCache(
                state.selectedBudgetId,
                search,
                filters,
                searchableRecords,
                evaluation.first,
                evaluation.second,
            )
        resultIndices = evaluation.first
        suggestions = evaluation.second
        isEvaluatingSearch = false
    }
    var sortedResultIndices by remember(state.selectedBudgetId) {
        mutableStateOf(cachedEvaluation?.indices ?: emptyList())
    }
    val resultsListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    val visibleTransactionIds =
        remember(sortedResultIndices, searchableRecords) {
            sortedResultIndices.mapNotNull { searchableRecords.getOrNull(it)?.recordId }.toSet()
        }
    val selectedTotalMinor =
        remember(searchableRecords, selectedTransactionIds) {
            searchableRecords
                .asSequence()
                .filter { it.recordId in selectedTransactionIds }
                .sumOf(ActivitySearchRecord::amountMinorUnits)
        }
    LaunchedEffect(isLiveBudget, selectionMode, selectedTransactionIds, selectedTotalMinor) {
        onActivitySelectionTotalChanged(
            selectedTotalMinor.takeIf { isLiveBudget && selectionMode && selectedTransactionIds.isNotEmpty() },
        )
    }
    DisposableEffect(state.selectedBudgetId) {
        onDispose { onActivitySelectionTotalChanged(null) }
    }
    LaunchedEffect(search, filters) {
        selectionMode = false
        selectedTransactionIds = emptySet()
    }
    val selectedSortOrder = state.activitySortOrder
    LaunchedEffect(resultIndices, searchableRecords, selectedSortOrder) {
        sortedResultIndices =
            if (selectedSortOrder == ActivitySortOrder.NEWEST_FIRST) {
                resultIndices
            } else {
                withContext(Dispatchers.Default) {
                    sortActivitySearchIndices(resultIndices, searchableRecords, selectedSortOrder)
                }
            }
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            ActivitySearchInput(
                value = search,
                onValueChange = {
                    search = it
                    state.activitySearchQuery = it
                },
                autoFocus = autoFocusSearch,
                focusRequestKey = state.activitySearchFocusRequest,
            )
            if (savedSearches.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    savedSearches.forEach { savedSearch ->
                        SurfaceCard(onClick = {
                            search = savedSearch.query
                            state.activitySearchQuery = savedSearch.query
                            state.activitySearchFilters = savedSearch.filters
                        }) {
                            Row(
                                Modifier.widthIn(min = 132.dp, max = 220.dp).padding(start = TandemSpacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = TandemSpacing.small)) {
                                    Text(
                                        savedSearch.name,
                                        color = tandemPalette.text,
                                        fontSize = TandemTypography.caption,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${savedSearch.filters.size} filters",
                                        color = tandemPalette.muted,
                                        fontSize = TandemTypography.caption,
                                        maxLines = 1,
                                    )
                                }
                                TandemIconButton(
                                    Lucide.X,
                                    "Remove ${savedSearch.name}",
                                    sizeDp = 36,
                                    iconSizeDp = 16,
                                ) {
                                    state.savedActivitySearchesJson =
                                        encodeSavedActivitySearches(
                                            decodeSavedActivitySearches(state.savedActivitySearchesJson) - savedSearch,
                                        )
                                }
                            }
                        }
                    }
                }
            }
            if (search.isNotBlank() || filters.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = {
                            savedSearchNameDraft =
                                search.trim().ifBlank {
                                    filters.joinToString(" · ") { it.label }.take(80)
                                }
                            showSaveSearchDialog = true
                        },
                    ) {
                        Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.accent)
                        Spacer(Modifier.width(TandemSpacing.extraSmall))
                        Text("Save search", color = tandemPalette.accent, fontSize = TandemTypography.caption)
                    }
                }
            }
            if (filters.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    filters.forEach { filter ->
                        ActivitySearchFilterChip(filter) { state.activitySearchFilters = filters - filter }
                    }
                }
            }
            val shownSuggestions =
                if (search.isBlank()) {
                    quickFilters
                } else {
                    suggestions.filterNot { it.label.equals(search.trim(), ignoreCase = true) }
                }
            if (shownSuggestions.isNotEmpty()) {
                Text(
                    if (search.isBlank()) "Quick filters" else "Suggestions",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.caption,
                    fontWeight = FontWeight.SemiBold,
                )
                if (search.isBlank()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        shownSuggestions.take(8).forEach { suggestion ->
                            val isActive = isActivitySearchFilterActive(state.activitySearchFilters, suggestion)
                            ActivitySearchQuickFilter(suggestion, isActive) {
                                state.activitySearchFilters =
                                    toggleActivitySearchFilter(state.activitySearchFilters, suggestion)
                            }
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 176.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall),
                    ) {
                        shownSuggestions.take(8).forEach { suggestion ->
                            ActivitySearchSuggestion(suggestion) {
                                state.activitySearchFilters =
                                    toggleActivitySearchFilter(state.activitySearchFilters, suggestion)
                                search = ""
                                state.activitySearchQuery = ""
                            }
                        }
                    }
                }
            }
            if (LocalDensity.current.fontScale >= 1.3f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (hasActiveSearch) "Results" else "All transactions",
                            color = tandemPalette.text,
                            fontSize = TandemTypography.cardTitle,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            formatInteger(resultIndices.size),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (hasActiveSearch) "Results" else "All transactions",
                            color = tandemPalette.text,
                            fontSize = TandemTypography.cardTitle,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            formatInteger(resultIndices.size),
                            color = tandemPalette.muted,
                            fontSize = TandemTypography.caption,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
                }
            }
            if (selectionMode) {
                mobileSession.transactionWriteMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
            }
            if (
                isLiveBudget &&
                (
                    mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId ||
                        loadedSearchBudgetId != state.selectedBudgetId ||
                        isBuildingSearchRecords
                )
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tandemPalette.accent)
                }
            } else if (isEvaluatingSearch && resultIndices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tandemPalette.accent)
                }
            } else if (resultIndices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        if (filters.isEmpty() &&
                            search.isBlank()
                        ) {
                            "No transactions yet"
                        } else {
                            "No matching transactions"
                        },
                        modifier = Modifier.padding(TandemSpacing.large),
                    )
                }
            } else {
                LazyColumn(
                    state = resultsListState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = if (selectionMode) 88.dp else 0.dp),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    itemsIndexed(
                        sortedResultIndices,
                        key = { _, index ->
                            val record = searchableRecords.getOrNull(index)
                            "search:${record?.recordId ?: index}"
                        },
                    ) { position, index ->
                        searchableRecords.getOrNull(index)?.let { record ->
                            val previousDate =
                                sortedResultIndices
                                    .getOrNull(position - 1)
                                    ?.let(searchableRecords::getOrNull)
                                    ?.dateIso
                            val transaction =
                                remember(record, state.budgetDisplaySettings, merchantIconMatcher) {
                                    record.toTransactionItem(state.budgetDisplaySettings, merchantIconMatcher)
                                }
                            val transactionId = transaction.recordId
                            TransactionRow(
                                transaction,
                                selectionMode = selectionMode,
                                selected = transactionId != null && transactionId in selectedTransactionIds,
                                showDateHeader = previousDate != record.dateIso,
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
            }
        }
        AnimatedVisibility(
            visible = isLiveBudget && selectionMode,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = tandemFloatingEnter(tandemReduceMotion),
            exit = tandemFloatingExit(tandemReduceMotion),
        ) {
            val allSelected = visibleTransactionIds.isNotEmpty() && selectedTransactionIds.containsAll(visibleTransactionIds)
            TransactionSelectionFloatingBar(
                allSelected = allSelected,
                canDelete = selectedTransactionIds.isNotEmpty() && !isDeletingSelection,
                isWorking = isDeletingSelection,
                onToggleAll = {
                    selectedTransactionIds = if (allSelected) emptySet() else visibleTransactionIds
                    if (allSelected) selectionMode = false
                },
                onDelete = { confirmBulkDelete = true },
                modifier = Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
            )
        }
    }
    TandemTaskSheet(
        visible = showSaveSearchDialog,
        onDismissRequest = { showSaveSearchDialog = false },
        onDismissed = {},
    ) {
        SurfaceCard(Modifier.fillMaxWidth().widthIn(max = 400.dp).padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier.fillMaxWidth().padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
            ) {
                Heading("Save search")
                TextFieldCard(
                    "Name",
                    savedSearchNameDraft,
                    { savedSearchNameDraft = it.take(80) },
                    TextFieldOptions(placeholder = "Name this search"),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                ) {
                    TandemActionButton(
                        "Cancel",
                        TandemActionButtonTone.NEUTRAL,
                        modifier = Modifier.weight(1f),
                        onClick = { showSaveSearchDialog = false },
                    )
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        modifier = Modifier.weight(1f),
                        enabled = savedSearchNameDraft.isNotBlank(),
                        onClick = {
                            val entry =
                                SavedActivitySearch(
                                    budgetId = state.selectedBudgetId,
                                    name = savedSearchNameDraft.trim(),
                                    query = search,
                                    filters = filters,
                                )
                            val updated =
                                decodeSavedActivitySearches(state.savedActivitySearchesJson)
                                    .filterNot { it.budgetId == entry.budgetId && it.name.equals(entry.name, ignoreCase = true) } +
                                    entry
                            state.savedActivitySearchesJson = encodeSavedActivitySearches(updated)
                            showSaveSearchDialog = false
                        },
                    )
                }
            }
        }
    }
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
