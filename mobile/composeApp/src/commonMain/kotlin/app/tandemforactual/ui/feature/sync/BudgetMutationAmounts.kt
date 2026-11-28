@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatAmount
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.format.formatMonth
import app.tandemforactual.ui.core.format.formatSignedCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.TransactionItem
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.formatLiveDate
import app.tandemforactual.ui.core.state.formatSignedCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal data class BudgetAmountChange(
    val label: String,
    val before: String,
    val after: String,
)

internal fun budgetMoneyMoveTransitions(
    fromName: String,
    fromBefore: Long,
    toName: String,
    toBefore: Long,
    amount: Long,
    formatAmount: (Long) -> String,
): List<BudgetAmountChange> {
    if (amount <= 0L) return emptyList()
    return listOf(
        BudgetAmountChange(fromName, formatAmount(fromBefore), formatAmount(fromBefore - amount)),
        BudgetAmountChange(toName, formatAmount(toBefore), formatAmount(toBefore + amount)),
    )
}

internal fun budgetMutationAmountChanges(
    mutation: QueuedBudgetReorder?,
    state: AppState,
    mobileSession: TandemMobileSession,
): List<BudgetAmountChange> {
    mutation ?: return emptyList()
    val payload = mutationPayload(mutation) ?: return emptyList()
    val monthId = payload.stringValue("month") ?: return emptyList()
    val formatAmount: (Long) -> String = state::formatCurrency
    return when (mutation.kind) {
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val before = budgetCategoryAssignedMinorFromLastSync(mobileSession, mutation.itemId, monthId) ?: return emptyList()
            val after = payload.longValue("amountMinor") ?: return emptyList()
            listOf(
                BudgetAmountChange(
                    mobileSession.categoryRecords.firstOrNull { it.recordId == mutation.itemId }?.recordName() ?: "Assigned",
                    formatAmount(before),
                    formatAmount(after),
                ),
            )
        }

        BudgetReorderKind.MOVE_BUDGET_MONEY -> {
            val fromId = payload.stringValue("fromCategoryId") ?: return emptyList()
            val toId = payload.stringValue("toCategoryId") ?: return emptyList()
            val amount = payload.longValue("amountMinor") ?: return emptyList()
            val fromBefore = budgetCategoryAssignedMinorFromLastSync(mobileSession, fromId, monthId) ?: return emptyList()
            val toBefore = budgetCategoryAssignedMinorFromLastSync(mobileSession, toId, monthId) ?: return emptyList()
            budgetMoneyMoveTransitions(
                mobileSession.categoryRecords.firstOrNull { it.recordId == fromId }?.recordName() ?: "From category",
                fromBefore,
                mobileSession.categoryRecords.firstOrNull { it.recordId == toId }?.recordName() ?: "To category",
                toBefore,
                amount,
                formatAmount,
            )
        }

        else -> {
            emptyList()
        }
    }
}

internal fun budgetCategoryAssignedMinorFromLastSync(
    mobileSession: TandemMobileSession,
    categoryId: String,
    monthId: String,
): Long? {
    val record =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.recordId == monthId
        } ?: return null
    val month =
        record.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            (groupElement as? JsonObject)
                ?.get("categories")
                ?.let { it as? JsonArray }
                ?.firstNotNullOfOrNull { categoryElement ->
                    (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == categoryId }
                }
        } ?: return null
    return category.longValue("budgeted") ?: category.longValue("assigned")
}

internal fun budgetCategoryCarryoverFromLastSync(
    mobileSession: TandemMobileSession,
    categoryId: String,
    monthId: String,
): Boolean? {
    val record =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.recordId == monthId
        } ?: return null
    val month =
        record.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            (groupElement as? JsonObject)
                ?.get("categories")
                ?.let { it as? JsonArray }
                ?.firstNotNullOfOrNull { categoryElement ->
                    (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == categoryId }
                }
        } ?: return null
    return category.booleanValue("carryover") ?: category.booleanValue("rollover")
}

internal fun budgetMutationTitle(
    action: String,
    itemName: String,
): String =
    when (action) {
        "CREATE_CATEGORY_GROUP" -> "Add category group · $itemName"
        "RENAME_CATEGORY_GROUP", "REFRESH_CATEGORY_GROUP_RENAME" -> "Rename category group · $itemName"
        "CREATE_CATEGORY", "REFRESH_CATEGORY_CREATE" -> "Add category · $itemName"
        "CREATE_PAYEE" -> "Add payee · $itemName"
        "CREATE_ACCOUNT" -> "Add account · $itemName"
        "CLOSE_ACCOUNT" -> "Close account · $itemName"
        "RENAME_ACCOUNT" -> "Rename account · $itemName"
        "REOPEN_ACCOUNT" -> "Reopen account · $itemName"
        "MOVE_ACCOUNT" -> "Reorder account · $itemName"
        "SET_ORDER" -> "Reorder · categories and accounts"
        "SET_CATEGORY_AMOUNT" -> "Assign money · $itemName"
        "SET_CATEGORY_CARRYOVER" -> "Update overspending rollover · $itemName"
        "COPY_BUDGET_MONTH" -> "Copy assignments · $itemName"
        "APPLY_BUDGET_TEMPLATE" -> "Apply template · $itemName"
        "APPLY_GOAL_TEMPLATES" -> "Apply templates · $itemName"
        "HOLD_BUDGET_NEXT_MONTH" -> "Hold money · $itemName"
        "RESET_BUDGET_HOLD" -> "Reset hold · $itemName"
        "MOVE_BUDGET_MONEY" -> "Move money · $itemName"
        "RENAME_PAYEE" -> "Rename payee · $itemName"
        "MERGE_PAYEE" -> "Merge payee · $itemName"
        "DELETE_PAYEE" -> "Delete payee · $itemName"
        "SET_PAYEE_DEFAULT_CATEGORY" -> "Update payee · $itemName"
        "SET_CATEGORY_NOTE" -> "Category note · $itemName"
        "SET_CATEGORY_GROUP_NOTE" -> "Group note · $itemName"
        "SET_ACCOUNT_NOTE" -> "Account note · $itemName"
        "SET_MONTH_NOTE" -> "Month note · $itemName"
        "IMPORT_STATEMENT" -> "Import transactions · $itemName"
        "SET_BUDGET_TYPE" -> "Switch budget type"
        "RENAME_CATEGORY", "REFRESH_CATEGORY_RENAME" -> "Rename category · $itemName"
        "HIDE_CATEGORY", "HIDE_CATEGORY_GROUP" -> "Hide · $itemName"
        "SHOW_CATEGORY", "SHOW_CATEGORY_GROUP" -> "Show · $itemName"
        "MOVE_CATEGORY_TO_GROUP" -> "Move · $itemName"
        "DELETE_CATEGORY" -> "Delete category · $itemName"
        "DELETE_CATEGORY_GROUP" -> "Delete category group · $itemName"
        "CATEGORY", "CATEGORY_GROUP" -> "Reorder · $itemName"
        else -> itemName
    }

internal fun budgetMutationMonthLabel(monthId: String?): String? {
    val parts = monthId?.split('-') ?: return null
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return formatMonth(year, month)
}

internal fun budgetMutationLastSyncedAssignment(
    mutation: QueuedBudgetReorder?,
    operation: OutboxOperationEntity?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String? {
    if (
        mutation?.kind != BudgetReorderKind.SET_CATEGORY_AMOUNT ||
        operation?.status != "rejected" ||
        operation.lastErrorCode != "invalid_request"
    ) {
        return null
    }
    val monthId = mutationPayload(mutation)?.stringValue("month") ?: return null
    val monthRecord =
        mobileSession.selectedBudgetMonthRecord?.takeIf {
            it.budgetId == operation.budgetId && it.recordId == monthId
        } ?: return null
    val month =
        monthRecord.remoteJson?.let { raw ->
            runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: return null
    val groups = month["categoryGroups"] as? kotlinx.serialization.json.JsonArray ?: return null
    val category =
        groups.firstNotNullOfOrNull { groupElement ->
            val categories = (groupElement as? JsonObject)?.get("categories") as? kotlinx.serialization.json.JsonArray
            categories?.firstNotNullOfOrNull { categoryElement ->
                (categoryElement as? JsonObject)?.takeIf { it.stringValue("id") == mutation.itemId }
            }
        } ?: return null
    val amount = category.longValue("budgeted") ?: category.longValue("assigned") ?: 0L
    val period = budgetMutationMonthLabel(monthId)
    return "Last synced in Actual${period?.let { " · $it" }.orEmpty()}: ${state.formatCurrency(amount)} assigned."
}

internal fun transactionIdentity(
    operation: OutboxOperationEntity,
    transaction: TransactionItem?,
    state: AppState,
    mobileSession: TandemMobileSession,
): String? {
    val version =
        (operation.optimisticJson ?: operation.baseSnapshotJson)
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    val date = transaction?.dateLabel ?: version?.stringValue("date")?.let(::formatLiveDate)
    val amount = (transaction?.amountMinorUnits ?: version?.longValue("amount"))?.let(state::formatSignedCurrency)
    val account =
        version?.stringValue("account")?.let { id ->
            mobileSession.accountRecords.firstOrNull { it.recordId == id }?.recordName()
        }
    return listOfNotNull(date, amount, account).joinToString(" · ").ifBlank { null }
}

internal fun mutationPayload(mutation: QueuedBudgetReorder): JsonObject? =
    mutation.payloadJson?.let { raw -> runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() }
