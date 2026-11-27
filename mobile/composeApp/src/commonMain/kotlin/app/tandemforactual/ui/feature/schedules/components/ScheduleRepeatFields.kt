@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.upcomingScheduleDates
import app.tandemforactual.ui.core.format.platformTodayIsoDate
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.ui.DatePickerDialog
import app.tandemforactual.ui.core.ui.ScheduleChoiceField
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.templates.FieldFrame
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X

private val FREQUENCIES =
    listOf("once" to "One time", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly")

private val PATTERN_TYPES =
    listOf(
        "day" to "Day",
        "MO" to "Monday",
        "TU" to "Tuesday",
        "WE" to "Wednesday",
        "TH" to "Thursday",
        "FR" to "Friday",
        "SA" to "Saturday",
        "SU" to "Sunday",
    )

/** Actual's "Last", "1st" … "31st" choices for a monthly pattern. */
private val PATTERN_VALUES = listOf("-1" to "Last") + (1..31).map { it.toString() to ordinal(it) }

/** How often the schedule repeats, from when, on which days of the month, around weekends and until when. */
@Composable
internal fun ScheduleRepeatFields(draft: ScheduleEditDraft) {
    ScheduleChoiceField("Repeats", FREQUENCIES.labelFor(draft.frequencyDraft), FREQUENCIES) {
        draft.frequencyDraft = it
        if (draft.editErrorField in setOf("interval", "occurrences", "startDate", "endDate")) draft.clearEditError()
    }
    if (draft.frequencyDraft != "once") {
        TextFieldCard(
            "Every (${intervalUnits(draft.frequencyDraft)})",
            draft.intervalDraft,
            {
                draft.intervalDraft = it.filter(Char::isDigit).take(3)
                draft.clearEditError("interval")
            },
            TextFieldOptions(
                placeholder = "1",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            ),
        )
        ScheduleFieldError(draft, "interval")
    }
    ScheduleDateField(if (draft.frequencyDraft == "once") "Date" else "Starts", "Choose date", draft.dateDraft) {
        draft.datePickerTarget = "start"
    }
    ScheduleFieldError(draft, "startDate")
    if (draft.frequencyDraft == "monthly") SchedulePatternFields(draft)
    if (draft.frequencyDraft != "once") {
        ToggleRow("Skip weekends", checked = draft.skipWeekendDraft) { draft.skipWeekendDraft = it }
        if (draft.skipWeekendDraft) {
            ScheduleChoiceField(
                "On a weekend, move to",
                if (draft.weekendModeDraft == "before") "The Friday before" else "The Monday after",
                listOf("before" to "The Friday before", "after" to "The Monday after"),
            ) { draft.weekendModeDraft = it }
        }
        ScheduleEndFields(draft)
    }
}

private fun intervalUnits(frequency: String): String =
    when (frequency) {
        "daily" -> "days"
        "weekly" -> "weeks"
        "monthly" -> "months"
        else -> "years"
    }

private fun List<Pair<String, String>>.labelFor(id: String): String = firstOrNull { it.first == id }?.second ?: id

private fun ScheduleEditDraft.replacePattern(
    index: Int,
    pattern: SchedulePattern,
) {
    patternsDraft = patternsDraft.mapIndexed { i, existing -> if (i == index) pattern else existing }
}

/** Actual's "Add specific days" list: each row is a day of the month or an nth weekday. */
@Composable
private fun SchedulePatternFields(draft: ScheduleEditDraft) {
    draft.patternsDraft.forEachIndexed { index, pattern ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            Box(Modifier.weight(1f)) {
                ScheduleChoiceField("Which", PATTERN_VALUES.labelFor(pattern.value.toString()), PATTERN_VALUES) {
                    draft.replacePattern(index, pattern.copy(value = it.toInt()))
                }
            }
            Box(Modifier.weight(1f)) {
                ScheduleChoiceField("Day", PATTERN_TYPES.labelFor(pattern.type), PATTERN_TYPES) {
                    draft.replacePattern(index, pattern.copy(type = it))
                }
            }
            TandemIconButton(Lucide.X, "Remove day", sizeDp = 40, iconSizeDp = 18, containerColor = tandemPalette.raised) {
                draft.patternsDraft = draft.patternsDraft.filterIndexed { i, _ -> i != index }
            }
        }
    }
    TandemActionButton(
        if (draft.patternsDraft.isEmpty()) "Repeat on specific days" else "Add another day",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Plus,
    ) {
        val day =
            draft.dateDraft
                .takeLast(2)
                .toIntOrNull()
                ?.coerceIn(1, 31) ?: 1
        draft.patternsDraft = draft.patternsDraft + SchedulePattern("day", day)
    }
}

@Composable
private fun ScheduleEndFields(draft: ScheduleEditDraft) {
    ScheduleChoiceField(
        "Ends",
        when (draft.endModeDraft) {
            "after_n_occurrences" -> "After a number of repeats"
            "on_date" -> "On a date"
            else -> "Never"
        },
        listOf("never" to "Never", "after_n_occurrences" to "After a number of repeats", "on_date" to "On a date"),
    ) {
        draft.endModeDraft = it
        if (draft.editErrorField in setOf("occurrences", "endDate")) draft.clearEditError()
    }
    if (draft.endModeDraft == "after_n_occurrences") {
        TextFieldCard(
            "Number of repeats",
            draft.endOccurrencesDraft,
            {
                draft.endOccurrencesDraft = it.filter(Char::isDigit).take(6)
                draft.clearEditError("occurrences")
            },
            TextFieldOptions(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)),
        )
        ScheduleFieldError(draft, "occurrences")
    }
    if (draft.endModeDraft == "on_date") {
        ScheduleDateField("End date", "Choose end date", draft.endDateDraft) { draft.datePickerTarget = "end" }
        ScheduleFieldError(draft, "endDate")
    }
}

/** The next few dates the draft's rule falls on, so the repeat settings can be checked before saving. */
@Composable
internal fun ScheduleUpcomingDates(draft: ScheduleEditDraft) {
    val rule = draft.dateRule(null).toString()
    val dates = remember(rule) { upcomingScheduleDates(rule, platformTodayIsoDate(), 4) }
    Text(
        if (dates.isEmpty()) "No upcoming dates" else "Next: " + dates.joinToString(", ") { formatLiveDate(it) },
        color = tandemPalette.muted,
        fontSize = TandemTypography.caption,
    )
}

@Composable
private fun ScheduleDateField(
    label: String,
    onClickLabel: String,
    date: String,
    onClick: () -> Unit,
) {
    FieldFrame(label, onClickLabel = onClickLabel, onClick = onClick) {
        Text(formatLiveDate(date), Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.action)
        Icon(Lucide.ChevronDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = tandemPalette.muted)
    }
}

/** The date picker for the draft's start or end date. */
@Composable
internal fun ScheduleEditDatePicker(draft: ScheduleEditDraft) {
    DatePickerDialog(
        visible = draft.datePickerTarget != null,
        initialDate = if (draft.datePickerTarget == "end") draft.endDateDraft else draft.dateDraft,
        onDismiss = { draft.datePickerTarget = null },
        onSelect = { value ->
            val field = if (draft.datePickerTarget == "end") "endDate" else "startDate"
            if (draft.datePickerTarget == "end") draft.endDateDraft = value else draft.dateDraft = value
            draft.datePickerTarget = null
            draft.clearEditError(field)
        },
    )
}
