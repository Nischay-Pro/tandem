@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.components.EmptyState
import app.tandemforactual.ui.components.PeriodStepper
import app.tandemforactual.ui.components.SelectableTile
import app.tandemforactual.ui.components.SurfaceCard
import com.composables.icons.lucide.Search
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun SelectorScreen(
    values: List<SelectorItem>,
    selected: String,
    live: Boolean = false,
    topContent: (@Composable () -> Unit)? = null,
    noMatches: (@Composable (String) -> Unit)? = null,
    onSelect: (SelectorItem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    PageColumn {
        TextFieldCard("Search", query, { query = it }, TextFieldOptions(placeholder = "Start typing"))
        topContent?.invoke()
        val filtered = values.filter { it.label.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            if (noMatches != null) {
                noMatches(query)
            } else {
                SurfaceCard { EmptyState("No matches", modifier = Modifier.padding(TandemSpacing.large)) }
            }
        }
        filtered.forEach { item ->
            ChoiceRow(
                item.label,
                selected = selected == if (live) item.id else item.label,
                onClick = { onSelect(item) },
            )
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
                            modifier = Modifier.weight(1f).height(44.dp),
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
