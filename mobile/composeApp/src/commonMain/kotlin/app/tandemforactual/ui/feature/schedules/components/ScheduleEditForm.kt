@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.schedules.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.ui.ScheduleChoiceField
import app.tandemforactual.ui.design.buttons.SegmentedSelector
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.feedback.FeedbackMessageOwner
import app.tandemforactual.ui.design.feedback.FieldErrorMessage
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.feature.schedules.scheduleFieldErrorMessage

/** The choices the schedule form offers, as id-name pairs. */
internal class ScheduleFormOptions(
    val accounts: List<Pair<String, String>>,
    val payees: List<Pair<String, String>>,
    val categories: List<Pair<String, String>>,
)

/** The schedule form: name, direction and amount, account, payee, category, repetition and auto-add, then save or cancel. */
@Composable
internal fun ScheduleEditForm(
    state: AppState,
    mobileSession: TandemMobileSession,
    draft: ScheduleEditDraft,
    options: ScheduleFormOptions,
    saveLabel: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    draft.editError?.takeIf { draft.editErrorField == null }?.let { InlineMessage(it, InlineMessageKind.ERROR) }
    TextFieldCard(
        "Name",
        draft.nameDraft,
        {
            draft.nameDraft = it
            draft.clearEditError("name")
        },
        TextFieldOptions(placeholder = "Uses the payee when empty"),
    )
    ScheduleFieldError(draft, "name")
    SegmentedSelector(listOf("Money out" to draft.outflowDraft, "Money in" to !draft.outflowDraft)) { draft.outflowDraft = it == 0 }
    ScheduleAmountFields(state, draft)
    SchedulePartyFields(draft, options)
    ScheduleRepeatFields(draft)
    ScheduleUpcomingDates(draft)
    ToggleRow(
        "Add automatically",
        "Adds the transaction on its date, the next time Tandem syncs.",
        checked = draft.postsTransactionDraft,
    ) { draft.postsTransactionDraft = it }
    budgetActionFeedbackMessage(mobileSession)
    TandemActionButton(
        if (mobileSession.isPerformingBudgetAction) "Saving…" else saveLabel,
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        enabled = !mobileSession.isPerformingBudgetAction,
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
internal fun ScheduleFieldError(
    draft: ScheduleEditDraft,
    field: String,
) {
    FieldErrorMessage(scheduleFieldErrorMessage(draft.editError, draft.editErrorField, field), owner = FeedbackMessageOwner.Field(field))
}

@Composable
private fun SchedulePartyFields(
    draft: ScheduleEditDraft,
    options: ScheduleFormOptions,
) {
    ScheduleChoiceField(
        "Account",
        options.accounts.firstOrNull { it.first == draft.accountIdDraft }?.second ?: "Choose account",
        options.accounts,
    ) {
        draft.accountIdDraft = it
        draft.clearEditError("account")
    }
    ScheduleFieldError(draft, "account")
    ScheduleChoiceField(
        "Payee",
        options.payees.firstOrNull { it.first == draft.payeeIdDraft }?.second ?: "Choose payee",
        options.payees,
    ) {
        draft.payeeIdDraft = it
        draft.clearEditError("payee")
    }
    ScheduleFieldError(draft, "payee")
    ScheduleChoiceField(
        "Category",
        options.categories.firstOrNull { it.first == draft.categoryIdDraft }?.second ?: "No category",
        listOf("" to "No category") + options.categories,
    ) { draft.categoryIdDraft = it }
}

@Composable
private fun ScheduleAmountFields(
    state: AppState,
    draft: ScheduleEditDraft,
) {
    val amountOptions =
        TextFieldOptions(
            amountFractionDigits = state.budgetDisplaySettings?.currencyFractionDigits ?: 2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    TextFieldCard(
        if (draft.amountRuleDraft == "isbetween") "Minimum amount" else "Amount",
        draft.amountDraft,
        {
            draft.amountDraft = it
            draft.clearEditError("amount")
        },
        amountOptions,
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
            amountOptions,
        )
        ScheduleFieldError(draft, "maximum")
    }
}
