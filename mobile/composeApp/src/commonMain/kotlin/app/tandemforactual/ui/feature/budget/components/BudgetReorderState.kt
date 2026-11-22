package app.tandemforactual.ui.feature.budget.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.BUDGET_GROUP_ORDER_SCOPE
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.enqueueBudgetReorder
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.feature.budget.itemTargetIdAfterMove
import app.tandemforactual.ui.feature.budget.reorderItemToIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Which budget group or category is being reordered, and the drag state of each list. */
@Stable
internal class BudgetReorderState(
    groupOrderId: MutableState<String?>,
    categoryOrderId: MutableState<String?>,
    categoryGroupId: MutableState<String?>,
    val groupDrag: DragReorderState,
    val categoryDrag: DragReorderState,
) {
    var selectedGroupOrderId by groupOrderId
    var selectedCategoryOrderId by categoryOrderId
    var selectedCategoryGroupId by categoryGroupId
}

@Composable
internal fun rememberBudgetReorderState(
    budgetId: String,
    monthId: String,
): BudgetReorderState {
    val groupOrderId = rememberSaveable(budgetId) { mutableStateOf<String?>(null) }
    val categoryOrderId = rememberSaveable(budgetId) { mutableStateOf<String?>(null) }
    val categoryGroupId = rememberSaveable(budgetId) { mutableStateOf<String?>(null) }
    val categoryDrag = remember(budgetId) { DragReorderState() }
    val groupDrag = remember(budgetId, monthId) { DragReorderState() }
    return remember(groupOrderId, categoryOrderId, categoryGroupId, groupDrag, categoryDrag) {
        BudgetReorderState(groupOrderId, categoryOrderId, categoryGroupId, groupDrag, categoryDrag)
    }
}

/** Saves group and category moves made on the budget screen. */
internal class BudgetReorderActions(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val scope: CoroutineScope,
    private val groups: List<LiveCategoryGroupSummary>,
    private val reorder: BudgetReorderState,
) {
    private val groupDrag = reorder.groupDrag
    private val budgetCategoryDrag = reorder.categoryDrag

    fun latestQueuedGroupOrder(): List<String> =
        budgetOrderForScreen(
            groups.map { it.id },
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE),
        )

    private fun latestQueuedCategoryOrder(group: LiveCategoryGroupSummary): List<String> =
        budgetOrderForScreen(
            group.categories.map { it.id },
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, group.id),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, group.id),
        )

    private fun saveGroupOrder(
        groupId: String,
        requestedOrder: List<String>,
    ) {
        // Actual always lists income groups after expense groups, so keep that on screen too.
        val incomeGroupIds = groups.filter { it.isIncome }.mapTo(mutableSetOf()) { it.id }
        val nextOrder = requestedOrder.filterNot { it in incomeGroupIds } + requestedOrder.filter { it in incomeGroupIds }
        if (nextOrder == latestQueuedGroupOrder()) {
            // Nothing changes (for example, Income dropped among expense groups).
            groupDrag.releaseHeldDrop()
            return
        }
        scope.launch {
            mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, BUDGET_GROUP_ORDER_SCOPE)
            mobileSession.feedback.clearBudgetActionMessage()
            val saved =
                mobileSession.budgetQueue.enqueueBudgetReorder(
                    state.selectedBudgetId,
                    BUDGET_GROUP_ORDER_SCOPE,
                    BudgetReorderKind.CATEGORY_GROUP,
                    groupId,
                    itemTargetIdAfterMove(nextOrder, groupId),
                    nextOrder,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    baseOrder = groups.map { it.id },
                )
            if (!saved) groupDrag.releaseHeldDrop()
        }
    }

    fun enqueueGroupMove(
        groupId: String,
        direction: Int,
    ) {
        val oldOrder = groupDrag.currentOrder(latestQueuedGroupOrder())
        val nextOrder = reorderItemToIndex(oldOrder, groupId, oldOrder.indexOf(groupId) + direction)
        if (oldOrder == nextOrder) return
        saveGroupOrder(groupId, nextOrder)
    }

    fun selectGroup(groupId: String) {
        reorder.selectedCategoryOrderId = null
        reorder.selectedCategoryGroupId = null
        reorder.selectedGroupOrderId = groupId
    }

    fun startGroupDrag(groupId: String) {
        selectGroup(groupId)
        groupDrag.start(groupDrag.currentOrder(latestQueuedGroupOrder()), groupId)
    }

    fun dropGroupDrag(groupId: String) {
        val result = groupDrag.finish() ?: return
        if (result.startingOrder != result.finalOrder) {
            saveGroupOrder(groupId, result.finalOrder)
        }
    }

    fun startCategoryDrag(
        group: LiveCategoryGroupSummary,
        categoryId: String,
    ) {
        reorder.selectedGroupOrderId = null
        reorder.selectedCategoryGroupId = group.id
        reorder.selectedCategoryOrderId = categoryId
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, group.id)
        mobileSession.feedback.clearBudgetActionMessage()
        budgetCategoryDrag.start(budgetCategoryDrag.currentOrder(latestQueuedCategoryOrder(group)), categoryId)
    }

    fun dragCategoryBy(
        categoryId: String,
        delta: Float,
    ) {
        if (budgetCategoryDrag.draggedItemId == categoryId) {
            budgetCategoryDrag.dragBy(delta)
        }
    }

    fun dropCategoryDrag(
        group: LiveCategoryGroupSummary,
        categoryId: String,
    ) {
        val serverCategoryOrder = group.categories.map { it.id }
        budgetCategoryDrag.finish()?.let { result ->
            if (result.itemId == categoryId && result.finalOrder != result.startingOrder) {
                scope.launch {
                    val saved =
                        mobileSession.budgetQueue.enqueueBudgetReorder(
                            state.selectedBudgetId,
                            group.id,
                            BudgetReorderKind.CATEGORY,
                            result.itemId,
                            itemTargetIdAfterMove(result.finalOrder, result.itemId),
                            result.finalOrder,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                            baseOrder = serverCategoryOrder,
                        )
                    if (!saved) budgetCategoryDrag.releaseHeldDrop()
                }
            }
        }
    }

    fun moveCategory(
        group: LiveCategoryGroupSummary,
        categoryId: String,
        direction: Int,
    ) {
        val serverCategoryOrder = group.categories.map { it.id }
        val currentOrder = budgetCategoryDrag.currentOrder(latestQueuedCategoryOrder(group))
        val nextOrder = reorderItemToIndex(currentOrder, categoryId, currentOrder.indexOf(categoryId) + direction)
        if (nextOrder == currentOrder) return
        scope.launch {
            mobileSession.budgetQueue.enqueueBudgetReorder(
                state.selectedBudgetId,
                group.id,
                BudgetReorderKind.CATEGORY,
                categoryId,
                itemTargetIdAfterMove(nextOrder, categoryId),
                nextOrder,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                baseOrder = serverCategoryOrder,
            )
        }
    }
}
