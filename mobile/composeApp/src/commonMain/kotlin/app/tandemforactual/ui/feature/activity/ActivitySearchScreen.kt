@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.navigation.openTransaction
import app.tandemforactual.ui.core.search.ActivitySearchRecord
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LocalRouteLazyListState
import app.tandemforactual.ui.core.state.MerchantIconMatcherPack
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.SkeletonRows
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemFloatingEnter
import app.tandemforactual.ui.design.theme.tandemFloatingExit
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import app.tandemforactual.ui.feature.activity.components.ActivitySavedSearchControls
import app.tandemforactual.ui.feature.activity.components.ActivitySearchEvaluationState
import app.tandemforactual.ui.feature.activity.components.ActivitySearchFilterChips
import app.tandemforactual.ui.feature.activity.components.ActivitySearchIndexState
import app.tandemforactual.ui.feature.activity.components.ActivitySearchResultsHeader
import app.tandemforactual.ui.feature.activity.components.ActivitySearchSelectionTotalEffect
import app.tandemforactual.ui.feature.activity.components.ActivitySearchSuggestions
import app.tandemforactual.ui.feature.activity.components.ActivitySelectionState
import app.tandemforactual.ui.feature.activity.components.SaveActivitySearchSheet
import app.tandemforactual.ui.feature.activity.components.rememberActivityQuickFilters
import app.tandemforactual.ui.feature.activity.components.rememberActivitySearchEvaluation
import app.tandemforactual.ui.feature.activity.components.rememberActivitySearchIndex
import app.tandemforactual.ui.feature.activity.components.rememberSaveActivitySearchState
import app.tandemforactual.ui.feature.transactions.TransactionRow
import app.tandemforactual.ui.feature.transactions.TransactionSelectionFloatingBar
import kotlinx.coroutines.launch

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
    val savedSearches = mobileSession.planning.savedSearches
    LegacySavedSearchMigrationEffect(state, mobileSession)
    val saveSearch = rememberSaveActivitySearchState()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val selection = remember(state.selectedBudgetId) { ActivitySelectionState() }
    val filters = state.activitySearchFilters
    val cachedSearch = state.activitySearchCache?.takeIf { it.budgetId == state.selectedBudgetId }
    val cachedEvaluation =
        state.activitySearchResultCache?.takeIf { cached ->
            cached.budgetId == state.selectedBudgetId &&
                cached.query == search &&
                cached.filters == filters &&
                cached.sourceRecords === cachedSearch?.searchableRecords
        }
    val index = rememberActivitySearchIndex(state, mobileSession, isLiveBudget, cachedSearch)
    val searchableRecords = index.searchableRecords
    val quickFilters = rememberActivityQuickFilters(searchableRecords)
    val evaluation = rememberActivitySearchEvaluation(state, search, filters, searchableRecords, quickFilters, cachedEvaluation)
    ActivitySearchSelectionTotalEffect(state.selectedBudgetId, isLiveBudget, selection, searchableRecords, onActivitySelectionTotalChanged)
    LaunchedEffect(search, filters) {
        selection.selectionMode = false
        selection.selectedTransactionIds = emptySet()
    }
    val resultsListState = LocalRouteLazyListState.current ?: rememberLazyListState()
    val onSearchChange: (String) -> Unit = {
        search = it
        state.activitySearchQuery = it
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = TandemSpacing.screen, vertical = TandemSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
        ) {
            ActivitySearchInput(
                value = search,
                onValueChange = onSearchChange,
                autoFocus = autoFocusSearch,
                focusRequestKey = state.activitySearchFocusRequest,
            )
            ActivitySavedSearchControls(state, mobileSession, scope, search, saveSearch, onSearchChange)
            if (filters.isNotEmpty()) ActivitySearchFilterChips(filters) { state.activitySearchFilters = filters - it }
            ActivitySearchSuggestions(state, search, quickFilters, evaluation.suggestions) { onSearchChange("") }
            ActivitySearchResultsHeader(state, search.isNotBlank() || filters.isNotEmpty(), evaluation.resultIndices.size)
            if (selection.selectionMode) {
                mobileSession.transactionWriteMessage?.let { InlineMessage(it, InlineMessageKind.ERROR) }
            }
            ActivitySearchResults(
                state,
                mobileSession,
                navigation,
                merchantIconMatcher,
                search,
                index,
                evaluation,
                selection,
                resultsListState,
            )
        }
        ActivitySearchSelectionBar(isLiveBudget, selection, evaluation.sortedResultIndices, searchableRecords)
    }
    SaveActivitySearchSheet(saveSearch) { name ->
        val id = savedSearches.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: newSavedSearchId()
        val entry = SavedActivitySearch(state.selectedBudgetId, name, search, filters).toSavedSearch(id)
        scope.launch { mobileSession.planning.saveSearch(state.selectedBudgetId, entry) }
    }
    ActivityBulkDeleteEffect(mobileSession, state.selectedBudgetId, selection)
}

/** Moves searches saved by older versions in app settings into the budget's planning store. */
@Composable
private fun LegacySavedSearchMigrationEffect(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    LaunchedEffect(state.selectedBudgetId, state.savedActivitySearchesJson, mobileSession.databaseGeneration) {
        val legacy = decodeSavedActivitySearches(state.savedActivitySearchesJson)
        val moving = legacy.filter { it.budgetId == state.selectedBudgetId }
        if (moving.isEmpty() || state.selectedBudgetId.isBlank()) return@LaunchedEffect
        if (moving.all { mobileSession.planning.saveSearch(state.selectedBudgetId, it.toSavedSearch(newSavedSearchId())) }) {
            state.savedActivitySearchesJson = encodeSavedActivitySearches(legacy - moving.toSet())
        }
    }
}

/** A skeleton while indexing or searching, an empty state, or the matching transactions. */
@Composable
private fun ColumnScope.ActivitySearchResults(
    state: AppState,
    mobileSession: TandemMobileSession,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
    search: String,
    index: ActivitySearchIndexState,
    evaluation: ActivitySearchEvaluationState,
    selection: ActivitySelectionState,
    resultsListState: LazyListState,
) {
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    val filters = state.activitySearchFilters
    if (
        isLiveBudget &&
        (
            mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId ||
                index.loadedSearchBudgetId != state.selectedBudgetId ||
                index.isBuildingSearchRecords
        )
    ) {
        SkeletonRows(modifier = Modifier.weight(1f))
    } else if (evaluation.isEvaluatingSearch && evaluation.resultIndices.isEmpty()) {
        SkeletonRows(modifier = Modifier.weight(1f))
    } else if (evaluation.resultIndices.isEmpty()) {
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
        ActivitySearchResultList(
            state,
            navigation,
            merchantIconMatcher,
            evaluation.sortedResultIndices,
            index.searchableRecords,
            selection,
            resultsListState,
        )
    }
}

/** The matching transactions with date headers, selection and opening. */
@Composable
private fun ColumnScope.ActivitySearchResultList(
    state: AppState,
    navigation: AppNavigation,
    merchantIconMatcher: MerchantIconMatcherPack?,
    sortedResultIndices: List<Int>,
    searchableRecords: List<ActivitySearchRecord>,
    selection: ActivitySelectionState,
    resultsListState: LazyListState,
) {
    val todayIso = platformTodayIsoDate()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    LazyColumn(
        state = resultsListState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = if (selection.selectionMode) 88.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
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
                    remember(record, state.budgetDisplaySettings, merchantIconMatcher, todayIso) {
                        record.toTransactionItem(state.budgetDisplaySettings, merchantIconMatcher, todayIso)
                    }
                val transactionId = transaction.recordId
                TransactionRow(
                    transaction,
                    activityStyle = true,
                    selectionMode = selection.selectionMode,
                    selected = transactionId != null && transactionId in selection.selectedTransactionIds,
                    showDateHeader = previousDate != record.dateIso,
                    showAccount = true,
                    showDivider = true,
                    onLongClick =
                        if (isLiveBudget && !state.readOnlyMode && transactionId != null) {
                            {
                                selection.selectionMode = true
                                selection.selectedTransactionIds = selection.selectedTransactionIds + transactionId
                            }
                        } else {
                            null
                        },
                ) {
                    if (selection.selectionMode && transactionId != null) {
                        selection.toggle(transactionId)
                    } else {
                        openTransaction(state, navigation, transaction, isLive = isLiveBudget)
                    }
                }
            }
        }
    }
}

/** The floating bar for selecting every visible result or deleting the selection. */
@Composable
private fun BoxScope.ActivitySearchSelectionBar(
    isLiveBudget: Boolean,
    selection: ActivitySelectionState,
    sortedResultIndices: List<Int>,
    searchableRecords: List<ActivitySearchRecord>,
) {
    val visibleTransactionIds =
        remember(sortedResultIndices, searchableRecords) {
            sortedResultIndices.mapNotNull { searchableRecords.getOrNull(it)?.recordId }.toSet()
        }
    AnimatedVisibility(
        visible = isLiveBudget && selection.selectionMode,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = tandemFloatingEnter(tandemReduceMotion),
        exit = tandemFloatingExit(tandemReduceMotion),
    ) {
        val allSelected = visibleTransactionIds.isNotEmpty() && selection.selectedTransactionIds.containsAll(visibleTransactionIds)
        TransactionSelectionFloatingBar(
            allSelected = allSelected,
            canDelete = selection.selectedTransactionIds.isNotEmpty() && !selection.isDeletingSelection,
            isWorking = selection.isDeletingSelection,
            onToggleAll = {
                selection.selectedTransactionIds = if (allSelected) emptySet() else visibleTransactionIds
                if (allSelected) selection.selectionMode = false
            },
            onDelete = { selection.confirmBulkDelete = true },
            modifier = Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium),
        )
    }
}
