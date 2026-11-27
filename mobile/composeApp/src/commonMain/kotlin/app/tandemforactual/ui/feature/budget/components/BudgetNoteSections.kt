@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.noteOverridesFor
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryGroupNote
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryNote
import app.tandemforactual.ui.core.session.budget.setBudgetMonthNote
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.ui.NoteEditor
import app.tandemforactual.ui.core.ui.NoteSection
import app.tandemforactual.ui.core.ui.noteWithPendingEdit

private fun AppState.password() = encryptedPassword.takeIf { it.isNotBlank() }

@Composable
internal fun CategoryNoteSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
) {
    val budgetId = state.selectedBudgetId
    val note =
        noteWithPendingEdit(
            mobileSession.budgetQueue.noteOverridesFor(budgetId, BudgetReorderKind.SET_CATEGORY_NOTE),
            mobileSession.categoryRecords.firstOrNull { it.recordId == category.id },
            category.id,
        )
    val editor =
        NoteEditor("Category note", category.name, "What this category is for", state.readOnlyMode) { draft ->
            mobileSession.budgetEdits.setBudgetCategoryNote(budgetId, category.id, draft, state.password())
        }
    NoteSection(note, editor, mobileSession, "category-note:$budgetId:${category.id}")
}

@Composable
internal fun CategoryGroupNoteSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary,
) {
    val budgetId = state.selectedBudgetId
    val note =
        noteWithPendingEdit(
            mobileSession.budgetQueue.noteOverridesFor(budgetId, BudgetReorderKind.SET_CATEGORY_GROUP_NOTE),
            mobileSession.categoryGroupRecords.firstOrNull { it.recordId == group.id },
            group.id,
        )
    val editor =
        NoteEditor("Group note", group.name, "What this group covers", state.readOnlyMode) { draft ->
            mobileSession.budgetEdits.setBudgetCategoryGroupNote(budgetId, group.id, draft, state.password())
        }
    NoteSection(note, editor, mobileSession, "group-note:$budgetId:${group.id}")
}

/** The selected month's note, which Actual keeps as `budget-YYYY-MM`. */
@Composable
internal fun MonthNoteSection(
    state: AppState,
    mobileSession: TandemMobileSession,
) {
    val budgetId = state.selectedBudgetId
    val month = state.selectedMonthId
    val label = state.selectedMonth
    val note =
        noteWithPendingEdit(
            mobileSession.budgetQueue.noteOverridesFor(budgetId, BudgetReorderKind.SET_MONTH_NOTE),
            mobileSession.selectedBudgetMonthRecord?.takeIf { it.recordId == month },
            month,
        )
    val editor =
        NoteEditor("Month note", label, "Plans or reminders for $label", state.readOnlyMode) { draft ->
            mobileSession.budgetEdits.setBudgetMonthNote(budgetId, month, draft, state.password(), label)
        }
    NoteSection(note, editor, mobileSession, "month-note:$budgetId:$month")
}
