@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatWeekday
import app.tandemforactual.ui.core.format.platformFirstWeekday
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.platformWeekday
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import kotlinx.coroutines.flow.first

@Composable
internal fun TransactionDatePickerDialog(
    visible: Boolean,
    selectedDate: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedDateParts = parseIsoDate(selectedDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 5, 1)
    var visibleYear by remember(selectedDate) { mutableStateOf(selectedDateParts.first) }
    var visibleMonth by remember(selectedDate) { mutableStateOf(selectedDateParts.second) }
    val firstWeekday = platformFirstWeekday()
    val firstDayOffset = (platformWeekday(visibleYear, visibleMonth, 1) - firstWeekday + 7) % 7
    val daysInVisibleMonth = daysInMonth(visibleYear, visibleMonth)
    val dayCells = List<Int?>(firstDayOffset) { null } + (1..daysInVisibleMonth).map { it }
    TandemChoiceDialog(visible, onDismiss) {
        SurfaceCard(Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .heightIn(max = 640.dp)
                    .padding(TandemSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
            ) {
                TandemModalHeader(
                    title = "Choose date",
                    titleSize = TandemTypography.cardTitle,
                    onClose = onDismiss,
                )
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
                SecondaryButton("Today", Modifier.fillMaxWidth()) { onSelect(platformTodayIsoDate()) }
                DatePickerWeekdayRow(visibleYear, visibleMonth, firstDayOffset)
                DatePickerDayGrid(visibleYear, visibleMonth, dayCells, selectedDate, onSelect)
            }
        }
    }
}

/** The weekday initials above the day grid, starting on the platform's first weekday. */
@Composable
private fun DatePickerWeekdayRow(
    year: Int,
    month: Int,
    firstDayOffset: Int,
) {
    Row(Modifier.fillMaxWidth()) {
        (0..6).forEach { column ->
            val representativeDay = 1 + ((column - firstDayOffset + 7) % 7)
            Text(
                formatWeekday(year, month, representativeDay),
                modifier = Modifier.weight(1f),
                color = tandemPalette.muted,
                textAlign = TextAlign.Center,
                fontSize = TandemTypography.caption,
            )
        }
    }
}

/** The weeks of the visible month, one tappable cell per day. */
@Composable
private fun DatePickerDayGrid(
    year: Int,
    month: Int,
    dayCells: List<Int?>,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    dayCells.chunked(7).forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            week.forEach { day ->
                if (day == null) {
                    Spacer(Modifier.weight(1f).height(44.dp))
                } else {
                    DatePickerDayCell(year, month, day, selectedDate, onSelect, Modifier.weight(1f).heightIn(min = 44.dp))
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(44.dp)) }
        }
    }
}

/** One day in the date picker, highlighted when it is the selected date. */
@Composable
private fun DatePickerDayCell(
    year: Int,
    month: Int,
    day: Int,
    selectedDate: String,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    val isoDate = transactionIsoDate(year, month, day)
    val selected = selectedDate == isoDate
    val cellTextColor by
        animateColorAsState(
            targetValue = if (selected) tandemPalette.accent else tandemPalette.text,
            animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
            label = "date-selection-text",
        )
    SelectableTile(
        selected = selected,
        contentDescription = displayTransactionDate(isoDate),
        onSelect = { onSelect(isoDate) },
        modifier = modifier,
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
