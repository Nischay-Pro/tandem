@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.applyBudgetTemplate
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.categoryCountLabel
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.rows.ListRow
import app.tandemforactual.ui.design.rows.RowLeading
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.sheets.SheetCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.BudgetTemplate
import app.tandemforactual.ui.feature.budget.budgetTemplateReplacedCount
import app.tandemforactual.ui.feature.budget.decodeBudgetTemplates
import app.tandemforactual.ui.feature.budget.encodeBudgetTemplates
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.launch

/** Sheet for saving this month's plan as a template and applying saved templates. */
@Composable
internal fun BudgetTemplatesSheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    groups: List<LiveCategoryGroupSummary>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentBudgetTemplates =
        remember(state.budgetTemplatesJson, state.selectedBudgetId) {
            decodeBudgetTemplates(state.budgetTemplatesJson).filter { it.budgetId == state.selectedBudgetId }
        }
    val currentTemplateAssignments =
        remember(groups) {
            groups.flatMap { group -> group.categories }.map { it.id to it.assignedMinor }
        }
    var confirmingTemplate by remember(state.selectedBudgetId, state.selectedMonthId) { mutableStateOf<BudgetTemplate?>(null) }
    val applyTemplate: (BudgetTemplate) -> Unit = { template ->
        scope.launch {
            val applied =
                mobileSession.budgetEdits.applyBudgetTemplate(
                    state.selectedBudgetId,
                    state.selectedMonthId,
                    template.assignments,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            confirmingTemplate = null
            if (applied) onDismiss()
        }
    }
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) onDismiss() },
        onDismissed = {},
    ) {
        SheetCard(
            modifier = Modifier.heightIn(max = 680.dp),
            header = {
                BudgetTemplatesHeader(mobileSession, onDismiss)
            },
        ) {
            SaveTemplateSection(state, mobileSession, currentTemplateAssignments)
            if (currentTemplateAssignments.size > 500) {
                InlineMessage("This plan has more than 500 categories and cannot be saved as a template.", InlineMessageKind.INFO)
            }
            budgetActionFeedbackMessage(mobileSession)
            if (currentBudgetTemplates.isEmpty()) {
                Text("Saved templates will appear here.", color = tandemPalette.muted, fontSize = TandemTypography.body)
            } else {
                currentBudgetTemplates.forEach { template ->
                    BudgetTemplateCard(template, state, mobileSession) {
                        if (budgetTemplateReplacedCount(template.assignments, currentTemplateAssignments) > 0) {
                            confirmingTemplate = template
                        } else {
                            applyTemplate(template)
                        }
                    }
                }
            }
        }
    }
    confirmingTemplate?.takeIf { visible }?.let { template ->
        ReplaceAmountsDialog(
            template,
            budgetTemplateReplacedCount(template.assignments, currentTemplateAssignments),
            state,
            mobileSession,
            onDismiss = { confirmingTemplate = null },
            onConfirm = { applyTemplate(template) },
        )
    }
}

@Composable
private fun ReplaceAmountsDialog(
    template: BudgetTemplate,
    replacedCount: Int,
    state: AppState,
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DestructiveConfirmationDialog(
        title = "Replace ${state.selectedMonth} amounts?",
        message =
            "${categoryCountLabel(replacedCount)} already ${if (replacedCount == 1) "has an amount" else "have amounts"} " +
                "this month. Applying “${template.name}” replaces them.",
        confirmLabel = if (mobileSession.isPerformingBudgetAction) "Applying…" else "Replace amounts",
        dismissLabel = "Keep amounts",
        enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
        dismissEnabled = !mobileSession.isPerformingBudgetAction,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) onDismiss() },
        onConfirm = onConfirm,
    )
}

@Composable
private fun BudgetTemplatesHeader(
    mobileSession: TandemMobileSession,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(24.dp), tint = tandemPalette.accent)
        Text(
            "Plan templates",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            enabled = !mobileSession.isPerformingBudgetAction,
        ) { onDismiss() }
    }
}

@Composable
private fun BudgetTemplateCard(
    template: BudgetTemplate,
    state: AppState,
    mobileSession: TandemMobileSession,
    onApply: () -> Unit,
) {
    SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(TandemSpacing.small)) {
            ListRow(
                template.name,
                subtitle = categoryCountLabel(template.assignments.size),
                leading = RowLeading.Icon(Lucide.CalendarDays),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
                TandemActionButton(
                    if (mobileSession.isPerformingBudgetAction) "Applying…" else "Apply",
                    TandemActionButtonTone.PRIMARY,
                    modifier = Modifier.weight(1f),
                    icon = Lucide.Check,
                    enabled = !state.readOnlyMode && !mobileSession.isPerformingBudgetAction,
                    loading = mobileSession.isPerformingBudgetAction,
                ) { onApply() }
                TandemIconButton(
                    Lucide.Trash2,
                    "Delete ${template.name}",
                    sizeDp = 48,
                    iconSizeDp = 20,
                    tint = tandemPalette.negative,
                    containerColor = tandemPalette.raised,
                    enabled = !mobileSession.isPerformingBudgetAction,
                ) {
                    state.budgetTemplatesJson =
                        encodeBudgetTemplates(
                            decodeBudgetTemplates(state.budgetTemplatesJson) - template,
                        )
                }
            }
        }
    }
}

@Composable
private fun SaveTemplateSection(
    state: AppState,
    mobileSession: TandemMobileSession,
    currentTemplateAssignments: List<Pair<String, Long>>,
) {
    var budgetTemplateNameDraft by rememberSaveable(state.selectedBudgetId) { mutableStateOf("") }
    val canSaveCurrentTemplate = currentTemplateAssignments.isNotEmpty() && currentTemplateAssignments.size <= 500
    TextFieldCard(
        "Template name",
        budgetTemplateNameDraft,
        { budgetTemplateNameDraft = it.take(60) },
        TextFieldOptions(placeholder = "Summer plan"),
    )
    TandemActionButton(
        "Save ${state.selectedMonth} plan",
        TandemActionButtonTone.PRIMARY,
        modifier = Modifier.fillMaxWidth(),
        icon = Lucide.Check,
        enabled =
            budgetTemplateNameDraft.isNotBlank() &&
                canSaveCurrentTemplate &&
                !mobileSession.isPerformingBudgetAction,
    ) {
        val saved =
            decodeBudgetTemplates(state.budgetTemplatesJson)
                .filterNot { it.budgetId == state.selectedBudgetId && it.name.equals(budgetTemplateNameDraft.trim(), true) }
        state.budgetTemplatesJson =
            encodeBudgetTemplates(
                saved + BudgetTemplate(state.selectedBudgetId, budgetTemplateNameDraft.trim(), currentTemplateAssignments),
            )
        budgetTemplateNameDraft = ""
    }
}
