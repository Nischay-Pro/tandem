@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.data.actual.GoalTemplateStatus
import app.tandemforactual.data.actual.noteWithoutTemplateLine
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.applyGoalTemplates
import app.tandemforactual.ui.core.session.budget.goalTemplateStatus
import app.tandemforactual.ui.core.session.budget.noteOverridesFor
import app.tandemforactual.ui.core.session.budget.previewGoalTemplates
import app.tandemforactual.ui.core.session.budget.setBudgetCategoryNote
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.ui.noteWithPendingEdit
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.rows.SectionCard
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.describeGoalTemplate
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Wand
import com.composables.icons.lucide.X
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Whether the category has what its templates ask for this month, in words. */
internal fun goalTemplateStatusText(
    status: GoalTemplateStatus,
    budgeted: Long,
    balance: Long,
    money: (Long) -> String,
): Pair<Boolean, String>? {
    if (status.problems.isNotEmpty()) return false to status.problems.joinToString("\n")
    val shortfall = status.shortfall(budgeted, balance) ?: return null
    val target = if (status.longGoal) "Goal ${money(status.goal ?: 0)}" else "Template ${money(status.goal ?: 0)} this month"
    return if (shortfall == 0L) true to "On track · $target" else false to "Underfunded by ${money(shortfall)} · $target"
}

/** The category's `#template` and `#goal` lines, its standing against them, and ways to add, remove and apply them. */
@Composable
internal fun CategoryTemplatesSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    scope: CoroutineScope,
) {
    val note =
        noteWithPendingEdit(
            mobileSession.budgetQueue.noteOverridesFor(state.selectedBudgetId, BudgetReorderKind.SET_CATEGORY_NOTE),
            mobileSession.categoryRecords.firstOrNull { it.recordId == category.id },
            category.id,
        )
    val password = state.encryptedPassword.takeIf { it.isNotBlank() }
    var status by remember(state.selectedBudgetId, category.id) { mutableStateOf<GoalTemplateStatus?>(null) }
    var adding by rememberSaveable(state.selectedBudgetId, category.id) { mutableStateOf(false) }
    LaunchedEffect(state.selectedBudgetId, state.selectedMonthId, category.id, note, category.assignedMinor) {
        status = mobileSession.budgetEdits.goalTemplateStatus(state.selectedBudgetId, state.selectedMonthId, category.id, password)
    }
    val canEdit = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction
    val money: (Double) -> String = { state.formatCurrency((it * MINOR_PER_MAJOR).roundToLong()) }
    SectionCard("Templates", action = if (canEdit) "Add" else null, onClick = if (canEdit) ({ adding = true }) else null) {
        val current = status
        if (current == null || current.templates.isEmpty()) {
            Text(
                "No templates. Add one and Tandem budgets this category the way Actual does.",
                color = tandemPalette.muted,
                fontSize = TandemTypography.body,
            )
        } else {
            goalTemplateStatusText(current, category.assignedMinor, category.balanceMinor, state::formatCurrency)?.let { (good, text) ->
                StatusRow(good, text)
            }
            current.templates.forEachIndexed { index, template ->
                TemplateRow(describeGoalTemplate(template, money), canEdit) {
                    scope.launch {
                        mobileSession.budgetEdits.setBudgetCategoryNote(
                            state.selectedBudgetId,
                            category.id,
                            noteWithoutTemplateLine(note, index),
                            password,
                        )
                    }
                }
            }
            TandemActionButton(
                "Apply to ${state.selectedMonth}",
                TandemActionButtonTone.NEUTRAL,
                Modifier.fillMaxWidth(),
                icon = Lucide.Wand,
                enabled = canEdit && current.problems.isEmpty(),
            ) {
                scope.launch {
                    val ids = listOf(category.id)
                    val edits = mobileSession.budgetEdits
                    val preview = edits.previewGoalTemplates(state.selectedBudgetId, state.selectedMonthId, true, ids, password)
                    if (preview != null &&
                        preview.errors.isEmpty()
                    ) {
                        edits.applyGoalTemplates(state.selectedBudgetId, state.selectedMonthId, true, ids, preview, password)
                    }
                }
            }
        }
    }
    GoalTemplateEditorSheet(adding, state, mobileSession, category, note, scope) { adding = false }
}

@Composable
private fun StatusRow(
    good: Boolean,
    text: String,
) {
    val tint = if (good) tandemPalette.positive else tandemPalette.warning
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(
            if (good) Lucide.CircleCheck else Lucide.TriangleAlert,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = tandemPalette.text, fontSize = TandemTypography.body)
    }
}

@Composable
private fun TemplateRow(
    text: String,
    canEdit: Boolean,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), color = tandemPalette.text, fontSize = TandemTypography.body)
        if (canEdit) TandemIconButton(Lucide.X, "Remove “$text”", sizeDp = 40, iconSizeDp = 16, tint = tandemPalette.muted) { onRemove() }
    }
}

private const val MINOR_PER_MAJOR = 100.0
