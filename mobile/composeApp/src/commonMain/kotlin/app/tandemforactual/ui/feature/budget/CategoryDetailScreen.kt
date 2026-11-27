@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.categoryIconChoice
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.isCategoryPinned
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.togglePinnedCategory
import app.tandemforactual.ui.design.buttons.SecondaryButton
import app.tandemforactual.ui.design.buttons.TandemIconButton
import app.tandemforactual.ui.design.feedback.Badge
import app.tandemforactual.ui.design.feedback.EmptyStateCard
import app.tandemforactual.ui.design.templates.AmountHero
import app.tandemforactual.ui.design.templates.PageColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.design.theme.tandemPalette
import app.tandemforactual.ui.feature.budget.components.AssignedAmountSheet
import app.tandemforactual.ui.feature.budget.components.CategoryDetailSheetState
import app.tandemforactual.ui.feature.budget.components.CategoryInsightsSection
import app.tandemforactual.ui.feature.budget.components.CategoryNoteSection
import app.tandemforactual.ui.feature.budget.components.CategoryThisMonthSection
import app.tandemforactual.ui.feature.budget.components.DeleteCategoryConfirmation
import app.tandemforactual.ui.feature.budget.components.DeleteCategorySheet
import app.tandemforactual.ui.feature.budget.components.EditCategorySheet
import app.tandemforactual.ui.feature.budget.components.MoveMoneySheet
import app.tandemforactual.ui.feature.budget.components.rememberCategoryDetailSheetState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun CategoryDetailScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyStateCard(
            "Choose a budget",
            "Open an Actual budget to view its categories.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
        return
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val sheets =
        rememberCategoryDetailSheetState(
            state.selectedBudgetId,
            state.selectedCategoryId,
            state.selectedMonthId,
            state.selectedCategoryGroupId,
        )
    val model = categoryDetailModel(state, liveBudgetMonth, mobileSession)
    val category = model.category

    fun openCategoryEditor() = showCategoryEditor(state, mobileSession, sheets, model)
    LaunchedEffect(state.selectedCategoryId, state.categoryEditRequested, category?.id) {
        if (state.categoryEditRequested && category != null) {
            state.categoryEditRequested = false
            openCategoryEditor()
        }
    }
    PageColumn {
        if (category == null) {
            EmptyStateCard("Not in this month’s plan", "This category isn’t in the saved plan for ${state.selectedMonth}.")
        } else {
            CategoryDetailContent(
                state,
                navigation,
                mobileSession,
                model,
                category,
                sheets,
                isLiveBudget,
                isTrackingBudget,
                scope,
                ::openCategoryEditor,
            )
        }
    }
    CategoryDetailSheets(state, navigation, mobileSession, liveBudgetMonth, model, sheets, isLiveBudget, isTrackingBudget, scope)
}

private class CategoryDetailModel(
    val category: LiveCategorySummary?,
    val currentIncomeType: Boolean,
    val deleteDestinations: List<LiveCategorySummary>,
    val moveMoneyCategories: List<Pair<String, LiveCategorySummary>>,
    val selectedGroup: LiveCategoryGroupSummary?,
)

@Composable
private fun categoryDetailModel(
    state: AppState,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
): CategoryDetailModel {
    val categoryRecords = mobileSession.categoryRecords
    val incomeById =
        remember(categoryRecords) {
            HashMap<String, Boolean>().apply {
                categoryRecords.forEach { record ->
                    getOrPut(record.recordId) { record.recordBody()?.booleanValue("is_income") ?: false }
                }
            }
        }
    val selectedCategoryId = state.selectedCategoryId
    return remember(liveBudgetMonth, incomeById, selectedCategoryId) {
        val groups = liveBudgetMonth?.groups.orEmpty()
        val currentIncomeType = incomeById[selectedCategoryId] ?: false

        fun sameType(candidate: LiveCategorySummary) = (incomeById[candidate.id] ?: false) == currentIncomeType
        CategoryDetailModel(
            category = groups.asSequence().flatMap { it.categories.asSequence() }.firstOrNull { it.id == selectedCategoryId },
            currentIncomeType = currentIncomeType,
            deleteDestinations = groups.flatMap { it.categories }.filter { it.id != selectedCategoryId && sameType(it) },
            moveMoneyCategories = groups.flatMap { group -> group.categories.filter(::sameType).map { group.name to it } },
            selectedGroup = groups.firstOrNull { group -> group.categories.any { it.id == selectedCategoryId } },
        )
    }
}

private fun showCategoryEditor(
    state: AppState,
    mobileSession: TandemMobileSession,
    sheets: CategoryDetailSheetState,
    model: CategoryDetailModel,
) {
    val selectedCategory = model.category ?: return
    sheets.editName = selectedCategory.name
    sheets.editCategoryGroupId = model.selectedGroup?.id ?: state.selectedCategoryGroupId
    sheets.selectedIconChoice =
        when (state.categoryIconChoice(selectedCategory.id)) {
            "Food" -> "Groceries"
            "Transport" -> "Car"
            else -> state.categoryIconChoice(selectedCategory.id) ?: "Automatic"
        }
    sheets.iconSearch = ""
    mobileSession.feedback.clearBudgetActionMessage()
    sheets.showEditDialog = true
}

@Composable
private fun CategoryDetailContent(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    model: CategoryDetailModel,
    category: LiveCategorySummary,
    sheets: CategoryDetailSheetState,
    isLiveBudget: Boolean,
    isTrackingBudget: Boolean,
    scope: CoroutineScope,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TandemSpacing.small)) {
        Box(Modifier.weight(1f)) { Badge(state.selectedMonth, tandemPalette.muted) }
        if (isLiveBudget) {
            TandemIconButton(
                Lucide.Pencil,
                "Edit category",
                enabled = !state.readOnlyMode,
                tint = tandemPalette.accent,
            ) { onEdit() }
        }
    }
    AmountHero(
        if (isTrackingBudget) "Activity" else "Available",
        state.formatCurrency(if (isTrackingBudget) category.activityMinor else category.balanceMinor),
        if (isTrackingBudget) "For the selected month" else "${state.formatCurrency(category.assignedMinor)} assigned",
    )
    CategoryInsightsSection(state, mobileSession, category, isTrackingBudget)
    CategoryThisMonthSection(
        state,
        navigation,
        mobileSession,
        category,
        sheets,
        model.moveMoneyCategories,
        isLiveBudget,
        isTrackingBudget,
        scope,
    )
    if (isLiveBudget) CategoryNoteSection(state, mobileSession, category, scope)
    if (isLiveBudget) {
        SecondaryButton(
            if (state.isCategoryPinned(category.id)) "Unpin" else "Pin",
        ) { state.togglePinnedCategory(category.id) }
    }
}

@Composable
private fun CategoryDetailSheets(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    model: CategoryDetailModel,
    sheets: CategoryDetailSheetState,
    isLiveBudget: Boolean,
    isTrackingBudget: Boolean,
    scope: CoroutineScope,
) {
    val category = model.category
    AssignedAmountSheet(
        visible = sheets.showAssignedAmountDialog && category != null && isLiveBudget && !isTrackingBudget,
        state = state,
        mobileSession = mobileSession,
        category = category,
        sheets = sheets,
        scope = scope,
    )
    MoveMoneySheet(
        visible = sheets.showMoveMoneyDialog && category != null && isLiveBudget && !isTrackingBudget,
        state = state,
        mobileSession = mobileSession,
        category = category,
        sheets = sheets,
        moveMoneyCategories = model.moveMoneyCategories,
        scope = scope,
    )
    EditCategorySheet(
        visible = sheets.showEditDialog && category != null,
        state = state,
        mobileSession = mobileSession,
        liveBudgetMonth = liveBudgetMonth,
        category = category,
        selectedGroup = model.selectedGroup,
        currentIncomeType = model.currentIncomeType,
        sheets = sheets,
        scope = scope,
    )
    DeleteCategorySheet(
        visible = sheets.showDeleteDialog && category != null,
        state = state,
        mobileSession = mobileSession,
        category = category,
        deleteDestinations = model.deleteDestinations,
        sheets = sheets,
    )
    if (sheets.confirmCategoryDelete && category != null) {
        DeleteCategoryConfirmation(state, navigation, mobileSession, category, model.deleteDestinations, sheets, scope)
    }
}
