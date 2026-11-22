package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun BudgetEdits.setBudgetCategoryAmount(
    budgetId: String,
    categoryId: String,
    month: String,
    amountMinor: Long,
    encryptionPassword: String?,
): Boolean {
    if (amountMinor < 0L) {
        host.feedback.budgetActionMessage = "Assigned amounts can’t be negative."
        return false
    }
    if (amountMinor > 9_007_199_254_740_991L) {
        host.feedback.budgetActionMessage = "Enter a smaller assigned amount."
        return false
    }
    return queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.SET_CATEGORY_AMOUNT,
        categoryId,
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put("amountMinor", amountMinor)
        },
        encryptionPassword,
    )
}

internal suspend fun BudgetEdits.setBudgetCategoryCarryover(
    budgetId: String,
    categoryId: String,
    month: String,
    enabled: Boolean,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.SET_CATEGORY_CARRYOVER,
        categoryId,
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put("enabled", enabled)
        },
        encryptionPassword,
    )

internal suspend fun BudgetEdits.copyBudgetMonth(
    budgetId: String,
    month: String,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.COPY_BUDGET_MONTH,
        month,
        "budget-$month",
        buildJsonObject { put("month", month) },
        encryptionPassword,
    )

internal suspend fun BudgetEdits.applyBudgetTemplate(
    budgetId: String,
    month: String,
    assignments: List<Pair<String, Long>>,
    encryptionPassword: String?,
): Boolean {
    if (assignments.isEmpty() || assignments.size > 500 || assignments.any { it.second < 0L }) {
        host.feedback.budgetActionMessage = "Choose a valid budget template."
        return false
    }
    return queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE,
        month,
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put(
                "assignments",
                JsonArray(
                    assignments.map { (categoryId, amountMinor) ->
                        buildJsonObject {
                            put("categoryId", categoryId)
                            put("amountMinor", amountMinor)
                        }
                    },
                ),
            )
        },
        encryptionPassword,
    )
}

internal suspend fun BudgetEdits.holdBudgetForNextMonth(
    budgetId: String,
    month: String,
    amountMinor: Long,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH,
        month,
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put("amountMinor", amountMinor)
        },
        encryptionPassword,
    )

internal suspend fun BudgetEdits.resetBudgetHold(
    budgetId: String,
    month: String,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.RESET_BUDGET_HOLD,
        month,
        "budget-$month",
        buildJsonObject { put("month", month) },
        encryptionPassword,
    )

internal suspend fun BudgetEdits.moveBudgetMoney(
    budgetId: String,
    month: String,
    fromCategoryId: String,
    toCategoryId: String,
    amountMinor: Long,
    encryptionPassword: String?,
): Boolean {
    if (fromCategoryId == toCategoryId || amountMinor <= 0L) {
        host.feedback.budgetActionMessage = "Choose two categories and a positive amount."
        return false
    }
    return queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.MOVE_BUDGET_MONEY,
        "$fromCategoryId:$toCategoryId",
        "budget-$month",
        buildJsonObject {
            put("month", month)
            put("fromCategoryId", fromCategoryId)
            put("toCategoryId", toCategoryId)
            put("amountMinor", amountMinor)
        },
        encryptionPassword,
    )
}
