@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import kotlinx.coroutines.CoroutineScope

/** Which budget screen sheets are open, and the new category group draft. */
@Stable
internal class BudgetScreenDialogs(
    showCopyLastMonth: MutableState<Boolean>,
    showTemplates: MutableState<Boolean>,
    showCreateCategoryGroup: MutableState<Boolean>,
    categoryGroupNameDraft: MutableState<String>,
    newCategoryGroupIsIncome: MutableState<Boolean>,
) {
    var showCopyLastMonth by showCopyLastMonth
    var showTemplates by showTemplates
    var showCreateCategoryGroup by showCreateCategoryGroup
    var categoryGroupNameDraft by categoryGroupNameDraft
    var newCategoryGroupIsIncome by newCategoryGroupIsIncome
    var showAutoAssign by mutableStateOf(false)

    fun openCreateCategoryGroup() {
        categoryGroupNameDraft = ""
        newCategoryGroupIsIncome = false
        showCreateCategoryGroup = true
    }
}

@Composable
internal fun rememberBudgetScreenDialogs(
    budgetId: String,
    monthId: String,
): BudgetScreenDialogs {
    val showCopyLastMonth = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val showTemplates = rememberSaveable(budgetId, monthId) { mutableStateOf(false) }
    val showCreateCategoryGroup = rememberSaveable(budgetId) { mutableStateOf(false) }
    val categoryGroupNameDraft = rememberSaveable(budgetId) { mutableStateOf("") }
    val newCategoryGroupIsIncome = rememberSaveable(budgetId) { mutableStateOf(false) }
    return remember(showCopyLastMonth, showTemplates, showCreateCategoryGroup, categoryGroupNameDraft, newCategoryGroupIsIncome) {
        BudgetScreenDialogs(showCopyLastMonth, showTemplates, showCreateCategoryGroup, categoryGroupNameDraft, newCategoryGroupIsIncome)
    }
}

/** Every sheet and dialog the budget screen can open. */
@Composable
internal fun BudgetScreenSheets(
    dialogs: BudgetScreenDialogs,
    hold: HoldSheetState,
    state: AppState,
    mobileSession: TandemMobileSession,
    groups: List<LiveCategoryGroupSummary>,
    displayGroups: List<LiveCategoryGroupSummary>,
    holdFigures: HoldSheetFigures,
    isLiveBudget: Boolean,
    isTrackingBudget: Boolean,
    scope: CoroutineScope,
) {
    CopyLastMonthSheet(
        visible = dialogs.showCopyLastMonth && isLiveBudget,
        state = state,
        mobileSession = mobileSession,
        onDismiss = { dialogs.showCopyLastMonth = false },
    )
    BudgetTemplatesSheet(
        visible = dialogs.showTemplates && isLiveBudget,
        state = state,
        mobileSession = mobileSession,
        groups = groups,
        onDismiss = { dialogs.showTemplates = false },
    )
    if (hold.mounted && isLiveBudget && !isTrackingBudget) {
        HoldForNextMonthSheet(hold, state, mobileSession, holdFigures, scope)
    }
    if (isLiveBudget) {
        CreateCategoryGroupDialog(
            visible = dialogs.showCreateCategoryGroup,
            state = state,
            mobileSession = mobileSession,
            name = dialogs.categoryGroupNameDraft,
            onNameChange = { dialogs.categoryGroupNameDraft = it },
            isIncome = dialogs.newCategoryGroupIsIncome,
            onIncomeChange = { dialogs.newCategoryGroupIsIncome = it },
            groupIds = displayGroups.map { it.id },
            onDismiss = { dialogs.showCreateCategoryGroup = false },
        )
    }
    MonthPickerDialog(
        visible = state.isBudgetMonthPickerOpen,
        state = state,
        onDismiss = { state.isBudgetMonthPickerOpen = false },
    )
}
