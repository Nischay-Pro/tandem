@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.feature.sync
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.ui.core.format.formatCurrency
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.orderBatchScopes
import app.tandemforactual.ui.core.state.AppState
import app.tandemforactual.ui.core.state.LiveBudgetMonthSummary
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.formatCurrency
import app.tandemforactual.ui.core.state.longValue
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import kotlinx.serialization.json.JsonArray

internal fun budgetMutationItemName(
    mutation: QueuedBudgetReorder?,
    operation: OutboxOperationEntity?,
    mobileSession: TandemMobileSession,
    liveBudgetMonth: LiveBudgetMonthSummary? = null,
): String {
    val itemId = mutation?.itemId ?: operation?.recordId
    val records =
        mobileSession.categoryRecords + mobileSession.categoryGroupRecords +
            mobileSession.accountRecords + mobileSession.payeeRecords
    val storedName = itemId?.let { id -> records.firstOrNull { it.recordId == id }?.recordName() }
    val monthName =
        itemId?.let { id ->
            liveBudgetMonth?.groups?.firstNotNullOfOrNull { group ->
                group.categories.firstOrNull { it.id == id }?.name
            }
        }
    return storedName ?: monthName ?: mutation?.name ?: "Budget change"
}

internal fun budgetMutationChangeTitle(
    mutation: QueuedBudgetReorder,
    itemName: String,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val payload = mutationPayload(mutation)
    val month = budgetMutationMonthLabel(payload?.stringValue("month"))
    val categoryName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "category"
    }
    val groupName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryGroupRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "group"
    }
    val target = payload?.longValue("amountMinor")?.let(state::formatCurrency)
    return when (mutation.kind) {
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> "Assign ${target ?: "money"} to $itemName${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.SET_CATEGORY_CARRYOVER ->
            if (payload?.booleanValue("enabled") == true) {
                "Carry unused money over · $itemName${month?.let { " · $it" }.orEmpty()}"
            } else {
                "Stop carrying unused money over · $itemName${month?.let { " · $it" }.orEmpty()}"
            }
        BudgetReorderKind.MOVE_BUDGET_MONEY ->
            "Move ${target ?: "money"} from ${categoryName(payload?.stringValue("fromCategoryId"))} to " +
                "${categoryName(payload?.stringValue("toCategoryId"))}${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.COPY_BUDGET_MONTH -> "Copy last month’s assignments${month?.let { " into $it" }.orEmpty()}"
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> {
            val count = (payload?.get("assignments") as? kotlinx.serialization.json.JsonArray)?.size
            "Apply budget template${count?.let { " to $it ${if (it == 1) "category" else "categories"}" }.orEmpty()}" +
                month?.let { " · $it" }.orEmpty()
        }
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "Hold ${target ?: "money"} for next month${month?.let { " · from $it" }.orEmpty()}"
        BudgetReorderKind.RESET_BUDGET_HOLD -> "Release money held for next month${month?.let { " · $it" }.orEmpty()}"
        BudgetReorderKind.RENAME_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_RENAME ->
            "Rename $itemName to ${payload?.stringValue("name") ?: mutation.name ?: "a new name"}"
        BudgetReorderKind.RENAME_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME ->
            "Rename $itemName to ${payload?.stringValue("name") ?: mutation.name ?: "a new name"}"
        BudgetReorderKind.CREATE_CATEGORY -> "Add category $itemName${mutation.parentGroupId?.let { " to ${groupName(it)}" }.orEmpty()}"
        BudgetReorderKind.CREATE_CATEGORY_GROUP -> "Add category group $itemName"
        BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.HIDE_CATEGORY_GROUP -> "Hide $itemName from the budget"
        BudgetReorderKind.SHOW_CATEGORY, BudgetReorderKind.SHOW_CATEGORY_GROUP -> "Show $itemName in the budget"
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP -> "Move $itemName to ${groupName(mutation.targetId ?: mutation.parentGroupId)}"
        BudgetReorderKind.DELETE_CATEGORY -> "Delete category $itemName"
        BudgetReorderKind.DELETE_CATEGORY_GROUP -> "Delete category group $itemName"
        BudgetReorderKind.CREATE_PAYEE -> "Add payee $itemName"
        BudgetReorderKind.CREATE_ACCOUNT -> "Add account $itemName"
        BudgetReorderKind.CLOSE_ACCOUNT -> "Close account $itemName"
        BudgetReorderKind.REOPEN_ACCOUNT -> "Reopen account $itemName"
        BudgetReorderKind.RENAME_ACCOUNT -> "Rename account to ${payload?.stringValue("name") ?: mutation.name ?: itemName}"
        BudgetReorderKind.MOVE_ACCOUNT -> "Change the order of $itemName"
        BudgetReorderKind.RENAME_PAYEE -> "Rename payee to ${payload?.stringValue("name") ?: mutation.name ?: itemName}"
        BudgetReorderKind.MERGE_PAYEE -> "Merge $itemName into ${payload?.stringValue("targetPayeeId") ?: "another payee"}"
        BudgetReorderKind.DELETE_PAYEE -> "Delete payee $itemName"
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY -> "Set $itemName’s default category"
        BudgetReorderKind.SET_CATEGORY_NOTE -> "Update the note for $itemName"
        BudgetReorderKind.IMPORT_STATEMENT -> "Import ${itemName.ifBlank { "transactions" }}"
        BudgetReorderKind.SET_BUDGET_TYPE -> "Switch to ${budgetTypeName(payload?.stringValue("type"))} budgeting"
        BudgetReorderKind.CATEGORY, BudgetReorderKind.CATEGORY_GROUP -> "Change the order of $itemName"
        BudgetReorderKind.SET_ORDER -> {
            val lists = mutation.orderBatchScopes().size
            "Save the new order of ${if (lists == 1) "1 list" else "$lists lists"}"
        }
        BudgetReorderKind.REFRESH_CATEGORY_CREATE, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Finish adding $itemName"
    }
}

internal fun pendingBudgetMutationTitle(itemName: String): String = itemName

internal fun budgetMutationPendingSummary(
    mutation: QueuedBudgetReorder,
    state: AppState,
    mobileSession: TandemMobileSession,
): String {
    val payload = mutationPayload(mutation)
    val categoryName: (String?) -> String = { id ->
        id?.let { key -> mobileSession.categoryRecords.firstOrNull { it.recordId == key }?.recordName() } ?: "category"
    }
    return when (mutation.kind) {
        BudgetReorderKind.MOVE_BUDGET_MONEY -> {
            val amount = payload?.longValue("amountMinor")?.let(state::formatCurrency) ?: "Money"
            val from = categoryName(payload?.stringValue("fromCategoryId"))
            val to = categoryName(payload?.stringValue("toCategoryId"))
            "$amount · $from → $to"
        }
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val target = payload?.longValue("amountMinor")
            val before =
                payload?.stringValue("month")?.let { monthId ->
                    budgetCategoryAssignedMinorFromLastSync(mobileSession, mutation.itemId, monthId)
                }
            val change =
                if (before != null && target != null) {
                    "${state.formatCurrency(before)} → ${state.formatCurrency(target)}"
                } else {
                    "→ ${target?.let(state::formatCurrency) ?: "money"}"
                }
            change
        }
        BudgetReorderKind.SET_CATEGORY_CARRYOVER -> {
            val before =
                payload?.stringValue("month")?.let { monthId ->
                    budgetCategoryCarryoverFromLastSync(mobileSession, mutation.itemId, monthId)
                }
            val after = payload?.booleanValue("enabled")
            if (before != null && after != null) {
                "${if (before) "On" else "Off"} → ${if (after) "On" else "Off"}"
            } else {
                if (after == true) "Overspending rollover on" else "Overspending rollover off"
            }
        }
        BudgetReorderKind.CATEGORY,
        BudgetReorderKind.CATEGORY_GROUP,
        BudgetReorderKind.MOVE_ACCOUNT,
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
        BudgetReorderKind.SET_ORDER,
        -> "Reorder"
        BudgetReorderKind.RENAME_CATEGORY,
        BudgetReorderKind.RENAME_CATEGORY_GROUP,
        BudgetReorderKind.RENAME_ACCOUNT,
        BudgetReorderKind.RENAME_PAYEE,
        BudgetReorderKind.REFRESH_CATEGORY_RENAME,
        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
        -> "Rename → ${payload?.stringValue("name") ?: mutation.name ?: "new name"}"
        BudgetReorderKind.CREATE_CATEGORY, BudgetReorderKind.REFRESH_CATEGORY_CREATE -> "Add category"
        BudgetReorderKind.CREATE_CATEGORY_GROUP, BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE -> "Add group"
        BudgetReorderKind.CREATE_ACCOUNT -> "Add account"
        BudgetReorderKind.CREATE_PAYEE -> "Add payee"
        BudgetReorderKind.HIDE_CATEGORY, BudgetReorderKind.HIDE_CATEGORY_GROUP -> "Hide"
        BudgetReorderKind.SHOW_CATEGORY, BudgetReorderKind.SHOW_CATEGORY_GROUP -> "Show"
        BudgetReorderKind.DELETE_CATEGORY, BudgetReorderKind.DELETE_CATEGORY_GROUP,
        BudgetReorderKind.DELETE_PAYEE,
        -> "Delete"
        BudgetReorderKind.COPY_BUDGET_MONTH -> "Copy last month"
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> "Apply template"
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> "Hold ${payload?.longValue("amountMinor")?.let(state::formatCurrency) ?: "money"}"
        BudgetReorderKind.RESET_BUDGET_HOLD -> "Release hold"
        BudgetReorderKind.CLOSE_ACCOUNT -> "Close account"
        BudgetReorderKind.REOPEN_ACCOUNT -> "Reopen account"
        BudgetReorderKind.MERGE_PAYEE -> "Merge payee"
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY -> "Set default category"
        BudgetReorderKind.SET_CATEGORY_NOTE -> "Edit note"
        BudgetReorderKind.IMPORT_STATEMENT -> "Import transactions"
        BudgetReorderKind.SET_BUDGET_TYPE -> "Switch to ${budgetTypeName(payload?.stringValue("type"))}"
    }
}

private fun budgetTypeName(type: String?): String = if (type == "tracking") "tracking" else "envelope"
