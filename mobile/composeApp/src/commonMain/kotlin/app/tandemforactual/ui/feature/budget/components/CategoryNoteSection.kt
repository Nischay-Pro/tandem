@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.categoryNoteOverridesFor
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryNote
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The category's note from Actual, with an edit still waiting to sync shown in its place. */
internal fun categoryNote(
    mobileSession: TandemMobileSession,
    budgetId: String,
    categoryId: String,
): String =
    mobileSession.budgetQueue.categoryNoteOverridesFor(budgetId)[categoryId]
        ?: mobileSession.categoryRecords
            .firstOrNull { it.recordId == categoryId }
            ?.recordBody()
            ?.stringValue("note")
            .orEmpty()

/** The category's note, shown in full, with a sheet to write or change it. */
@Composable
internal fun CategoryNoteSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    scope: CoroutineScope,
) {
    val note = categoryNote(mobileSession, state.selectedBudgetId, category.id)
    var editing by rememberSaveable(state.selectedBudgetId, category.id) { mutableStateOf(false) }
    var draft by rememberSaveable(state.selectedBudgetId, category.id) { mutableStateOf("") }
    val canEdit = !state.readOnlyMode
    SectionCard(
        "Note",
        action = if (!canEdit) null else if (note.isBlank()) "Add" else "Edit",
        onClick =
            if (canEdit) {
                {
                    draft = note
                    mobileSession.feedback.clearBudgetActionMessage()
                    editing = true
                }
            } else {
                null
            },
    ) {
        Text(
            note.ifBlank { "No note yet" },
            color = if (note.isBlank()) tandemPalette.muted else tandemPalette.text,
            fontSize = TandemTypography.body,
            lineHeight = TandemTypography.bodyLineHeight,
        )
    }
    CategoryNoteSheet(
        visible = editing,
        state = state,
        mobileSession = mobileSession,
        category = category,
        draft = draft,
        unchanged = draft.trim() == note.trim(),
        onDraftChange = { draft = it },
        onClose = { editing = false },
        scope = scope,
    )
}

@Composable
private fun CategoryNoteSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    draft: String,
    unchanged: Boolean,
    onDraftChange: (String) -> Unit,
    onClose: () -> Unit,
    scope: CoroutineScope,
) {
    val busy = mobileSession.isPerformingBudgetAction
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!busy) onClose() },
        onDismissed = {},
    ) {
        SheetCard(
            maxWidth = 520.dp,
            header = { BudgetSheetTitle(Lucide.NotebookPen, "Category note", mobileSession, onClose) },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy) { onClose() }
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = !state.readOnlyMode && !busy && !unchanged,
                    ) {
                        scope.launch {
                            val saved =
                                mobileSession.budgetEdits.setBudgetCategoryNote(
                                    state.selectedBudgetId,
                                    category.id,
                                    draft,
                                    state.encryptedPassword.takeIf { it.isNotBlank() },
                                )
                            if (saved) onClose()
                        }
                    }
                }
            },
        ) {
            Text(category.name, color = tandemPalette.muted, fontSize = TandemTypography.caption)
            TextFieldCard(
                "Note",
                draft,
                onDraftChange,
                TextFieldOptions(placeholder = "What this category is for", singleLine = false),
            )
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}
