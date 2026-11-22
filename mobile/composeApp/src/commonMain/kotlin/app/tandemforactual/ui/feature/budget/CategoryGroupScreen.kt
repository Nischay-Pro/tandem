@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.budget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tandemforactual.ui.app.FloatingAction
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.HIDDEN_CATEGORY_SCOPE_SUFFIX
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.categoryGroupVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.categoryVisibilityOverridesFor
import app.tandemforactual.ui.core.session.budget.deletedCategoryIdsFor
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryGroupDelete
import app.tandemforactual.ui.core.session.budget.resolveCategoryGroupId
import app.tandemforactual.ui.core.session.budgetActionFeedbackMessage
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.session.shouldShowBudgetCreateAction
import app.tandemforactual.ui.core.state.AppNavigation
import app.tandemforactual.ui.core.state.AppRoute
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveCategorySummary
import app.tandemforactual.ui.core.state.SelectorItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.stringValue
import app.tandemforactual.ui.core.ui.AccountActionDropdown
import app.tandemforactual.ui.design.feedback.EmptyState
import app.tandemforactual.ui.design.feedback.InlineMessage
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import app.tandemforactual.ui.design.sheets.DestructiveConfirmationDialog
import app.tandemforactual.ui.design.templates.PageLazyColumn
import app.tandemforactual.ui.design.theme.TandemSpacing
import app.tandemforactual.ui.feature.budget.components.AddCategoryDialog
import app.tandemforactual.ui.feature.budget.components.CategoryGroupListContext
import app.tandemforactual.ui.feature.budget.components.CategoryGroupReorder
import app.tandemforactual.ui.feature.budget.components.CategoryGroupScreenState
import app.tandemforactual.ui.feature.budget.components.EditCategoryGroupDialog
import app.tandemforactual.ui.feature.budget.components.HiddenCategoryLists
import app.tandemforactual.ui.feature.budget.components.UNSELECTED_GROUP_DELETE_DESTINATION
import app.tandemforactual.ui.feature.budget.components.categoryGroupCategoryItems
import app.tandemforactual.ui.feature.budget.components.categoryGroupHeaderItems
import app.tandemforactual.ui.feature.budget.components.hiddenCategoryItems
import app.tandemforactual.ui.feature.budget.components.rememberCategoryGroupScreenState
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun CategoryGroupScreen(
    state: AppState,
    navigation: AppNavigation,
    liveBudgetMonth: LiveBudgetMonthSummary?,
    mobileSession: TandemMobileSession,
) {
    val scope = rememberCoroutineScope()
    val isLiveBudget = state.selectedBudgetId.isNotBlank()
    if (!isLiveBudget) {
        EmptyState(
            "Choose a budget",
            "Open an Actual budget to view its categories.",
            actionLabel = "Choose budget",
            onAction = { navigation.open(AppRoute.BUDGET_CHOOSER) },
        )
        return
    }
    val isTrackingBudget = isLiveBudget && state.budgetDisplaySettings?.budgetType == "tracking"
    val groups = liveBudgetMonth?.groups.orEmpty()
    val selectedGroupId = mobileSession.budgetQueue.resolveCategoryGroupId(state.selectedBudgetId, state.selectedCategoryGroupId)
    val group = groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    val screen = rememberCategoryGroupScreenState(state.selectedBudgetId, group?.id, group?.name)
    val categoryScopeId = group?.id.orEmpty()
    val dragReorder = remember(state.selectedBudgetId, group?.id) { DragReorderState() }
    val orderedCategories = rememberOrderedCategories(state, mobileSession, group, dragReorder)
    val hiddenCategoryScopeId = "${group?.id.orEmpty()}$HIDDEN_CATEGORY_SCOPE_SUFFIX"
    val hiddenDrag = remember(state.selectedBudgetId, group?.id) { DragReorderState() }
    val hidden = rememberHiddenCategoryLists(state, mobileSession, group, hiddenCategoryScopeId, hiddenDrag)
    val reorder =
        CategoryGroupReorder(
            state = state,
            mobileSession = mobileSession,
            scope = scope,
            screen = screen,
            categoryScopeId = categoryScopeId,
            serverCategoryOrder = group?.categories.orEmpty().map { it.id },
            dragReorder = dragReorder,
            hiddenCategoryScopeId = hiddenCategoryScopeId,
            hiddenCategoryIds = hidden.hiddenCategoryRecords.map { it.recordId },
            hiddenDrag = hiddenDrag,
        )
    val context = CategoryGroupListContext(state, navigation, mobileSession, screen, reorder, isLiveBudget, isTrackingBudget)
    Box(Modifier.fillMaxSize()) {
        CategoryGroupList(group, orderedCategories, hidden, context, scope)
        if (group != null && shouldShowBudgetCreateAction(isLiveBudget, screen.selectedCategoryOrderId != null)) {
            FloatingAction(
                compact = true,
                enabled = !state.readOnlyMode,
                label = "Add category",
                contentDescription = "Add category",
                icon = Lucide.Plus,
                outlined = true,
                onClick = {
                    screen.newCategoryName = ""
                    mobileSession.feedback.clearBudgetActionMessage()
                    screen.showCreateCategoryDialog = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
    if (isLiveBudget && group != null) {
        EditCategoryGroupDialog(state, mobileSession, group, groups, screen, scope)
        if (screen.showDeleteGroupDialog) {
            DeleteCategoryGroupDialog(state, navigation, mobileSession, group, groups, screen, scope)
        }
        AddCategoryDialog(state, mobileSession, group, screen, scope)
    }
}

@Composable
private fun rememberOrderedCategories(
    state: AppState,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary?,
    dragReorder: DragReorderState,
): List<LiveCategorySummary> {
    val categoryScopeId = group?.id.orEmpty()
    val queuedCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, categoryScopeId)
    val categoryMoveStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, categoryScopeId)
    val serverCategoryOrder = group?.categories.orEmpty().map { it.id }
    val queuedCategoryOrder = budgetOrderForScreen(serverCategoryOrder, categoryMoveStatus, queuedCategoryMoves)
    val categoryOrderIds = dragReorder.currentOrder(queuedCategoryOrder)
    return remember(group?.categories, categoryOrderIds) {
        val categories = group?.categories.orEmpty()
        val categoriesById = categories.associateBy { it.id }
        categoryOrderIds.mapNotNull(categoriesById::get) + categories.filterNot { it.id in categoryOrderIds }
    }
}

@Composable
private fun rememberHiddenCategoryLists(
    state: AppState,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary?,
    hiddenCategoryScopeId: String,
    hiddenDrag: DragReorderState,
): HiddenCategoryLists {
    val hiddenGroupRecords =
        remember(
            mobileSession.categoryGroupRecords,
            mobileSession.budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId),
        ) {
            val visibilityOverrides = mobileSession.budgetQueue.categoryGroupVisibilityOverridesFor(state.selectedBudgetId)
            mobileSession.categoryGroupRecords.filter { record ->
                visibilityOverrides[record.recordId] ?: (record.recordBody()?.booleanValue("hidden") == true)
            }
        }
    val categoryVisibilityOverrides = mobileSession.budgetQueue.categoryVisibilityOverridesFor(state.selectedBudgetId)
    val deletedCategoryIds = mobileSession.budgetQueue.deletedCategoryIdsFor(state.selectedBudgetId)
    val hiddenCategoryRecords =
        remember(mobileSession.categoryRecords, group?.id, categoryVisibilityOverrides, deletedCategoryIds) {
            mobileSession.categoryRecords.filter { record ->
                val body = record.recordBody()
                record.recordId !in deletedCategoryIds &&
                    (categoryVisibilityOverrides[record.recordId] ?: body?.booleanValue("hidden") ?: false) &&
                    (body?.stringValue("group") ?: body?.stringValue("group_id")) == group?.id
            }
        }
    val queuedHiddenCategoryMoves = mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, hiddenCategoryScopeId)
    val hiddenCategoryMoveStatus = mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, hiddenCategoryScopeId)
    val queuedHiddenCategoryOrder =
        budgetOrderForScreen(
            hiddenCategoryRecords.map { it.recordId },
            hiddenCategoryMoveStatus,
            queuedHiddenCategoryMoves,
        )
    val hiddenCategoryOrder = hiddenDrag.currentOrder(queuedHiddenCategoryOrder)
    val orderedHiddenCategoryRecords =
        remember(hiddenCategoryRecords, hiddenCategoryOrder) {
            val recordsById = hiddenCategoryRecords.associateBy { it.recordId }
            hiddenCategoryOrder.mapNotNull(recordsById::get) + hiddenCategoryRecords.filterNot { it.recordId in hiddenCategoryOrder }
        }
    return HiddenCategoryLists(hiddenCategoryRecords, orderedHiddenCategoryRecords, hiddenGroupRecords, hiddenCategoryMoveStatus)
}

@Composable
private fun CategoryGroupList(
    group: LiveCategoryGroupSummary?,
    orderedCategories: List<LiveCategorySummary>,
    hidden: HiddenCategoryLists,
    context: CategoryGroupListContext,
    scope: CoroutineScope,
) {
    val categoryMoveStatus =
        context.mobileSession.budgetQueue.budgetReorderStatus(context.state.selectedBudgetId, context.reorder.categoryScopeId)
    PageLazyColumn {
        if (group == null) {
            item(key = "category-group-empty-message") {
                EmptyState(
                    "No category group",
                    "No category group is available for this month.",
                    modifier = Modifier.padding(horizontal = TandemSpacing.large),
                )
            }
        } else {
            categoryGroupHeaderItems(group, categoryMoveStatus, context)
            categoryGroupCategoryItems(group, orderedCategories, context)
            hiddenCategoryItems(group, hidden, context, scope)
        }
    }
}

@Composable
private fun DeleteCategoryGroupDialog(
    state: AppState,
    navigation: AppNavigation,
    mobileSession: TandemMobileSession,
    group: LiveCategoryGroupSummary,
    groups: List<LiveCategoryGroupSummary>,
    screen: CategoryGroupScreenState,
    scope: CoroutineScope,
) {
    val groupDeleteDestinations =
        remember(groups, group.id) {
            groups
                .filter { it.id != group.id && it.isIncome == group.isIncome }
                .flatMap { destinationGroup ->
                    destinationGroup.categories.map { category ->
                        SelectorItem(category.id, "${category.name} · ${destinationGroup.name}")
                    }
                }
        }
    val groupHasCategories =
        remember(group.id, group.categories, mobileSession.categoryRecords) {
            group.categories.isNotEmpty() ||
                mobileSession.categoryRecords.any { record ->
                    val body = record.recordBody()
                    record.recordType == "category" &&
                        body?.booleanValue("deleted") != true &&
                        (body?.stringValue("group") ?: body?.stringValue("group_id")) == group.id
                }
        }
    DestructiveConfirmationDialog(
        title = "Delete ${group.name}?",
        message =
            if (groupHasCategories) {
                "The group’s categories will be deleted. Move their budget and transaction history to another category, or leave transactions unassigned."
            } else {
                "This category group is empty."
            },
        confirmLabel = "Delete group",
        dismissLabel = "Keep group",
        enabled =
            !state.readOnlyMode &&
                !mobileSession.isPerformingBudgetAction &&
                (!groupHasCategories || screen.deleteGroupDestinationId != UNSELECTED_GROUP_DELETE_DESTINATION),
        content = {
            if (groupHasCategories) {
                GroupDeleteDestinationPicker(screen, groupDeleteDestinations)
            }
            budgetActionFeedbackMessage(mobileSession)
        },
        onDismissRequest = { if (!mobileSession.isPerformingBudgetAction) screen.showDeleteGroupDialog = false },
        onConfirm = {
            scope.launch {
                val deleted =
                    mobileSession.budgetQueue.enqueueBudgetCategoryGroupDelete(
                        state.selectedBudgetId,
                        group.id,
                        screen.deleteGroupDestinationId
                            .takeUnless {
                                it.isBlank() || it == UNSELECTED_GROUP_DELETE_DESTINATION
                            },
                        groups.map { it.id },
                        state.encryptedPassword.takeIf(String::isNotBlank),
                    )
                if (deleted) {
                    screen.showDeleteGroupDialog = false
                    navigation.back()
                }
            }
        },
    )
}

@Composable
private fun GroupDeleteDestinationPicker(
    screen: CategoryGroupScreenState,
    groupDeleteDestinations: List<SelectorItem>,
) {
    val deleteGroupDestinationId = screen.deleteGroupDestinationId
    AccountActionDropdown(
        title = "Reassign to",
        selectedLabel =
            when {
                deleteGroupDestinationId == UNSELECTED_GROUP_DELETE_DESTINATION -> "Choose destination"
                deleteGroupDestinationId.isBlank() -> "Leave unassigned"
                else ->
                    groupDeleteDestinations.firstOrNull { it.id == deleteGroupDestinationId }?.label
                        ?: "Leave unassigned"
            },
        options =
            listOf("Leave unassigned" to null) +
                groupDeleteDestinations.map { it.label to it.id },
        onSelect = { screen.deleteGroupDestinationId = it.orEmpty() },
    )
    InlineMessage(
        when {
            deleteGroupDestinationId == UNSELECTED_GROUP_DELETE_DESTINATION ->
                "Choose a destination or leave transactions unassigned."
            deleteGroupDestinationId.isBlank() ->
                "Transactions become unassigned; deleted-category budget amounts are removed."
            else ->
                "Budget amounts and transaction history will move to the selected category."
        },
        InlineMessageKind.INFO,
    )
}
