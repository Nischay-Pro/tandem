package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.BUDGET_GROUP_ORDER_SCOPE
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.enqueueIfMissing
import app.tandemforactual.ui.core.session.resolveCategoryGroupId
import app.tandemforactual.ui.core.session.resolveCategoryId
import kotlin.uuid.Uuid

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryVisibilityChange(
    budgetId: String,
    categoryId: String,
    groupId: String,
    hidden: Boolean,
    currentOrder: List<String>,
    encryptionPassword: String?,
): Boolean {
    host.feedback.budgetActionMessage = null
    val scopeId = groupId
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = scopeId,
                kind = if (hidden) BudgetReorderKind.HIDE_CATEGORY else BudgetReorderKind.SHOW_CATEGORY,
                itemId = categoryId,
                targetId = null,
                desiredOrder = currentOrder,
                encryptionPassword = encryptionPassword,
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryGroupCreate(
    budgetId: String,
    name: String,
    isIncome: Boolean,
    currentOrder: List<String>,
    encryptionPassword: String?,
): String? {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Enter a category group name up to 100 characters."
        return null
    }
    if (budgetId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before adding a category group."
        return null
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return null
    }
    val localGroupId = "local-${Uuid.random()}"
    host.feedback.budgetActionMessage = null
    val persisted =
        persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = BUDGET_GROUP_ORDER_SCOPE,
                    kind = BudgetReorderKind.CREATE_CATEGORY_GROUP,
                    itemId = localGroupId,
                    targetId = null,
                    desiredOrder = currentOrder + localGroupId,
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                    isIncome = isIncome,
                ),
            )
        }
    return localGroupId.takeIf { persisted }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryGroupRename(
    budgetId: String,
    groupId: String,
    name: String,
    currentOrder: List<String>,
    encryptionPassword: String?,
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Enter a category group name up to 100 characters."
        return false
    }
    if (budgetId.isBlank() || groupId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before renaming this category group."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return false
    }
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = BUDGET_GROUP_ORDER_SCOPE,
                kind = BudgetReorderKind.RENAME_CATEGORY_GROUP,
                itemId = groupId,
                targetId = null,
                desiredOrder = currentOrder,
                encryptionPassword = encryptionPassword,
                name = cleanName,
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryGroupDelete(
    budgetId: String,
    groupId: String,
    transferCategoryId: String?,
    currentOrder: List<String>,
    encryptionPassword: String?,
): Boolean {
    if (budgetId.isBlank() || groupId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before deleting this category group."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) return false
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = BUDGET_GROUP_ORDER_SCOPE,
                kind = BudgetReorderKind.DELETE_CATEGORY_GROUP,
                itemId = queue.resolveCategoryGroupId(budgetId, groupId),
                targetId = transferCategoryId?.let { queue.resolveCategoryId(budgetId, it) },
                desiredOrder = currentOrder,
                encryptionPassword = encryptionPassword,
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryCreate(
    budgetId: String,
    groupId: String,
    name: String,
    currentOrder: List<String>,
    encryptionPassword: String?,
): String? {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Enter a category name up to 100 characters."
        return null
    }
    if (budgetId.isBlank() || groupId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before adding a category."
        return null
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return null
    }
    val localCategoryId = "local-category-${Uuid.random()}"
    host.feedback.budgetActionMessage = null
    val persisted =
        persistBudgetReorderQueueMutation { queue ->
            val resolvedGroupId = queue.resolveCategoryGroupId(budgetId, groupId)
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = groupId,
                    kind = BudgetReorderKind.CREATE_CATEGORY,
                    itemId = localCategoryId,
                    targetId = null,
                    desiredOrder = currentOrder + localCategoryId,
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                    parentGroupId = resolvedGroupId,
                ),
            )
        }
    return localCategoryId.takeIf { persisted }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryRename(
    budgetId: String,
    categoryId: String,
    name: String,
    encryptionPassword: String?,
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Enter a category name up to 100 characters."
        return false
    }
    if (budgetId.isBlank() || categoryId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before renaming this category."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return false
    }
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = "category-$categoryId",
                kind = BudgetReorderKind.RENAME_CATEGORY,
                itemId = queue.resolveCategoryId(budgetId, categoryId),
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = encryptionPassword,
                name = cleanName,
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryMoveToGroup(
    budgetId: String,
    categoryId: String,
    groupId: String,
    encryptionPassword: String?,
): Boolean {
    if (budgetId.isBlank() || categoryId.isBlank() || groupId.isBlank()) {
        host.feedback.budgetActionMessage = "Choose a category group."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return false
    }
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = "category-$categoryId",
                kind = BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
                itemId = queue.resolveCategoryId(budgetId, categoryId),
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = encryptionPassword,
                parentGroupId = queue.resolveCategoryGroupId(budgetId, groupId),
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryDelete(
    budgetId: String,
    categoryId: String,
    transferCategoryId: String?,
    encryptionPassword: String?,
): Boolean {
    if (budgetId.isBlank() || categoryId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before deleting this category."
        return false
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return false
    }
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = "category-$categoryId",
                kind = BudgetReorderKind.DELETE_CATEGORY,
                itemId = queue.resolveCategoryId(budgetId, categoryId),
                targetId = transferCategoryId?.let { queue.resolveCategoryId(budgetId, it) },
                desiredOrder = emptyList(),
                encryptionPassword = encryptionPassword,
            ),
        )
    }
}

internal suspend fun BudgetEditQueue.enqueueBudgetCategoryGroupVisibilityChange(
    budgetId: String,
    groupId: String,
    hidden: Boolean,
    encryptionPassword: String?,
): Boolean {
    if (budgetId.isBlank() || groupId.isBlank() || host.refuseWriteInReadOnlyMode()) return false
    host.feedback.budgetActionMessage = null
    return persistBudgetReorderQueueMutation { queue ->
        queue.enqueueIfMissing(
            QueuedBudgetReorder(
                budgetId = budgetId,
                scopeId = BUDGET_GROUP_ORDER_SCOPE,
                kind = if (hidden) BudgetReorderKind.HIDE_CATEGORY_GROUP else BudgetReorderKind.SHOW_CATEGORY_GROUP,
                itemId = queue.resolveCategoryGroupId(budgetId, groupId),
                targetId = null,
                desiredOrder = emptyList(),
                encryptionPassword = encryptionPassword,
            ),
        )
    }
}
