package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.importBudgetAccountTransactions
import app.tandemforactual.api.client.setBudgetType
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.optionalString
import app.tandemforactual.ui.core.session.requirePayload
import app.tandemforactual.ui.core.session.requiredAssignments
import app.tandemforactual.ui.core.session.requiredBoolean
import app.tandemforactual.ui.core.session.requiredLong
import app.tandemforactual.ui.core.session.requiredStatementItems
import app.tandemforactual.ui.core.session.requiredString
import kotlinx.serialization.json.JsonObject

/** What sending one queued edit produced: whether Actual applied it, and the ids Actual gave anything it created. */
internal class Dispatched(
    val moved: Boolean,
    val categoryGroupId: String? = null,
    val categoryId: String? = null,
    val payeeId: String? = null,
    val accountId: String? = null,
)

/** Sends one queued edit to Actual through the edit that matches its kind. */
internal suspend fun BudgetEditQueue.dispatch(request: QueuedBudgetReorder): Dispatched =
    when (request.kind) {
        BudgetReorderKind.CATEGORY,
        BudgetReorderKind.HIDE_CATEGORY,
        BudgetReorderKind.SHOW_CATEGORY,
        BudgetReorderKind.CREATE_CATEGORY_GROUP,
        BudgetReorderKind.RENAME_CATEGORY_GROUP,
        BudgetReorderKind.DELETE_CATEGORY_GROUP,
        BudgetReorderKind.CATEGORY_GROUP,
        BudgetReorderKind.CREATE_CATEGORY,
        BudgetReorderKind.CREATE_PAYEE,
        BudgetReorderKind.CREATE_ACCOUNT,
        BudgetReorderKind.RENAME_CATEGORY,
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP,
        BudgetReorderKind.DELETE_CATEGORY,
        BudgetReorderKind.HIDE_CATEGORY_GROUP,
        BudgetReorderKind.SHOW_CATEGORY_GROUP,
        -> sendStructureEdit(request)
        BudgetReorderKind.SET_CATEGORY_AMOUNT,
        BudgetReorderKind.SET_CATEGORY_CARRYOVER,
        BudgetReorderKind.COPY_BUDGET_MONTH,
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE,
        -> Dispatched(sendAssignmentEdit(request))
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH,
        BudgetReorderKind.RESET_BUDGET_HOLD,
        BudgetReorderKind.MOVE_BUDGET_MONEY,
        -> Dispatched(sendMoneyMove(request))
        BudgetReorderKind.CLOSE_ACCOUNT,
        BudgetReorderKind.RENAME_ACCOUNT,
        BudgetReorderKind.REOPEN_ACCOUNT,
        -> Dispatched(sendAccountEdit(request))
        BudgetReorderKind.SET_ORDER,
        BudgetReorderKind.MOVE_ACCOUNT,
        -> Dispatched(sendAccountOrderEdit(request))
        BudgetReorderKind.RENAME_PAYEE,
        BudgetReorderKind.MERGE_PAYEE,
        BudgetReorderKind.DELETE_PAYEE,
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY,
        BudgetReorderKind.IMPORT_STATEMENT,
        -> Dispatched(sendPayeeEdit(request))
        BudgetReorderKind.SET_CATEGORY_NOTE -> Dispatched(sendCategoryNote(request))
        BudgetReorderKind.SET_BUDGET_TYPE -> Dispatched(sendBudgetType(request))
        BudgetReorderKind.REFRESH_CATEGORY_GROUP_CREATE,
        BudgetReorderKind.REFRESH_CATEGORY_GROUP_RENAME,
        BudgetReorderKind.REFRESH_CATEGORY_CREATE,
        BudgetReorderKind.REFRESH_CATEGORY_RENAME,
        -> Dispatched(false)
    }

/** Category, group, payee and account structure; creating something records the id Actual gave it. */
private suspend fun BudgetEditQueue.sendStructureEdit(request: QueuedBudgetReorder): Dispatched {
    val key = request.operationId
    val password = request.encryptionPassword

    suspend fun category(
        action: String,
        target: String?,
        group: String? = null,
    ) = edits.manageBudgetCategory(request.budgetId, request.itemId, action, target, password, group, key)

    suspend fun group(
        action: String,
        name: String?,
        target: String?,
    ) = edits.manageBudgetCategoryGroup(request.budgetId, request.itemId, action, name, target, password, key)
    return when (request.kind) {
        BudgetReorderKind.CATEGORY -> Dispatched(category("move", request.targetId))
        BudgetReorderKind.HIDE_CATEGORY -> Dispatched(category("hide", null))
        BudgetReorderKind.SHOW_CATEGORY -> Dispatched(category("show", null))
        BudgetReorderKind.MOVE_CATEGORY_TO_GROUP -> Dispatched(category("move", null, request.parentGroupId))
        BudgetReorderKind.RENAME_CATEGORY ->
            Dispatched(edits.renameBudgetCategory(request.budgetId, request.itemId, request.name.orEmpty(), password, key))
        BudgetReorderKind.DELETE_CATEGORY ->
            Dispatched(edits.deleteBudgetCategory(request.budgetId, request.itemId, request.targetId, password, key))
        BudgetReorderKind.RENAME_CATEGORY_GROUP -> Dispatched(group("rename", request.name, null))
        BudgetReorderKind.DELETE_CATEGORY_GROUP -> Dispatched(group("delete", null, request.targetId))
        BudgetReorderKind.CATEGORY_GROUP -> Dispatched(group("move", null, request.targetId))
        BudgetReorderKind.HIDE_CATEGORY_GROUP -> Dispatched(group("hide", null, null))
        BudgetReorderKind.SHOW_CATEGORY_GROUP -> Dispatched(group("show", null, null))
        BudgetReorderKind.CREATE_CATEGORY_GROUP ->
            edits
                .createBudgetCategoryGroup(request.budgetId, request.name.orEmpty(), request.isIncome, password, key)
                .let { Dispatched(it != null, categoryGroupId = it) }
        BudgetReorderKind.CREATE_CATEGORY ->
            edits
                .createBudgetCategory(request.budgetId, request.name.orEmpty(), request.parentGroupId.orEmpty(), password, key)
                .let { Dispatched(it != null, categoryId = it) }
        BudgetReorderKind.CREATE_PAYEE -> edits.createQueuedBudgetPayee(request).let { Dispatched(it != null, payeeId = it) }
        BudgetReorderKind.CREATE_ACCOUNT -> edits.createQueuedBudgetAccount(request).let { Dispatched(it != null, accountId = it) }
        else -> error("Not a structure edit: ${request.kind}")
    }
}

/** Assigning money: amounts, carryover, copying a month and applying a template. */
private suspend fun BudgetEditQueue.sendAssignmentEdit(request: QueuedBudgetReorder): Boolean =
    when (request.kind) {
        BudgetReorderKind.SET_CATEGORY_AMOUNT -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t update the assigned amount.",
                null,
            ) {
                host.apiClient.setBudgetCategoryAmount(
                    request.budgetId,
                    request.itemId,
                    payload.requiredString("month"),
                    payload.requiredLong("amountMinor"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.SET_CATEGORY_CARRYOVER -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t update overspending rollover.",
                null,
            ) {
                host.apiClient.setBudgetCategoryCarryover(
                    request.budgetId,
                    request.itemId,
                    payload.requiredString("month"),
                    payload.requiredBoolean("enabled"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.COPY_BUDGET_MONTH -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t copy last month’s assignments.",
                null,
            ) {
                host.apiClient.copyBudgetMonth(
                    request.budgetId,
                    payload.requiredString("month"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.APPLY_BUDGET_TEMPLATE -> {
            val payload = request.requirePayload()
            val assignments = payload.requiredAssignments()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t apply this template.",
                null,
            ) {
                host.apiClient.applyBudgetTemplate(
                    request.budgetId,
                    payload.requiredString("month"),
                    assignments,
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        else -> error("Not a AssignmentEdit edit: ${request.kind}")
    }

/** Holding money for next month, resetting a hold, and moving money between categories. */
private suspend fun BudgetEditQueue.sendMoneyMove(request: QueuedBudgetReorder): Boolean =
    when (request.kind) {
        BudgetReorderKind.HOLD_BUDGET_NEXT_MONTH -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t hold this money for next month.",
                null,
            ) {
                host.apiClient.holdBudgetForNextMonth(
                    request.budgetId,
                    payload.requiredString("month"),
                    payload.requiredLong("amountMinor"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.RESET_BUDGET_HOLD -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t reset the next-month hold.",
                null,
            ) {
                host.apiClient.resetBudgetHold(
                    request.budgetId,
                    payload.requiredString("month"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.MOVE_BUDGET_MONEY -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t move money.",
                null,
            ) {
                host.apiClient.moveMoneyBetweenCategories(
                    request.budgetId,
                    payload.requiredString("month"),
                    payload.requiredString("fromCategoryId"),
                    payload.requiredString("toCategoryId"),
                    payload.requiredLong("amountMinor"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        else -> error("Not a MoneyMove edit: ${request.kind}")
    }

/** Closing, renaming and reopening accounts. */
private suspend fun BudgetEditQueue.sendAccountEdit(request: QueuedBudgetReorder): Boolean =
    when (request.kind) {
        BudgetReorderKind.CLOSE_ACCOUNT -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t close this account.",
                null,
            ) {
                host.apiClient.closeBudgetAccount(
                    request.budgetId,
                    request.itemId,
                    payload.optionalString("transferAccountId"),
                    payload.optionalString("transferCategoryId"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.RENAME_ACCOUNT -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t rename this account.",
                null,
            ) {
                host.apiClient.renameBudgetAccount(
                    request.budgetId,
                    request.itemId,
                    payload.requiredString("name"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.REOPEN_ACCOUNT ->
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t reopen this account.",
                null,
            ) {
                host.apiClient.reopenBudgetAccount(
                    request.budgetId,
                    request.itemId,
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        else -> error("Not an account edit: ${request.kind}")
    }

/** Reordering accounts. */
private suspend fun BudgetEditQueue.sendAccountOrderEdit(request: QueuedBudgetReorder): Boolean =
    when (request.kind) {
        BudgetReorderKind.SET_ORDER -> {
            val order =
                request.requirePayload()["order"] as? JsonObject
                    ?: throw IllegalStateException("A saved reorder could not be read.")
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t save the new order.",
                null,
            ) {
                host.apiClient.setBudgetOrder(
                    request.budgetId,
                    order,
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.MOVE_ACCOUNT -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t reorder this account.",
                null,
            ) {
                host.apiClient.moveBudgetAccount(
                    request.budgetId,
                    request.itemId,
                    payload.optionalString("targetAccountId"),
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        else -> error("Not an account edit: ${request.kind}")
    }

/** A category's note, saved in Actual's notes under the category's id. */
private suspend fun BudgetEditQueue.sendCategoryNote(request: QueuedBudgetReorder): Boolean {
    val payload = request.requirePayload()
    return host.feedback.performBudgetAction(
        request.budgetId,
        request.encryptionPassword,
        "Couldn’t save this category’s note.",
        null,
    ) {
        host.apiClient.setBudgetCategoryNote(
            request.budgetId,
            request.itemId,
            payload.requiredString("note"),
            request.encryptionPassword,
            request.operationId,
        )
    }
}

/** Switching between envelope and tracking budgeting. */
private suspend fun BudgetEditQueue.sendBudgetType(request: QueuedBudgetReorder): Boolean {
    val payload = request.requirePayload()
    return host.feedback.performBudgetAction(
        request.budgetId,
        request.encryptionPassword,
        "Couldn’t switch the budget type.",
        null,
    ) {
        host.apiClient.setBudgetType(
            request.budgetId,
            payload.requiredString("type"),
            request.encryptionPassword,
            request.operationId,
        )
    }
}

/** Payee changes and statement imports. */
private suspend fun BudgetEditQueue.sendPayeeEdit(request: QueuedBudgetReorder): Boolean =
    when (request.kind) {
        BudgetReorderKind.RENAME_PAYEE,
        BudgetReorderKind.MERGE_PAYEE,
        BudgetReorderKind.DELETE_PAYEE,
        BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY,
        -> {
            val payload = request.requirePayload()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t update this payee.",
                null,
            ) {
                host.apiClient.manageBudgetPayee(
                    request.budgetId,
                    request.itemId,
                    payload.requiredString("action"),
                    payload.optionalString("name"),
                    payload.optionalString("targetPayeeId"),
                    request.encryptionPassword,
                    payload.optionalString("categoryId"),
                    request.operationId,
                )
            }
        }
        BudgetReorderKind.IMPORT_STATEMENT -> {
            val payload = request.requirePayload()
            val imported = payload.requiredStatementItems()
            host.feedback.performBudgetAction(
                request.budgetId,
                request.encryptionPassword,
                "Couldn’t import this statement.",
                null,
            ) {
                host.apiClient.importBudgetAccountTransactions(
                    request.budgetId,
                    payload.requiredString("accountId"),
                    imported,
                    request.encryptionPassword,
                    request.operationId,
                )
            }
        }
        else -> error("Not a PayeeEdit edit: ${request.kind}")
    }
