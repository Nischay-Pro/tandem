@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemModalHeader
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.NoteText
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import kotlinx.coroutines.launch

/** A record's note from Actual, with an edit still waiting to sync shown in its place. */
internal fun noteWithPendingEdit(
    pending: Map<String, String>,
    record: BudgetRecordEntity?,
    id: String,
): String = pending[id] ?: record?.recordBody()?.stringValue("note").orEmpty()

/** What a note belongs to: the sheet's [title], the [subject] shown under it, and how a draft is saved. */
internal class NoteEditor(
    val title: String,
    val subject: String,
    val placeholder: String,
    val readOnly: Boolean,
    val save: suspend (String) -> Boolean,
)

/** A note from Actual with tappable links; an empty note offers "Add a note" unless the budget is read-only. */
@Composable
internal fun NoteSection(
    note: String,
    editor: NoteEditor,
    mobileSession: TandemMobileSession,
    stateKey: String,
) {
    var editing by rememberSaveable(stateKey) { mutableStateOf(false) }
    var draft by rememberSaveable(stateKey) { mutableStateOf("") }
    val open = {
        draft = note
        mobileSession.feedback.clearBudgetActionMessage()
        editing = true
    }
    when {
        note.isNotBlank() -> {
            SectionCard("Note", actionContent = { if (!editor.readOnly) NoteEditAction(open) }) {
                NoteText(note, Modifier.fillMaxWidth().padding(horizontal = TandemSpacing.large, vertical = TandemSpacing.medium))
            }
        }

        !editor.readOnly -> {
            SurfaceCard {
                ListRow(
                    "Add a note",
                    editor.placeholder,
                    leading = RowLeading.Icon(Lucide.NotebookPen),
                    onClick = open,
                )
            }
        }
    }
    NoteEditSheet(
        visible = editing,
        editor = editor,
        mobileSession = mobileSession,
        draft = draft,
        unchanged = draft.trim() == note.trim(),
        onDraftChange = { draft = it },
        onClose = { editing = false },
    )
}

@Composable
private fun NoteEditAction(onClick: () -> Unit) {
    Text(
        "Edit",
        color = tandemPalette.accent,
        fontSize = TandemTypography.label,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .clip(RoundedCornerShape(TandemShapes.small))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = TandemSpacing.small, vertical = TandemSpacing.extraSmall),
    )
}

@Composable
private fun NoteEditSheet(
    visible: Boolean,
    editor: NoteEditor,
    mobileSession: TandemMobileSession,
    draft: String,
    unchanged: Boolean,
    onDraftChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val busy = mobileSession.isPerformingBudgetAction
    TandemTaskSheet(visible = visible, onDismissRequest = { if (!busy) onClose() }, onDismissed = {}) {
        SheetCard(
            maxWidth = 520.dp,
            header = { TandemModalHeader(editor.title, onClose, enabled = !busy, leadingIcon = Lucide.NotebookPen) },
            actions = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                    TandemActionButton("Cancel", TandemActionButtonTone.NEUTRAL, Modifier.weight(1f), enabled = !busy) { onClose() }
                    TandemActionButton(
                        "Save",
                        TandemActionButtonTone.PRIMARY,
                        Modifier.weight(1f),
                        icon = Lucide.Check,
                        enabled = !editor.readOnly && !busy && !unchanged,
                    ) {
                        scope.launch { if (editor.save(draft)) onClose() }
                    }
                }
            },
        ) {
            Text(editor.subject, color = tandemPalette.muted, fontSize = TandemTypography.caption)
            TextFieldCard(
                "Note",
                draft,
                onDraftChange,
                TextFieldOptions(placeholder = editor.placeholder, singleLine = false),
            )
            if (draft.isBlank() && !unchanged) {
                Text("Saving an empty note removes it.", color = tandemPalette.muted, fontSize = TandemTypography.caption)
            }
            budgetActionFeedbackMessage(mobileSession)
        }
    }
}
