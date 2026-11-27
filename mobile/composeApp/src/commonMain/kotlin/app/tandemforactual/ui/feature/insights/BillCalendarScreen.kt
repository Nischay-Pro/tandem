@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.planning.CashFlow
import app.tandemforactual.data.planning.SpendingInsights
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.leadingBlankDays
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.format.weekdayHeaders
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.design.buttons.PeriodStepper
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.rows.DividerLine
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.ListRowOptions
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.templates.PageColumns
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette

private data class BillEvent(
    val date: String,
    val name: String,
    val payeeId: String?,
    val amountMinor: Long,
)

/** A month's scheduled bills and income on a calendar, with the chosen day's list below. */
@Composable
internal fun BillCalendarScreen(
    state: AppState,
    schedules: List<LiveScheduleSummary>,
) {
    val today = platformTodayIsoDate()
    var monthId by rememberSaveable { mutableStateOf(today.take(7)) }
    var selected by rememberSaveable(monthId) { mutableStateOf(if (today.startsWith(monthId)) today else null) }
    val events =
        remember(schedules, monthId) {
            val first = "$monthId-01"
            val last = "$monthId-${SpendingInsights.daysInMonth(monthId).toString().padStart(2, '0')}"
            schedules
                .flatMap { schedule ->
                    val amount = schedule.amountMinor ?: schedule.amountRangeMinor?.let { (low, high) -> (low + high) / 2 } ?: 0
                    CashFlow
                        .scheduleDates(schedule.dateValueJson, first, last)
                        .filter { it.startsWith(monthId) }
                        .map { BillEvent(it, schedule.name, schedule.payeeId, amount) }
                }.sortedBy { it.date }
        }
    PageColumns(
        primary = {
            val year = monthId.take(4).toInt()
            val month = monthId.drop(5).toInt()
            PeriodStepper(
                formatMonth(year, month),
                "Previous month",
                "Next month",
                { monthId = SpendingInsights.shiftMonth(monthId, -1) },
                { monthId = SpendingInsights.shiftMonth(monthId, 1) },
            )
            MonthTotals(state, events)
            SurfaceCard {
                MonthGrid(monthId, today, selected, events) { selected = it }
            }
        },
        secondary = {
            if (schedules.isEmpty()) {
                EmptyStateCard("No schedules yet", "Bills and paydays scheduled in Actual show on this calendar.")
            } else {
                DayList(state, selected, events)
            }
        },
    )
}

@Composable
private fun MonthTotals(
    state: AppState,
    events: List<BillEvent>,
) {
    val out = -events.filter { it.amountMinor < 0 }.sumOf { it.amountMinor }
    val incoming = events.filter { it.amountMinor > 0 }.sumOf { it.amountMinor }
    Text(
        "${state.formatCurrency(out)} in bills · ${state.formatCurrency(incoming)} coming in",
        color = tandemPalette.muted,
        fontSize = TandemTypography.compactBody,
    )
}

@Composable
private fun MonthGrid(
    monthId: String,
    today: String,
    selected: String?,
    events: List<BillEvent>,
    onSelect: (String) -> Unit,
) {
    val palette = tandemPalette
    val year = monthId.take(4).toInt()
    val month = monthId.drop(5).toInt()
    val cells = List(leadingBlankDays(year, month)) { null } + (1..SpendingInsights.daysInMonth(monthId)).toList()
    val byDate = events.groupBy { it.date }
    Column(Modifier.padding(TandemSpacing.medium), verticalArrangement = Arrangement.spacedBy(TandemSpacing.extraSmall)) {
        Row {
            weekdayHeaders().forEach { name ->
                Text(
                    name,
                    Modifier.weight(1f),
                    color = palette.muted,
                    fontSize = TandemTypography.micro,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = "$monthId-${day.toString().padStart(2, '0')}"
                        DayCell(day, date == today, date == selected, byDate[date].orEmpty(), Modifier.weight(1f)) { onSelect(date) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<BillEvent>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = tandemPalette
    val description = billDayDescription(day, events)
    Column(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(TandemShapes.control))
            .background(if (isSelected) palette.accentSurface else palette.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$day",
            color = if (isToday) palette.accent else palette.text,
            fontSize = TandemTypography.compactBody,
            fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            events.take(3).forEach { event ->
                val dot = Modifier.size(6.dp).clip(CircleShape)
                if (event.amountMinor < 0) {
                    Box(dot.background(palette.negative))
                } else {
                    Box(dot.border(1.25.dp, palette.positive, CircleShape))
                }
            }
        }
    }
}

private fun billDayDescription(
    day: Int,
    events: List<BillEvent>,
): String {
    val bills = events.count { it.amountMinor < 0 }
    val income = events.size - bills
    val parts =
        listOfNotNull(
            bills.takeIf { it > 0 }?.let { if (it == 1) "1 bill" else "$it bills" },
            income.takeIf { it > 0 }?.let { if (it == 1) "1 deposit" else "$it deposits" },
        )
    return if (parts.isEmpty()) "$day" else "$day, ${parts.joinToString(", ")}"
}

@Composable
private fun DayList(
    state: AppState,
    selected: String?,
    events: List<BillEvent>,
) {
    val shown = selected?.let { day -> events.filter { it.date == day } } ?: events
    val title = selected?.let { formatLiveDate(it) } ?: "This month"
    SectionCard(title, if (shown.isEmpty()) "Nothing scheduled" else null) {
        shown.forEachIndexed { index, event ->
            if (index > 0) DividerLine()
            ListRow(
                event.name,
                if (selected == null) formatLiveDate(event.date) else null,
                state.formatCurrency(event.amountMinor),
                leading = RowLeading.Payee(event.name),
                options = ListRowOptions(showChevron = false),
            )
        }
    }
}
