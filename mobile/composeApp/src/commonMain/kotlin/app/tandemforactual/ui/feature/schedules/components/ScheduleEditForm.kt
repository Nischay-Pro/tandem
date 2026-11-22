@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.ui.ScheduleChoiceField
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.FieldFrame
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.schedules.ScheduleDatePickerDialog
import app.tandemforactual.ui.feature.schedules.scheduleFieldErrorMessage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide

/** The schedule edit form: name, amount rule, account, payee, repetition and end, then save or cancel. */
@Composable
internal fun ScheduleEditForm(
    state: AppState,
    mobileSession: TandemMobileSession,
    draft: ScheduleEditDraft,
    accountOptions: List<Pair<String, String>>,
    payeeOptions: List<Pair<String, String>>,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    draft.editError?.takeIf { draft.editErrorField == null }?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    TextFieldCard("Name", draft.nameDraft, {
        draft.nameDraft = it
        draft.clearEditError("name")
    })
    ScheduleFieldError(draft, "name")
    ScheduleAmountFields(state, draft)
    ScheduleChoiceField(
        "Account",
        accountOptions.firstOrNull { it.first == draft.accountIdDraft }?.second ?: "Choose account",
        accountOptions,
    ) {
        draft.accountIdDraft = it
        draft.clearEditError("account")
    }
    ScheduleFieldError(draft, "account")
    ScheduleChoiceField(
        "Payee",
        payeeOptions.firstOrNull { it.first == draft.payeeIdDraft }?.second ?: "Choose payee",
        payeeOptions,
    ) {
        draft.payeeIdDraft = it
        draft.clearEditError("payee")
    }
    ScheduleFieldError(draft, "payee")
    ScheduleRepeatFields(draft)
    budgetActionFeedbackMessage(mobileSession)
    TandemActionButton(
        if (mobileSession.isPerformingBudgetAction) {
            "Saving…"
        } else {
            "Save schedule"
        },
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        enabled =
            !mobileSession.isPerformingBudgetAction,
        loading = mobileSession.isPerformingBudgetAction,
        onClick = onSave,
    )
    TandemActionButton(
        "Cancel",
        TandemActionButtonTone.NEUTRAL,
        modifier = Modifier.fillMaxWidth(),
        enabled = !mobileSession.isPerformingBudgetAction,
        onClick = onCancel,
    )
}

@Composable
private fun ScheduleFieldError(
    draft: ScheduleEditDraft,
    field: String,
) {
    FieldErrorMessage(scheduleFieldErrorMessage(draft.editError, draft.editErrorField, field), owner = FeedbackMessageOwner.Field(field))
}

@Composable
private fun ScheduleAmountFields(
    state: AppState,
    draft: ScheduleEditDraft,
) {
    TextFieldCard(
        "Amount",
        draft.amountDraft,
        {
            draft.amountDraft = it
            draft.clearEditError("amount")
        },
        TextFieldOptions(
            amountFractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        ),
    )
    ScheduleFieldError(draft, "amount")
    ScheduleChoiceField(
        "Amount rule",
        when (draft.amountRuleDraft) {
            "isapprox" -> "About this amount"
            "isbetween" -> "Between two amounts"
            else -> "Exact amount"
        },
        listOf("is" to "Exact amount", "isapprox" to "About this amount", "isbetween" to "Between two amounts"),
    ) {
        draft.amountRuleDraft = it
        draft.clearEditError("maximum")
    }
    if (draft.amountRuleDraft == "isbetween") {
        TextFieldCard(
            "Maximum amount",
            draft.amountMaxDraft,
            {
                draft.amountMaxDraft = it
                draft.clearEditError("maximum")
            },
            TextFieldOptions(
                amountFractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            ),
        )
        ScheduleFieldError(draft, "maximum")
    }
}

@Composable
private fun ScheduleRepeatFields(draft: ScheduleEditDraft) {
    ScheduleChoiceField(
        if (draft.frequencyDraft == "once") "Repeats" else "Repeats every",
        when (draft.frequencyDraft) {
            "daily" -> "Daily"
            "weekly" -> "Weekly"
            "monthly" -> "Monthly"
            "yearly" -> "Yearly"
            else -> "One time"
        },
        listOf(
            "once" to "One time",
            "daily" to "Daily",
            "weekly" to "Weekly",
            "monthly" to "Monthly",
            "yearly" to "Yearly",
        ),
    ) {
        draft.frequencyDraft = it
        if (draft.editErrorField in setOf("interval", "occurrences", "startDate", "endDate")) {
            draft.clearEditError()
        }
    }
    if (draft.frequencyDraft != "once") {
        TextFieldCard(
            "Interval",
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
    ScheduleDateField(
        if (draft.frequencyDraft == "once") "Date" else "Start date",
        "Choose date",
        draft.dateDraft,
    ) { draft.datePickerTarget = "start" }
    ScheduleFieldError(draft, "startDate")
    if (draft.frequencyDraft != "once") ScheduleEndFields(draft)
}

@Composable
private fun ScheduleEndFields(draft: ScheduleEditDraft) {
    ToggleRow("Skip weekends", checked = draft.skipWeekendDraft) {
        draft.skipWeekendDraft = it
    }
    ScheduleChoiceField(
        "Ends",
        when (draft.endModeDraft) {
            "after_n_occurrences" -> "After a number of repeats"
            "on_date" -> "On a date"
            else -> "Never"
        },
        listOf(
            "never" to "Never",
            "after_n_occurrences" to "After a number of repeats",
            "on_date" to "On a date",
        ),
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

@Composable
private fun ScheduleDateField(
    label: String,
    onClickLabel: String,
    date: String,
    onClick: () -> Unit,
) {
    FieldFrame(
        label,
        onClickLabel = onClickLabel,
        onClick = onClick,
    ) {
        Text(
            formatLiveDate(date),
            Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.action,
        )
        Icon(
            Lucide.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = tandemPalette.muted,
        )
    }
}

/** The date picker for the draft's start or end date. */
@Composable
internal fun ScheduleEditDatePicker(draft: ScheduleEditDraft) {
    ScheduleDatePickerDialog(
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
