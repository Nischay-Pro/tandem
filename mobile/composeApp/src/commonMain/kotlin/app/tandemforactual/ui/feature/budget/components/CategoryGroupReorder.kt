package app.tandemforactual.ui.feature.budget.components

import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.DragReorderState
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.budget.budgetReorderMovesFor
import app.tandemforactual.ui.core.session.budget.budgetReorderStatus
import app.tandemforactual.ui.core.session.budget.clearBudgetReorderMessage
import app.tandemforactual.ui.core.session.budget.enqueueBudgetCategoryVisibilityChange
import app.tandemforactual.ui.core.session.budget.enqueueBudgetReorder
import app.tandemforactual.ui.core.session.budgetOrderForScreen
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.itemTargetIdAfterMove
import app.tandemforactual.ui.core.state.reorderItemToIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Saves category moves, drags and visibility changes made on the category group screen. */
internal class CategoryGroupReorder(
    private val state: AppState,
    private val mobileSession: TandemMobileSession,
    private val scope: CoroutineScope,
    private val screen: CategoryGroupScreenState,
    val categoryScopeId: String,
    val serverCategoryOrder: List<String>,
    val dragReorder: DragReorderState,
    val hiddenCategoryScopeId: String,
    private val hiddenCategoryIds: List<String>,
    val hiddenDrag: DragReorderState,
) {
    fun currentCategoryOrder(): List<String> =
        dragReorder.currentOrder(
            budgetOrderForScreen(
                serverCategoryOrder,
                mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, categoryScopeId),
                mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, categoryScopeId),
            ),
        )

    fun enqueueCategoryMove(
        itemId: String,
        direction: Int,
    ) {
        val oldOrder = currentCategoryOrder()
        val nextOrder = reorderItemToIndex(oldOrder, itemId, oldOrder.indexOf(itemId) + direction)
        if (oldOrder == nextOrder) return
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, categoryScopeId)
        mobileSession.feedback.clearBudgetActionMessage()
        scope.launch {
            mobileSession.budgetQueue.enqueueBudgetReorder(
                state.selectedBudgetId,
                categoryScopeId,
                BudgetReorderKind.CATEGORY,
                itemId,
                itemTargetIdAfterMove(nextOrder, itemId),
                nextOrder,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                baseOrder = serverCategoryOrder,
            )
        }
    }

    fun startDrag(categoryId: String) {
        screen.selectedCategoryOrderId = categoryId
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, categoryScopeId)
        mobileSession.feedback.clearBudgetActionMessage()
        dragReorder.start(currentCategoryOrder(), categoryId)
    }

    fun dragBy(
        categoryId: String,
        delta: Float,
    ) {
        if (dragReorder.draggedItemId == categoryId) {
            dragReorder.dragBy(delta)
        }
    }

    fun dropDrag(categoryId: String) {
        dragReorder.finish()?.let { result ->
            if (result.itemId == categoryId && result.finalOrder != result.startingOrder) {
                scope.launch {
                    val saved =
                        mobileSession.budgetQueue.enqueueBudgetReorder(
                            state.selectedBudgetId,
                            categoryScopeId,
                            BudgetReorderKind.CATEGORY,
                            result.itemId,
                            itemTargetIdAfterMove(result.finalOrder, result.itemId),
                            result.finalOrder,
                            state.encryptedPassword.takeIf { it.isNotBlank() },
                            baseOrder = serverCategoryOrder,
                        )
                    if (!saved) dragReorder.releaseHeldDrop()
                }
            }
        }
    }

    fun setCategoryHidden(
        categoryId: String,
        groupId: String,
        hidden: Boolean,
        onSaved: () -> Unit = {},
    ) {
        scope.launch {
            val saved =
                mobileSession.budgetQueue.enqueueBudgetCategoryVisibilityChange(
                    budgetId = state.selectedBudgetId,
                    categoryId = categoryId,
                    groupId = groupId,
                    hidden = hidden,
                    currentOrder = currentCategoryOrder(),
                    encryptionPassword = state.encryptedPassword.takeIf { it.isNotBlank() },
                )
            if (saved) onSaved()
        }
    }

    fun latestQueuedHiddenCategoryOrder(): List<String> =
        budgetOrderForScreen(
            hiddenCategoryIds,
            mobileSession.budgetQueue.budgetReorderStatus(state.selectedBudgetId, hiddenCategoryScopeId),
            mobileSession.budgetQueue.budgetReorderMovesFor(state.selectedBudgetId, hiddenCategoryScopeId),
        )

    private fun saveHiddenOrder(
        recordId: String,
        nextOrder: List<String>,
    ) {
        if (nextOrder == latestQueuedHiddenCategoryOrder()) return
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, hiddenCategoryScopeId)
        scope.launch {
            mobileSession.budgetQueue.enqueueBudgetReorder(
                state.selectedBudgetId,
                hiddenCategoryScopeId,
                BudgetReorderKind.CATEGORY,
                recordId,
                itemTargetIdAfterMove(nextOrder, recordId),
                nextOrder,
                state.encryptedPassword.takeIf { it.isNotBlank() },
                baseOrder = hiddenCategoryIds,
            )
        }
    }

    fun moveHiddenCategory(
        recordId: String,
        direction: Int,
    ) {
        val currentOrder = hiddenDrag.currentOrder(latestQueuedHiddenCategoryOrder())
        saveHiddenOrder(
            recordId,
            reorderItemToIndex(currentOrder, recordId, currentOrder.indexOf(recordId) + direction),
        )
    }

    fun startHiddenDrag(recordId: String) {
        screen.selectedCategoryOrderId = recordId
        mobileSession.budgetQueue.clearBudgetReorderMessage(state.selectedBudgetId, hiddenCategoryScopeId)
        mobileSession.feedback.clearBudgetActionMessage()
        hiddenDrag.start(hiddenDrag.currentOrder(latestQueuedHiddenCategoryOrder()), recordId)
    }

    fun dragHiddenBy(
        recordId: String,
        delta: Float,
    ) {
        if (hiddenDrag.draggedItemId == recordId) hiddenDrag.dragBy(delta)
    }

    fun dropHiddenDrag(recordId: String) {
        val result = hiddenDrag.finish(latestQueuedHiddenCategoryOrder()) ?: return
        if (result.itemId != recordId || result.finalOrder == result.startingOrder) return
        scope.launch {
            val saved =
                mobileSession.budgetQueue.enqueueBudgetReorder(
                    state.selectedBudgetId,
                    hiddenCategoryScopeId,
                    BudgetReorderKind.CATEGORY,
                    result.itemId,
                    itemTargetIdAfterMove(result.finalOrder, result.itemId),
                    result.finalOrder,
                    state.encryptedPassword.takeIf { it.isNotBlank() },
                    baseOrder = hiddenCategoryIds,
                )
            if (!saved) hiddenDrag.releaseHeldDrop()
        }
    }
}
