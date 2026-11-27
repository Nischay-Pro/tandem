@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.format.displayTransactionDate
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatWeekday
import app.tandemforactual.ui.core.format.parseIsoDate
import app.tandemforactual.ui.core.format.platformFirstWeekday
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.platformWeekday
import app.tandemforactual.ui.core.format.transactionIsoDate
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.MIN_TOUCH_TARGET
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemChoiceDialog
import app.tandemforactual.ui.design.templates.FieldFrame
import app.tandemforactual.ui.design.theme.TandemMotion
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.WindowWidthClass
import app.tandemforactual.ui.design.theme.currentWindowSizeInfo
import app.tandemforactual.ui.design.theme.tandemMotionSpec
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.design.theme.tandemReduceMotion
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X

@Composable
internal fun DatePickerDialog(
    visible: Boolean,
    initialDate: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selected = parseIsoDate(initialDate) ?: parseIsoDate(platformTodayIsoDate()) ?: Triple(2026, 1, 1)
    var visibleYear by rememberSaveable(initialDate) { mutableStateOf(selected.first) }
    var visibleMonth by rememberSaveable(initialDate) { mutableStateOf(selected.second) }
    val month =
        DatePickerMonth(visibleYear, visibleMonth) { step ->
            val index = visibleYear * 12 + visibleMonth - 1 + step
            visibleYear = index / 12
            visibleMonth = index % 12 + 1
        }
    val wideWindow = currentWindowSizeInfo().widthClass >= WindowWidthClass.MEDIUM
    TandemChoiceDialog(visible = visible, onDismissRequest = onDismiss) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            if (wideWindow && maxHeight < DATE_PICKER_STACKED_MIN_HEIGHT) {
                SheetCard(maxWidth = 640.dp, spacing = TandemSpacing.small) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.large)) {
                        Column(Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                            DatePickerHeader(onDismiss)
                            DatePickerStepper(month)
                            SecondaryButton("Today", Modifier.fillMaxWidth()) { onSelect(platformTodayIsoDate()) }
                        }
                        Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
                            DatePickerMonthGrid(month, initialDate, onSelect)
                        }
                    }
                }
            } else {
                SheetCard(
                    spacing = TandemSpacing.small,
                    header = { DatePickerHeader(onDismiss) },
                    actions = { SecondaryButton("Today", Modifier.fillMaxWidth()) { onSelect(platformTodayIsoDate()) } },
                ) {
                    DatePickerStepper(month)
                    DatePickerMonthGrid(month, initialDate, onSelect)
                }
            }
        }
    }
}

private val DATE_PICKER_STACKED_MIN_HEIGHT = 560.dp

private class DatePickerMonth(
    val year: Int,
    val month: Int,
    val step: (Int) -> Unit,
) {
    val firstDayOffset = (platformWeekday(year, month, 1) - platformFirstWeekday() + 7) % 7
    val cells: List<Int?> = List<Int?>(firstDayOffset) { null } + (1..daysInMonth(year, month)).map { it }
}

@Composable
private fun DatePickerHeader(onDismiss: () -> Unit) {
    TandemModalHeader(title = "Choose date", titleSize = TandemTypography.cardTitle, onClose = onDismiss)
}

@Composable
private fun DatePickerStepper(month: DatePickerMonth) {
    PeriodStepper(
        title = formatMonth(month.year, month.month),
        previousLabel = "Previous month",
        nextLabel = "Next month",
        onPrevious = { month.step(-1) },
        onNext = { month.step(1) },
    )
}

/** The weekday initials and the weeks of the visible month, one tappable cell per day. */
@Composable
private fun DatePickerMonthGrid(
    month: DatePickerMonth,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        (0..6).forEach { column ->
            Text(
                formatWeekday(month.year, month.month, 1 + ((column - month.firstDayOffset + 7) % 7)),
                Modifier.weight(1f),
                color = tandemPalette.muted,
                textAlign = TextAlign.Center,
                fontSize = TandemTypography.caption,
            )
        }
    }
    month.cells.chunked(7).forEach { week ->
        Row(horizontalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
            week.forEach { day ->
                if (day == null) {
                    Spacer(Modifier.weight(1f).height(MIN_TOUCH_TARGET))
                } else {
                    DatePickerDayCell(transactionIsoDate(month.year, month.month, day), day, selectedDate, onSelect)
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(MIN_TOUCH_TARGET)) }
        }
    }
}

@Composable
private fun RowScope.DatePickerDayCell(
    isoDate: String,
    day: Int,
    selectedDate: String,
    onSelect: (String) -> Unit,
) {
    val selected = selectedDate == isoDate
    val textColor by animateColorAsState(
        targetValue = if (selected) tandemPalette.accent else tandemPalette.text,
        animationSpec = tandemMotionSpec(tandemReduceMotion, TandemMotion.COLOR_MILLIS),
        label = "date-selection-text",
    )
    SelectableTile(
        selected = selected,
        contentDescription = displayTransactionDate(isoDate),
        onSelect = { onSelect(isoDate) },
        modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH_TARGET),
        shape = CircleShape,
        selectedBackground = tandemPalette.accentSurface,
        unselectedBackground = Color.Transparent,
    ) {
        Text(
            day.toString(),
            color = textColor,
            fontSize = TandemTypography.label,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** A tappable field showing [date], or [emptyLabel] when there's none, that opens [DatePickerDialog]. */
@Composable
internal fun DateField(
    label: String,
    date: String?,
    emptyLabel: String,
    onDateChange: (String?) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FieldFrame(label, onClickLabel = "Choose $label", onClick = { picking = true }) {
        Text(
            date?.let(::formatLiveDate) ?: emptyLabel,
            Modifier.weight(1f),
            color = if (date == null) tandemPalette.muted else tandemPalette.text,
            fontSize = TandemTypography.action,
        )
        if (date != null) {
            TandemIconButton(Lucide.X, "Clear $label", onClick = { onDateChange(null) })
        } else {
            Icon(Lucide.Calendar, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
        }
    }
    DatePickerDialog(
        visible = picking,
        initialDate = date ?: platformTodayIsoDate(),
        onDismiss = { picking = false },
        onSelect = {
            picking = false
            onDateChange(it)
        },
    )
}
