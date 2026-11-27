@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.noteWithTemplateLine
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryNote
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.inputs.PillGrid
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.ToggleRow
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.GoalTemplateDraft
import app.tandemforactual.ui.feature.budget.GoalTemplateKind
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Builds one template from plain fields and adds its line to the category's note, in Actual's own syntax. */
@Composable
internal fun GoalTemplateEditorSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    note: String,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    var draft by remember(visible, category.id) { mutableStateOf(GoalTemplateDraft()) }
    val line = draft.line()
    val busy = mobileSession.isPerformingBudgetAction
    TandemTaskSheet(visible = visible, onDismissRequest = { if (!busy) onClose() }, onDismissed = {}) {
        SheetCard(
            modifier = Modifier.heightIn(max = 720.dp),
            maxWidth = 560.dp,
            header = { BudgetSheetTitle(Lucide.Wand, "Add template", mobileSession, onClose) },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy) { onClose() }
                    TandemActionButton(
                        "Add",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = line != null && !state.readOnlyMode && !busy,
                    ) {
                        val added = line ?: return@TandemActionButton
                        scope.launch {
                            val saved =
                                mobileSession.budgetEdits.setBudgetCategoryNote(
                                    state.selectedBudgetId,
                                    category.id,
                                    noteWithTemplateLine(note, added),
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (saved) onClose()
                        }
                    }
                }
            },
        ) {
            Text(category.name, color = tandemPalette.muted, fontSize = TandemTypography.caption)
            PillGrid(GoalTemplateKind.entries.map { it to it.label }, draft.kind, { draft = draft.copy(kind = it) }, columns = 2)
            GoalTemplateFields(draft, mobileSession) { draft = it }
            Text(
                line ?: "Fill in the fields to see the note line.",
                color = if (line == null) tandemPalette.muted else tandemPalette.text,
                fontSize = TandemTypography.caption,
                fontFamily = FontFamily.Monospace,
            )
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}

@Composable
private fun GoalTemplateFields(
    draft: GoalTemplateDraft,
    mobileSession: TandemMobileSession,
    onChange: (GoalTemplateDraft) -> Unit,
) {
    val decimal = TextFieldOptions(placeholder = "0")
    when (draft.kind) {
        GoalTemplateKind.FIXED -> {
            TextFieldCard("Each month", draft.amount, { onChange(draft.copy(amount = it)) }, decimal)
            TextFieldCard("Up to (optional)", draft.cap, { onChange(draft.copy(cap = it)) }, decimal)
        }
        GoalTemplateKind.BY_DATE -> {
            TextFieldCard("Target", draft.amount, { onChange(draft.copy(amount = it)) }, decimal)
            TextFieldCard("By month", draft.month, { onChange(draft.copy(month = it)) }, TextFieldOptions(placeholder = "2027-06"))
            ToggleRow("Repeat every year", checked = draft.repeatYearly) { onChange(draft.copy(repeatYearly = it)) }
        }
        GoalTemplateKind.PERCENT -> PercentFields(draft, mobileSession, onChange)
        GoalTemplateKind.AVERAGE -> {
            PillGrid(listOf(3, 6, 12).map { it to "$it months" }, draft.months, { onChange(draft.copy(months = it)) }, columns = 3)
            TextFieldCard("Adjust by % (optional)", draft.adjustPercent, {
                onChange(draft.copy(adjustPercent = it))
            }, TextFieldOptions(placeholder = "10 or -5"))
        }
        GoalTemplateKind.SCHEDULE -> {
            val names =
                mobileSession.scheduleRecords
                    .mapNotNull { it.recordName() }
                    .distinct()
                    .sorted()
            if (names.isEmpty()) {
                Text(
                    "This budget has no named schedules yet.",
                    color = tandemPalette.muted,
                    fontSize = TandemTypography.body,
                )
            }
            if (names.isNotEmpty()) PillGrid(names.map { it to it }, draft.schedule, { onChange(draft.copy(schedule = it)) }, columns = 2)
            ToggleRow("Budget the full amount when it’s due", checked = draft.full) { onChange(draft.copy(full = it)) }
        }
        GoalTemplateKind.CAP -> CapFields(draft, onChange)
        GoalTemplateKind.REMAINDER ->
            TextFieldCard("Weight", draft.weight, {
                onChange(draft.copy(weight = it))
            }, TextFieldOptions(placeholder = "1"))
        GoalTemplateKind.GOAL -> TextFieldCard("Balance to reach", draft.amount, { onChange(draft.copy(amount = it)) }, decimal)
    }
    if (draft.kind != GoalTemplateKind.REMAINDER && draft.kind != GoalTemplateKind.GOAL) {
        TextFieldCard("Priority (optional)", draft.priority, {
            onChange(draft.copy(priority = it))
        }, TextFieldOptions(placeholder = "Lower numbers are funded first"))
    }
}

@Composable
private fun PercentFields(
    draft: GoalTemplateDraft,
    mobileSession: TandemMobileSession,
    onChange: (GoalTemplateDraft) -> Unit,
) {
    val incomes =
        mobileSession.categoryRecords
            .filter { (it.recordBody()?.get("is_income") as? JsonPrimitive)?.booleanOrNull == true }
            .mapNotNull { it.recordName() }
    TextFieldCard("Percent", draft.percent, { onChange(draft.copy(percent = it)) }, TextFieldOptions(placeholder = "10"))
    val sources = listOf("all income" to "All income") + incomes.map { it to it } + listOf("available funds" to "Available funds")
    PillGrid(sources, draft.source, { onChange(draft.copy(source = it)) }, columns = 2)
    ToggleRow("Use last month’s income", checked = draft.previous) { onChange(draft.copy(previous = it)) }
}

@Composable
private fun CapFields(
    draft: GoalTemplateDraft,
    onChange: (GoalTemplateDraft) -> Unit,
) {
    TextFieldCard("Up to", draft.cap, { onChange(draft.copy(cap = it)) }, TextFieldOptions(placeholder = "0"))
    PillGrid(listOf("monthly" to "Month", "weekly" to "Week", "daily" to "Day"), draft.period, {
        onChange(draft.copy(period = it))
    }, columns = 3)
    if (draft.period == "weekly") {
        TextFieldCard(
            "Weeks start on",
            draft.weekStart,
            { onChange(draft.copy(weekStart = it)) },
            TextFieldOptions(placeholder = "2027-01-04"),
        )
    }
    ToggleRow("Keep any extra above the cap", checked = draft.hold) { onChange(draft.copy(hold = it)) }
}
