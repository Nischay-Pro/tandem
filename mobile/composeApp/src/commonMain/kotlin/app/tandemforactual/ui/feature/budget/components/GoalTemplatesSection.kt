@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.tandemforactual.data.actual.GoalTemplateOutcome
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.applyGoalTemplates
import app.tandemforactual.ui.core.session.budget.checkGoalTemplates
import app.tandemforactual.ui.core.session.budget.previewGoalTemplates
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.budgetTemplateReplacedCount
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Sparkles
import kotlinx.coroutines.launch

/** The `#template` lines in category notes, applied the way Actual's "Apply budget templates" applies them. */
internal data class GoalTemplatesPreview(
    val fill: GoalTemplateOutcome,
    val overwrite: GoalTemplateOutcome,
    val problems: List<String>,
)

/** Problems first: a run that can't apply explains why, then lines Actual's check would flag. */
internal fun goalTemplateMessages(preview: GoalTemplatesPreview): List<String> = (preview.overwrite.errors + preview.problems).distinct()

internal fun goalTemplateSummary(preview: GoalTemplatesPreview): String =
    when {
        preview.overwrite.errors.isNotEmpty() -> "Fix these notes before applying templates."
        preview.overwrite.applied == 0 -> "No category notes have #template or #goal lines yet."
        preview.fill.applied == 0 -> "${categoryCountLabel(preview.overwrite.applied)} with templates. All already have amounts."
        else -> "${categoryCountLabel(preview.overwrite.applied)} with templates · ${preview.fill.applied} still empty"
    }

@Composable
internal fun GoalTemplatesSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    currentAssignments: List<Pair<String, Long>>,
    onApplied: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    var preview by remember(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf<GoalTemplatesPreview?>(null) }
    var confirming by remember(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf(false) }
    LaunchedEffect(state.selectedBudgetId, state.selectedMonthId) {
        val edits = mobileSession.budgetEdits
        val fill = edits.previewGoalTemplates(state.selectedBudgetId, state.selectedMonthId, false, null, password)
        val overwrite = edits.previewGoalTemplates(state.selectedBudgetId, state.selectedMonthId, true, null, password)
        if (fill != null && overwrite != null) {
            preview = GoalTemplatesPreview(fill, overwrite, edits.checkGoalTemplates(state.selectedBudgetId, password))
        }
    }
    val apply: (Boolean) -> Unit = { force ->
        val outcome = preview?.let { if (force) it.overwrite else it.fill }
        if (outcome != null) {
            scope.launch {
                val queued =
                    mobileSession.budgetEdits.applyGoalTemplates(
                        state.selectedBudgetId,
                        state.selectedMonthId,
                        force,
                        null,
                        outcome,
                        password,
                    )
                confirming = false
                if (queued) onApplied()
            }
        }
    }
    GoalTemplatesCard(state, mobileSession, preview, onFill = { apply(false) }, onOverwrite = {
        val replaced =
            preview?.let {
                budgetTemplateReplacedCount(
                    it.overwrite.values.map { v ->
                        v.categoryId to v.budgeted
                    },
                    currentAssignments,
                )
            }
        if ((replaced ?: 0) > 0) confirming = true else apply(true)
    })
    val pending = preview
    if (confirming && pending != null) {
        val replaced = budgetTemplateReplacedCount(pending.overwrite.values.map { it.categoryId to it.budgeted }, currentAssignments)
        DestructiveConfirmationDialog(
            title = "Replace ${state.selectedMonth} amounts?",
            message =
                "${categoryCountLabel(replaced)} already ${if (replaced == 1) "has an amount" else "have amounts"} that " +
                    "${if (replaced == 1) "its template changes" else "their templates change"}. Overwriting replaces them, as Actual does.",
            confirmLabel = if (mobileSession.isPerformingBudgetAction) "Applying…" else "Overwrite amounts",
            dismissLabel = "Keep amounts",
            enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
            dismissEnabled = !mobileSession.isPerformingBudgetAction,
            onDismissRequest = { confirming = false },
            onConfirm = { apply(true) },
        )
    }
}

@Composable
private fun GoalTemplatesCard(
    state: AppState,
    mobileSession: TandemMobileSession,
    preview: GoalTemplatesPreview?,
    onFill: () -> Unit,
    onOverwrite: () -> Unit,
) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(TandemSpacing.small), verticalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            ListRow(
                "Templates in category notes",
                subtitle = preview?.let(::goalTemplateSummary) ?: "Reading templates…",
                leading = RowLeading.Icon(Lucide.NotebookPen),
            )
            preview?.let(::goalTemplateMessages)?.takeIf { it.isNotEmpty() }?.let { messages ->
                InlineMessage(
                    messages.take(MAX_MESSAGES).joinToString("\n") +
                        if (messages.size > MAX_MESSAGES) "\n…and ${messages.size - MAX_MESSAGES} more" else "",
                    InlineMessageKind.WARNING,
                )
            }
            Text(
                "Uses the #template and #goal lines from each category’s note, so results match Apply budget templates in Actual.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.caption,
            )
            val ready =
                preview != null && preview.overwrite.errors.isEmpty() && !state.readOnlyMode && !mobileSession.isPerformingBudgetAction
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemActionButton(
                    "Fill empty",
                    TandemActionButtonTone.PRIMARY,
                    modifier = Modifier.weight(1f),
                    icon = Lucide.Sparkles,
                    enabled = ready && (preview?.fill?.applied ?: 0) > 0,
                ) { onFill() }
                TandemActionButton(
                    "Overwrite",
                    TandemActionButtonTone.NEUTRAL,
                    modifier = Modifier.weight(1f),
                    icon = Lucide.RefreshCw,
                    enabled = ready && (preview?.overwrite?.applied ?: 0) > 0,
                ) { onOverwrite() }
            }
        }
    }
}

private const val MAX_MESSAGES = 4
