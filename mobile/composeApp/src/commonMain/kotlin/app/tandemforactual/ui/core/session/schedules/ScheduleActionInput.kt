package app.tandemforactual.ui.core.session.schedules

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalScheduleActionOutboxItem
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.schedules.ScheduleActionController
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal suspend fun ScheduleActionController.manageBudgetSchedule(
    budgetId: String,
    scheduleId: String,
    action: String,
    name: String?,
    encryptionPassword: String?,
    fields: JsonObject? = null,
    expectedNextDate: String? = null,
): Boolean {
    val cleanName = name?.trim()
    if (action == "rename" && cleanName.isNullOrBlank()) {
        host.reportBudgetActionMessage("Enter a schedule name.")
        return false
    }
    if (cleanName != null && cleanName.length > 100) {
        host.reportBudgetActionMessage("Schedule names can be up to 100 characters.")
        return false
    }
    if (action in setOf("post", "skip") && expectedNextDate.isNullOrBlank()) {
        host.reportBudgetActionMessage("Refresh the schedule before posting or skipping it.")
        return false
    }
    if (action !in setOf("post", "skip", "edit", "delete")) {
        host.reportBudgetActionMessage("This schedule action isn’t supported.")
        return false
    }
    if (action == "edit" && fields == null) {
        host.reportBudgetActionMessage("Couldn’t save this schedule on this device. Try again.")
        return false
    }
    return enqueueScheduleAction(
        budgetId = budgetId,
        scheduleId = scheduleId,
        action = action,
        expectedNextDate = expectedNextDate,
        name = cleanName,
        fields = fields,
        encryptionPassword = encryptionPassword,
    )
}

private suspend fun ScheduleActionController.enqueueScheduleAction(
    budgetId: String,
    scheduleId: String,
    action: String,
    expectedNextDate: String?,
    name: String? = null,
    fields: JsonObject? = null,
    encryptionPassword: String?,
): Boolean {
    val localRepository = host.repository
    if (host.refuseWriteInReadOnlyMode()) {
        host.reportBudgetActionMessage(host.transactionWriteMessage)
        return false
    }
    if (localRepository == null) {
        host.reportBudgetActionMessage("Tandem can’t save this action on this device.")
        return false
    }
    if (budgetId.isBlank()) {
        host.reportBudgetActionMessage("Open a budget before changing it.")
        return false
    }
    val cacheOwner = host.budgetCacheOwnerForLocalEdit()
    if (cacheOwner.isNullOrBlank() || !host.isCurrentIdentityCacheOwner()) {
        host.reportBudgetActionMessage("Sign in to the server and account that own this budget before changing it.")
        return false
    }
    if (!restoreScheduleActionQueue()) {
        host.reportBudgetActionMessage("Couldn’t load saved schedule actions. Try again.")
        return false
    }
    val operationId = Uuid.random().toString()
    val payload =
        buildJsonObject {
            expectedNextDate?.let { put("expectedNextDate", it) }
            name?.let { put("name", it) }
            fields?.let { put("fields", it) }
            put("cacheOwner", cacheOwner)
            put("encrypted", !encryptionPassword.isNullOrBlank())
        }.toString()
    return try {
        val operation =
            localRepository.enqueueScheduleAction(
                LocalScheduleActionOutboxItem(
                    operationId = operationId,
                    budgetId = budgetId,
                    scheduleId = scheduleId,
                    action = action,
                    payloadJson = payload,
                    createdAtEpochMs = host.nowEpochMillis(),
                ),
            )
        if (!encryptionPassword.isNullOrBlank()) host.scheduleActionEncryptionPasswords[budgetId] = encryptionPassword
        refreshScheduleActionQueue(budgetId)
        host.reportBudgetActionMessage(null)
        scheduleActionRetrySignal += 1
        operation.status in setOf("queued", "sending", "committed")
    } catch (error: LocalOperationConflictException) {
        host.reportBudgetActionMessage("This schedule already has an action waiting to sync or review.")
        false
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        host.reportBudgetActionMessage(localStorageFailureMessage(error, "Couldn’t save this action on this device. Try again."))
        false
    }
}
