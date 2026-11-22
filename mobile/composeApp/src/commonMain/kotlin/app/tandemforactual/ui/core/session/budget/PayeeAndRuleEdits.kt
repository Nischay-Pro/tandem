package app.tandemforactual.ui.core.session.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.enqueueIfMissing
import app.tandemforactual.ui.core.state.defaultCategoryRulesByPayee
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal suspend fun BudgetEdits.createBudgetPayee(
    budgetId: String,
    name: String,
    encryptionPassword: String?,
): String? {
    val messageOwner = host.feedback.currentBudgetActionMessageOwner
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        host.feedback.budgetActionMessage = "Enter a payee name."
        return null
    }
    if (cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Payee names can be up to 100 characters."
        return null
    }
    if (host.refuseWriteInReadOnlyMode()) {
        host.feedback.budgetActionMessage = host.transactionWriteMessage
        return null
    }
    if (budgetId.isBlank()) {
        host.feedback.budgetActionMessage = "Open a budget before changing it."
        return null
    }
    if (!host.isCurrentIdentityCacheOwner()) {
        host.feedback.setBudgetActionMessage(
            "Sign in to the server and account that own this budget before changing it.",
            ownerRoute = messageOwner,
        )
        return null
    }
    host.payeeRecords.firstOrNull { it.recordName()?.equals(cleanName, ignoreCase = true) == true }?.let { return it.recordId }
    val localPayeeId = "local-payee-${Uuid.random()}"
    val queued =
        queue.persistBudgetReorderQueueMutation { queue ->
            queue.enqueueIfMissing(
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "payees",
                    kind = BudgetReorderKind.CREATE_PAYEE,
                    itemId = localPayeeId,
                    targetId = null,
                    desiredOrder = emptyList(),
                    encryptionPassword = encryptionPassword,
                    name = cleanName,
                ),
            )
        }
    if (!queued) return null
    host.replacePayeeRecords(queue.payeeRecordsWithPendingCreates(budgetId, host.payeeRecords))
    if (!host.isServerReachable) {
        host.feedback.setBudgetActionMessage(
            "Saved on this device. It will sync when Actual is reachable.",
            InlineMessageKind.INFO,
            messageOwner,
        )
    }
    queue.retryQueuedBudgetReorders()
    return localPayeeId
}

internal suspend fun BudgetEdits.manageBudgetPayee(
    budgetId: String,
    payeeId: String,
    action: String,
    name: String?,
    targetPayeeId: String?,
    encryptionPassword: String?,
    categoryId: String? = null,
): Boolean {
    val cleanName = name?.trim()
    if (action == "rename" && cleanName.isNullOrBlank()) {
        host.feedback.budgetActionMessage = "Enter a payee name."
        return false
    }
    if (cleanName != null && cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Payee names can be up to 100 characters."
        return false
    }
    if (action == "merge" && (targetPayeeId.isNullOrBlank() || targetPayeeId == payeeId)) {
        host.feedback.budgetActionMessage = "Choose another payee to merge into."
        return false
    }
    val kind =
        when (action) {
            "rename" -> BudgetReorderKind.RENAME_PAYEE
            "merge" -> BudgetReorderKind.MERGE_PAYEE
            "delete" -> BudgetReorderKind.DELETE_PAYEE
            "default-category" -> BudgetReorderKind.SET_PAYEE_DEFAULT_CATEGORY
            else -> {
                host.feedback.budgetActionMessage = "This payee change isn’t supported."
                return false
            }
        }
    return queue.enqueueOfflineBudgetMutation(
        budgetId = budgetId,
        kind = kind,
        itemId = payeeId,
        scopeId = "payees",
        payload =
            buildJsonObject {
                put("action", action)
                cleanName?.let { put("name", it) }
                targetPayeeId?.let { put("targetPayeeId", it) }
                categoryId?.let { put("categoryId", it) }
            },
        encryptionPassword = encryptionPassword,
        name = cleanName,
    )
}

internal suspend fun BudgetEdits.createQueuedBudgetPayee(request: QueuedBudgetReorder): String? {
    val name = request.name ?: return null
    var createdId: String? = null
    val saved =
        host.feedback.performBudgetAction(
            budgetId = request.budgetId,
            encryptionPassword = request.encryptionPassword,
            failureMessage = "Couldn’t add this payee. Try again.",
            messageOwner = null,
        ) {
            createdId =
                host.apiClient.createBudgetPayee(
                    budgetId = request.budgetId,
                    name = name,
                    encryptionPassword = request.encryptionPassword,
                    idempotencyKeyOverride = request.operationId,
                )
        }
    return createdId.takeIf { saved || host.feedback.budgetActionWasAppliedButRefreshFailed }
}

/** The payee's default-category rule, or else the category last used with this payee. */
internal suspend fun BudgetEdits.suggestedCategoryForPayee(
    budgetId: String,
    payeeId: String,
): String? {
    if (budgetId.isBlank() || payeeId.isBlank()) return null
    defaultCategoryRulesByPayee(host.ruleRecords)[payeeId]?.singleOrNull()?.let { return it }
    return host.repository?.latestCategoryForPayee(budgetId, payeeId)
}

internal suspend fun BudgetEdits.createBudgetRule(
    budgetId: String,
    rule: JsonObject,
    encryptionPassword: String?,
): Boolean =
    host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t add this rule. Try again.") {
        host.apiClient.createBudgetRule(budgetId, rule, encryptionPassword)
    }

internal suspend fun BudgetEdits.editBudgetRule(
    budgetId: String,
    ruleId: String,
    rule: JsonObject,
    encryptionPassword: String?,
): Boolean =
    host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t save this rule. Try again.") {
        host.apiClient.manageBudgetRule(budgetId, ruleId, "edit", rule, encryptionPassword)
    }

internal suspend fun BudgetEdits.deleteBudgetRule(
    budgetId: String,
    ruleId: String,
    encryptionPassword: String?,
): Boolean =
    host.feedback.performBudgetAction(budgetId, encryptionPassword, "Couldn’t delete this rule. Try again.") {
        host.apiClient.manageBudgetRule(budgetId, ruleId, "delete", null, encryptionPassword)
    }
