@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.PeriodStepper
import app.tandemforactual.ui.components.SecondaryButton
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import kotlinx.coroutines.flow.first

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun SelectorScreen(
    values: List<SelectorItem>,
    selected: String,
    live: Boolean = false,
    topContent: (@Composable () -> Unit)? = null,
    noMatches: (@Composable (String) -> Unit)? = null,
    groups: List<SelectorGroup> = emptyList(),
    searchLabel: String = "Search items",
    searchPlaceholder: String = "Find an item",
    onSelect: (SelectorItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
    val filteredIds = filtered.mapTo(mutableSetOf(), SelectorItem::id)
    val groupedItems =
        groups.mapNotNull { group ->
            val items = group.items.filter { it.id in filteredIds }
            if (items.isEmpty()) null else group to items
        }
    val groupedIds = groupedItems.flatMapTo(mutableSetOf()) { (_, items) -> items.map(SelectorItem::id) }
    val ungroupedItems = filtered.filterNot { it.id in groupedIds }
    PageLazyColumn {
        if (values.size > 8) {
            item {
                TextFieldCard(searchLabel, query, { query = it }, TextFieldOptions(placeholder = searchPlaceholder))
            }
        }
        topContent?.let { content -> item { content() } }
        if (filtered.isEmpty()) {
            item {
                if (noMatches != null) {
                    noMatches(query)
                } else {
                    SurfaceCard { EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large)) }
                }
            }
        } else {
            items(ungroupedItems, key = { "choice-${it.id}" }) { item ->
                ChoiceRow(
                    item.label,
                    selected = selected.isNotBlank() && selected == if (live) item.id else item.label,
                    onClick = { onSelect(item) },
                )
            }
            groupedItems.forEach { (group, groupItems) ->
                stickyHeader(key = "selector-group-${group.title}") {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text(
                            group.title,
                            modifier = Modifier.padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.small),
                            color = tandemPalette.text,
                            fontSize = TandemTypography.label,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                items(groupItems, key = { "${group.title}-${it.id}" }) { item ->
                    ChoiceRow(
                        item.label,
                        selected = selected.isNotBlank() && selected == if (live) item.id else item.label,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DatePickerScreen(
    state: AppState,
    navigation: AppNavigation,
) {
    val selectedDateParts = parseIsoDate(state.transactionDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 5, 1)
    var visibleYear by remember { mutableStateOf(selectedDateParts.first) }
    var visibleMonth by remember { mutableStateOf(selectedDateParts.second) }
    val firstWeekday = platformFirstWeekday()
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - firstWeekday + 7) % 7
    val daysInVisibleMonth = daysInMonth(visibleYear, visibleMonth)
    val dayCells = List<Int?>(firstDayOffset) { null } + (1..daysInVisibleMonth).map { it }
    PageColumn {
        PeriodStepper(
            title = formatMonth(visibleYear, visibleMonth),
            previousLabel = "Previous month",
            nextLabel = "Next month",
            onPrevious = {
                if (visibleMonth == 1) {
                    visibleMonth = 12
                    visibleYear -= 1
                } else {
                    visibleMonth -= 1
                }
            },
            onNext = {
                if (visibleMonth == 12) {
                    visibleMonth = 1
                    visibleYear += 1
                } else {
                    visibleMonth += 1
                }
            },
        )
        SecondaryButton("Today", Modifier.fillMaxWidth()) {
            state.transactionDate = platformTodayIsoDate()
            navigation.back()
        }
        Row(Modifier.fillMaxWidth()) {
            (0..6).forEach { column ->
                val representativeDay = 1 + ((column - firstDayOffset + 7) % 7)
                Text(
                    formatWeekday(visibleYear, visibleMonth, representativeDay),
                    modifier = Modifier.weight(1f),
                    color = tandemPalette.muted,
                    textAlign = TextAlign.Center,
                    fontSize = TandemTypography.caption,
                )
            }
        }
        dayCells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    } else {
                        val isoDate = transactionIsoDate(visibleYear, visibleMonth, day)
                        val selected = state.transactionDate == isoDate
                        val cellTextColor by
                            animateColorAsState(
                                targetValue = if (selected) tandemPalette.accent else tandemPalette.text,
                                animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
                                label = "date-selection-text",
                            )
                        SelectableTile(
                            selected = selected,
                            contentDescription = displayTransactionDate(isoDate),
                            onSelect = {
                                state.transactionDate = isoDate
                                navigation.back()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            shape = CircleShape,
                            selectedBackground = tandemPalette.accentSurface,
                            unselectedBackground = Color.Transparent,
                        ) {
                            Text(
                                day.toString(),
                                color = cellTextColor,
                                fontSize = TandemTypography.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
            }
        }
    }
}

internal fun currentMonthId(): String = platformTodayIsoDate().take(7).takeIf { it.length == 7 } ?: "2026-05"

internal fun currentMonthLabel(): String {
    val parts = currentMonthId().split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: 5
    return formatMonth(year, month)
}

internal fun parseIsoDate(value: String): Triple<Int, Int, Int>? {
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return Triple(year, month, day).takeIf { year > 0 && month in 1..12 && day in 1..daysInMonth(year, month) }
}

internal fun daysInMonth(
    year: Int,
    month: Int,
): Int =
    when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

internal fun transactionIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

internal fun displayTransactionDate(value: String): String =
    parseIsoDate(value)?.let { (year, month, day) -> formatDate(SampleDate(year, month, day)) } ?: value
