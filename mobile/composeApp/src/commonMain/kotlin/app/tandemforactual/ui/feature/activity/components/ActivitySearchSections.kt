@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.activity.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.SavedSearch
import app.tandemforactual.ui.core.format.formatInteger
import app.tandemforactual.ui.core.search.ActivitySearchFilter
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.activity.ActivitySearchFilterChip
import app.tandemforactual.ui.feature.activity.ActivitySearchQuickFilter
import app.tandemforactual.ui.feature.activity.ActivitySearchSuggestion
import app.tandemforactual.ui.feature.activity.ActivitySortControl
import app.tandemforactual.ui.feature.activity.activityFilters
import app.tandemforactual.ui.feature.activity.isActivitySearchFilterActive
import app.tandemforactual.ui.feature.activity.summary
import app.tandemforactual.ui.feature.activity.toggleActivitySearchFilter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The saved searches row, and a "Save search" button while a query or filter is active. */
@Composable
internal fun ActivitySavedSearchControls(
    state: AppState,
    mobileSession: TandemMobileSession,
    scope: CoroutineScope,
    search: String,
    saveSearch: SaveActivitySearchState,
    onSearchChange: (String) -> Unit,
) {
    val savedSearches = mobileSession.planning.savedSearches
    val filters = state.activitySearchFilters
    if (savedSearches.isNotEmpty()) {
        ActivitySavedSearchesRow(
            savedSearches,
            onApply = { savedSearch ->
                onSearchChange(savedSearch.query)
                state.activitySearchFilters = savedSearch.activityFilters()
            },
            onRemove = { savedSearch ->
                scope.launch { mobileSession.planning.deleteSavedSearch(state.selectedBudgetId, savedSearch.id) }
            },
        )
    }
    if (search.isNotBlank() || filters.isNotEmpty()) {
        ActivitySaveSearchButton {
            saveSearch.savedSearchNameDraft = search.trim().ifBlank { filters.joinToString(" · ") { it.label }.take(80) }
            saveSearch.showSaveSearchDialog = true
        }
    }
}

/** Saved searches as a scrolling row of cards; tapping one applies it. */
@Composable
private fun ActivitySavedSearchesRow(
    savedSearches: List<SavedSearch>,
    onApply: (SavedSearch) -> Unit,
    onRemove: (SavedSearch) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        savedSearches.forEach { savedSearch ->
            SurfaceCard(onClick = { onApply(savedSearch) }) {
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
                            savedSearch.summary(),
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
                        onRemove(savedSearch)
                    }
                }
            }
        }
    }
}

/** The "Save search" button shown while a query or filter is active. */
@Composable
private fun ActivitySaveSearchButton(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) {
            Icon(Lucide.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.accent)
            Spacer(Modifier.width(TandemSpacing.extraSmall))
            Text("Save search", color = tandemPalette.accent, fontSize = TandemTypography.caption)
        }
    }
}

/** The active filters as removable chips. */
@Composable
internal fun ActivitySearchFilterChips(
    filters: List<ActivitySearchFilter>,
    onRemove: (ActivitySearchFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small),
    ) {
        filters.forEach { filter ->
            ActivitySearchFilterChip(filter) { onRemove(filter) }
        }
    }
}

/** Quick filters before anything is typed, or suggestions that match the query. */
@Composable
internal fun ActivitySearchSuggestions(
    state: AppState,
    search: String,
    quickFilters: List<ActivitySearchFilter>,
    suggestions: List<ActivitySearchFilter>,
    onSuggestionApplied: () -> Unit,
) {
    val shownSuggestions =
        if (search.isBlank()) {
            quickFilters
        } else {
            suggestions.filterNot { it.label.equals(search.trim(), ignoreCase = true) }
        }
    if (shownSuggestions.isEmpty()) return
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
                    onSuggestionApplied()
                }
            }
        }
    }
}

/** The results title and count with the sort control, stacked at large font scales. */
@Composable
internal fun ActivitySearchResultsHeader(
    state: AppState,
    hasActiveSearch: Boolean,
    resultCount: Int,
) {
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
                ActivitySearchResultsTitle(hasActiveSearch, resultCount)
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
                ActivitySearchResultsTitle(hasActiveSearch, resultCount)
            }
            ActivitySortControl(state.activitySortOrder) { state.activitySortOrder = it }
        }
    }
}

@Composable
private fun ActivitySearchResultsTitle(
    hasActiveSearch: Boolean,
    resultCount: Int,
) {
    Text(
        if (hasActiveSearch) "Results" else "All transactions",
        color = tandemPalette.text,
        fontSize = TandemTypography.cardTitle,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        formatInteger(resultCount),
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
        maxLines = 1,
        softWrap = false,
    )
}
