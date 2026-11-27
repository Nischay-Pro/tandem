package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits

internal suspend fun BudgetEdits.renameBudgetCategory(
    budgetId: String,
    categoryId: String,
    name: String,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        host.feedback.budgetActionMessage = "Enter a category name."
        return false
    }
    if (cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Category names can be up to 100 characters."
        return false
    }
    return host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t rename this category. Try again.") {
        host.apiClient.renameBudgetCategory(budgetId, categoryId, cleanName, encryptionPassword, idempotencyKey)
    }
}

internal suspend fun BudgetEdits.createBudgetCategory(
    budgetId: String,
    name: String,
    categoryGroupId: String,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
): String? {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        host.feedback.budgetActionMessage = "Enter a category name."
        return null
    }
    if (cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Category names can be up to 100 characters."
        return null
    }
    var categoryId: String? = null
    val created =
        host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category. Try again.") {
            categoryId = host.apiClient.createBudgetCategory(budgetId, categoryGroupId, cleanName, encryptionPassword, idempotencyKey)
        }
    return categoryId.takeIf { created || host.feedback.budgetActionWasAppliedButRefreshFailed }
}

internal suspend fun BudgetEdits.createBudgetCategoryGroup(
    budgetId: String,
    name: String,
    isIncome: Boolean,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
): String? {
    val cleanName = name.trim()
    if (cleanName.isBlank() || cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Enter a category group name up to 100 characters."
        return null
    }
    var groupId: String? = null
    val created =
        host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category group. Try again.") {
            groupId = host.apiClient.createBudgetCategoryGroup(budgetId, cleanName, isIncome, encryptionPassword, idempotencyKey)
        }
    return groupId.takeIf { created || host.feedback.budgetActionWasAppliedButRefreshFailed }
}

internal suspend fun BudgetEdits.manageBudgetCategoryGroup(
    budgetId: String,
    groupId: String,
    action: String,
    name: String?,
    targetId: String?,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
): Boolean {
    if (action == "rename" && name.isNullOrBlank()) {
        host.feedback.budgetActionMessage = "Enter a category group name."
        return false
    }
    return host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category group. Try again.") {
        host.apiClient.manageBudgetCategoryGroup(budgetId, groupId, action, name?.trim(), targetId, encryptionPassword, idempotencyKey)
    }
}

internal suspend fun BudgetEdits.manageBudgetCategory(
    budgetId: String,
    categoryId: String,
    action: String,
    targetId: String?,
    encryptionPassword: String?,
    targetGroupId: String? = null,
    idempotencyKey: String? = null,
): Boolean =
    host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category. Try again.") {
        host.apiClient.manageBudgetCategory(budgetId, categoryId, action, targetId, encryptionPassword, targetGroupId, idempotencyKey)
    }

internal suspend fun BudgetEdits.deleteBudgetCategory(
    budgetId: String,
    categoryId: String,
    transferCategoryId: String?,
    encryptionPassword: String?,
    idempotencyKey: String? = null,
): Boolean =
    host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t delete this category. Try again.") {
        host.apiClient.deleteBudgetCategory(budgetId, categoryId, transferCategoryId, encryptionPassword, idempotencyKey)
    }
