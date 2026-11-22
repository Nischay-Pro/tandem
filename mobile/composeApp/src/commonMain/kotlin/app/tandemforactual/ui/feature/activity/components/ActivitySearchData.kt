@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.search.ActivitySearchField
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.search.ActivitySearchRecord
import app.tandemforactual.ui.core.search.ActivitySortOrder
import app.tandemforactual.ui.core.search.matchesActivitySearch
import app.tandemforactual.ui.core.search.prepareActivitySearch
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.observeActivitySearchRecords
import app.tandemforactual.ui.core.state.ActivitySearchCache
import app.tandemforactual.ui.core.state.ActivitySearchResultCache
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.feature.activity.activitySearchSuggestions
import app.tandemforactual.ui.feature.activity.buildSearchActivityRecords
import app.tandemforactual.ui.feature.activity.recentActivitySearchMonthFilters
import app.tandemforactual.ui.feature.activity.sortActivitySearchIndices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The budget's transactions indexed for search, and whether that index is still being built. */
internal class ActivitySearchIndexState(
    cachedSearch: ActivitySearchCache?,
    isLiveBudget: Boolean,
) {
    var searchableRecords by mutableStateOf(cachedSearch?.searchableRecords ?: emptyList())
    var loadedSearchBudgetId by mutableStateOf(cachedSearch?.budgetId ?: "")
    var isBuildingSearchRecords by mutableStateOf(isLiveBudget && cachedSearch == null)

    fun clear() {
        searchableRecords = emptyList()
        loadedSearchBudgetId = ""
        isBuildingSearchRecords = false
    }

    suspend fun refresh(
        state: AppState,
        mobileSession: TandemMobileSession,
    ) {
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

/** Observes the budget's transactions and keeps them indexed for search, reusing the cached index when it still matches. */
@Composable
internal fun rememberActivitySearchIndex(
    state: AppState,
    mobileSession: TandemMobileSession,
    isLiveBudget: Boolean,
    cachedSearch: ActivitySearchCache?,
): ActivitySearchIndexState {
    LaunchedEffect(state.selectedBudgetId, mobileSession, mobileSession.databaseGeneration) {
        if (isLiveBudget) mobileSession.observeActivitySearchRecords(state.selectedBudgetId)
    }
    val index = remember(state.selectedBudgetId) { ActivitySearchIndexState(cachedSearch, isLiveBudget) }
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
            index.clear()
        } else if (mobileSession.activitySearchRecordsBudgetId != state.selectedBudgetId) {
            index.clear()
        } else {
            index.refresh(state, mobileSession)
        }
    }
    return index
}

/** Month, status, transfer and tag filters offered before anything is typed. */
@Composable
internal fun rememberActivityQuickFilters(searchableRecords: List<ActivitySearchRecord>): List<ActivitySearchFilter> =
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

/** The matching result indices, in list order and in the chosen sort, plus refinement suggestions. */
internal class ActivitySearchEvaluationState(
    cachedEvaluation: ActivitySearchResultCache?,
) {
    var suggestions by mutableStateOf(cachedEvaluation?.suggestions ?: emptyList())
    var resultIndices by mutableStateOf(cachedEvaluation?.indices ?: emptyList())
    var isEvaluatingSearch by mutableStateOf(cachedEvaluation == null)
    var sortedResultIndices by mutableStateOf(cachedEvaluation?.indices ?: emptyList())

    suspend fun evaluate(
        state: AppState,
        search: String,
        filters: List<ActivitySearchFilter>,
        searchableRecords: List<ActivitySearchRecord>,
        quickFilters: List<ActivitySearchFilter>,
    ) {
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
            return
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
}

/** Runs the search over the index whenever the query, filters or index change, then sorts the results. */
@Composable
internal fun rememberActivitySearchEvaluation(
    state: AppState,
    search: String,
    filters: List<ActivitySearchFilter>,
    searchableRecords: List<ActivitySearchRecord>,
    quickFilters: List<ActivitySearchFilter>,
    cachedEvaluation: ActivitySearchResultCache?,
): ActivitySearchEvaluationState {
    val evaluation = remember(state.selectedBudgetId) { ActivitySearchEvaluationState(cachedEvaluation) }
    LaunchedEffect(state.selectedBudgetId, search, filters, searchableRecords, quickFilters, state.budgetDisplaySettings) {
        evaluation.evaluate(state, search, filters, searchableRecords, quickFilters)
    }
    val selectedSortOrder = state.activitySortOrder
    val resultIndices = evaluation.resultIndices
    LaunchedEffect(resultIndices, searchableRecords, selectedSortOrder) {
        evaluation.sortedResultIndices =
            if (selectedSortOrder == ActivitySortOrder.NEWEST_FIRST) {
                resultIndices
            } else {
                withContext(Dispatchers.Default) {
                    sortActivitySearchIndices(resultIndices, searchableRecords, selectedSortOrder)
                }
            }
    }
    return evaluation
}

/** Reports the selected results' total to the shell while selecting, and clears it on leaving. */
@Composable
internal fun ActivitySearchSelectionTotalEffect(
    budgetId: String,
    isLiveBudget: Boolean,
    selection: ActivitySelectionState,
    searchableRecords: List<ActivitySearchRecord>,
    onActivitySelectionTotalChanged: (Long?) -> Unit,
) {
    val selectedTotalMinor =
        remember(searchableRecords, selection.selectedTransactionIds) {
            searchableRecords
                .asSequence()
                .filter { it.recordId in selection.selectedTransactionIds }
                .sumOf(ActivitySearchRecord::amountMinorUnits)
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
