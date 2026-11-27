package app.tandemforactual.ui.core.session.budget

import app.tandemforactual.ui.core.session.BudgetReorderKind
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Queues a category's note; Actual keeps it in its notes table under the category's id, and an empty note clears it. */
internal suspend fun BudgetEdits.setBudgetCategoryNote(
    budgetId: String,
    categoryId: String,
    note: String,
    encryptionPassword: String?,
): Boolean = queueNote(budgetId, BudgetReorderKind.SET_CATEGORY_NOTE, categoryId, "categories", note, encryptionPassword)

internal suspend fun BudgetEdits.setBudgetCategoryGroupNote(
    budgetId: String,
    groupId: String,
    note: String,
    encryptionPassword: String?,
): Boolean = queueNote(budgetId, BudgetReorderKind.SET_CATEGORY_GROUP_NOTE, groupId, NOTES_SCOPE, note, encryptionPassword)

internal suspend fun BudgetEdits.setBudgetAccountNote(
    budgetId: String,
    accountId: String,
    note: String,
    encryptionPassword: String?,
): Boolean = queueNote(budgetId, BudgetReorderKind.SET_ACCOUNT_NOTE, accountId, NOTES_SCOPE, note, encryptionPassword)

/** [month] is `YYYY-MM`; Actual stores it as `budget-YYYY-MM`. [monthLabel] names it in the pending changes list. */
internal suspend fun BudgetEdits.setBudgetMonthNote(
    budgetId: String,
    month: String,
    note: String,
    encryptionPassword: String?,
    monthLabel: String? = null,
): Boolean = queueNote(budgetId, BudgetReorderKind.SET_MONTH_NOTE, month, NOTES_SCOPE, note, encryptionPassword, monthLabel)

private const val NOTES_SCOPE = "notes"

private suspend fun BudgetEdits.queueNote(
    budgetId: String,
    kind: BudgetReorderKind,
    itemId: String,
    scopeId: String,
    note: String,
    encryptionPassword: String?,
    name: String? = null,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        kind,
        itemId,
        scopeId,
        buildJsonObject { put("note", note.trim()) },
        encryptionPassword,
        name,
    )
