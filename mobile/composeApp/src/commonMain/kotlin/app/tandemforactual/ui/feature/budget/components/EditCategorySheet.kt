@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryMoveToGroup
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryRename
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.categoryIconChoices
import app.tandemforactual.ui.core.state.categoryIconVector
import app.tandemforactual.ui.core.state.setCategoryIconChoice
import app.tandemforactual.ui.design.buttons.SelectableTile
import app.tandemforactual.ui.design.buttons.TandemActionButton
import app.tandemforactual.ui.design.buttons.TandemActionButtonTone
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.cards.SurfaceCard
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.sheets.TandemTaskSheet
import app.tandemforactual.ui.design.templates.TextFieldCard
import app.tandemforactual.ui.design.templates.TextFieldOptions
import app.tandemforactual.ui.design.templates.TransactionPickerRow
import app.tandemforactual.ui.design.templates.financeIcon
import app.tandemforactual.ui.design.theme.TandemShapes
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.TandemTypography
import app.tandemforactual.ui.design.theme.tandemAlloFamily
import app.tandemforactual.ui.design.theme.tandemPalette
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sheet for renaming a category, moving it to another group and choosing its icon. */
@Composable
internal fun EditCategorySheet(
    visible: Boolean,
    state: AppState,
    mobileSession: TandemMobileSession,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    category: LiveCategorySummary?,
    selectedGroup: LiveCategoryGroupSummary?,
    currentIncomeType: Boolean,
    sheets: CategoryDetailSheetState,
    scope: CoroutineScope,
) {
    TandemTaskSheet(
        visible = visible,
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) sheets.showEditDialog = false },
        onDismissed = {},
    ) {
        if (category != null) {
            SurfaceCard(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
                    .heightIn(max = 720.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(TandemSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(TandemSpacing.medium),
                ) {
                    EditCategoryHeader(mobileSession) { sheets.showEditDialog = false }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.small),
                    ) {
                        TextFieldCard("Name", sheets.editName, { sheets.editName = it }, TextFieldOptions(placeholder = category.name))
                        val destinationGroups =
                            liveBudgetMonth!!.groups.filter { candidateGroup ->
                                candidateGroup.isIncome == currentIncomeType
                            }
                        CategoryGroupPicker(state, destinationGroups, sheets)
                        CategoryIconPicker(category, sheets)
                        budgetActionFeedbackMessage(mobileSession)
                    }
                    EditCategoryActions(state, mobileSession, category, selectedGroup, sheets, scope)
                }
            }
        }
    }
}

@Composable
private fun EditCategoryHeader(
    mobileSession: TandemMobileSession,
    onClose: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tandemPalette.accentSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Pencil, contentDescription = null, modifier = Modifier.size(20.dp), tint = tandemPalette.accent)
        }
        Text(
            "Edit category",
            modifier = Modifier.weight(1f),
            color = tandemPalette.text,
            fontSize = TandemTypography.sectionTitle,
            fontFamily = tandemAlloFamily(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TandemIconButton(
            Lucide.X,
            "Close",
            sizeDp = 40,
            iconSizeDp = 18,
            containerColor = tandemPalette.raised,
            enabled = !mobileSession.isPerformingBudgetAction,
        ) { onClose() }
    }
}

@Composable
private fun CategoryGroupPicker(
    state: AppState,
    destinationGroups: List<LiveCategoryGroupSummary>,
    sheets: CategoryDetailSheetState,
) {
    var categoryGroupMenuExpanded by rememberSaveable(state.selectedBudgetId, state.selectedCategoryId) { mutableStateOf(false) }
    TransactionPickerRow(
        value = destinationGroups.firstOrNull { it.id == sheets.editCategoryGroupId }?.name.orEmpty(),
        placeholder = "Category group",
        icon = Lucide.Tags,
    ) { categoryGroupMenuExpanded = true }
    DropdownMenu(
        expanded = categoryGroupMenuExpanded,
        onDismissRequest = { categoryGroupMenuExpanded = false },
    ) {
        destinationGroups.forEach { candidateGroup ->
            DropdownMenuItem(
                onClick = {
                    sheets.editCategoryGroupId = candidateGroup.id
                    categoryGroupMenuExpanded = false
                },
            ) {
                Text(candidateGroup.name, color = tandemPalette.text, fontSize = TandemTypography.compactBody)
            }
        }
    }
}

@Composable
private fun CategoryIconPicker(
    category: LiveCategorySummary,
    sheets: CategoryDetailSheetState,
) {
    TextFieldCard(
        "Icon",
        sheets.iconSearch,
        { sheets.iconSearch = it },
        TextFieldOptions(placeholder = "Search icons"),
    )
    val visibleIconChoices = categoryIconChoices.filter { it.contains(sheets.iconSearch.trim(), ignoreCase = true) }
    if (visibleIconChoices.isEmpty()) {
        EmptyStateCard("No icons match", "Try another name.")
    }
    visibleIconChoices.chunked(4).forEach { rowChoices ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
            rowChoices.forEach { choice ->
                val icon = categoryIconVector(choice) ?: financeIcon(category.name) ?: Lucide.Receipt
                SelectableTile(
                    selected = sheets.selectedIconChoice == choice,
                    contentDescription = if (choice == "Automatic") "Automatic icon" else choice,
                    onSelect = { sheets.selectedIconChoice = choice },
                    modifier = Modifier.weight(1f).heightIn(min = 66.dp),
                    shape = RoundedCornerShape(TandemShapes.control),
                    selectedBackground = tandemPalette.accentSurface,
                    unselectedBackground = tandemPalette.surface,
                    selectedBorder = tandemPalette.accent,
                    unselectedBorder = tandemPalette.border,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TandemSpacing.xxSmall),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                            tint = tandemPalette.accent,
                        )
                        Text(
                            choice,
                            color = tandemPalette.text,
                            fontSize = TandemTypography.micro,
                            maxLines = 2,
                        )
                    }
                }
            }
            repeat(4 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun EditCategoryActions(
    state: AppState,
    mobileSession: TandemMobileSession,
    category: LiveCategorySummary,
    selectedGroup: LiveCategoryGroupSummary?,
    sheets: CategoryDetailSheetState,
    scope: CoroutineScope,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        TandemActionButton(
            "Delete",
            TandemActionButtonTone.DESTRUCTIVE,
            modifier = Modifier.weight(1f),
            icon = Lucide.Trash2,
            enabled = !state.readOnlyMode,
        ) {
            sheets.showEditDialog = false
            sheets.deleteDestinationId = null
            mobileSession.feedback.clearBudgetActionMessage()
            sheets.showDeleteDialog = true
        }
        TandemActionButton(
            "Cancel",
            TandemActionButtonTone.NEUTRAL,
            modifier = Modifier.weight(1f),
            enabled = true,
        ) { sheets.showEditDialog = false }
        TandemActionButton(
            "Save",
            TandemActionButtonTone.PRIMARY,
            modifier = Modifier.weight(1f),
            enabled = !state.readOnlyMode && sheets.editName.isNotBlank(),
        ) {
            scope.launch {
                var saved = true
                val currentGroupId = selectedGroup?.id ?: state.selectedCategoryGroupId
                if (sheets.editCategoryGroupId != currentGroupId) {
                    saved =
                        mobileSession.budgetQueue.enqueueBudgetCategoryMoveToGroup(
                            state.selectedBudgetId,
                            category.id,
                            sheets.editCategoryGroupId,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                    if (saved) state.selectedCategoryGroupId = sheets.editCategoryGroupId
                }
                if (saved && sheets.editName.trim() != category.name) {
                    saved =
                        mobileSession.budgetQueue.enqueueBudgetCategoryRename(
                            state.selectedBudgetId,
                            category.id,
                            sheets.editName,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                        )
                }
                if (saved) {
                    state.setCategoryIconChoice(category.id, sheets.selectedIconChoice)
                    sheets.showEditDialog = false
                }
            }
        }
    }
}
