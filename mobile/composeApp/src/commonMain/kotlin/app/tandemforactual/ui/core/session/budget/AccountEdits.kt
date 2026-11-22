package app.tandemforactual.ui.core.session.budget
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.enqueueWrites
import app.tandemforactual.data.store.findRecordsByIds
import app.tandemforactual.ui.core.session.BudgetReorderKind
import app.tandemforactual.ui.core.session.QueuedBudgetReorder
import app.tandemforactual.ui.core.session.budget.BudgetEditQueue
import app.tandemforactual.ui.core.session.budget.BudgetEdits
import app.tandemforactual.ui.core.session.enqueueIfMissing
import app.tandemforactual.ui.core.session.enqueueOrderDraft
import app.tandemforactual.ui.core.session.isOrderDraft
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.optionalString
import app.tandemforactual.ui.core.session.requirePayload
import app.tandemforactual.ui.core.session.rewriteAccountIds
import app.tandemforactual.ui.core.session.stringValue
import app.tandemforactual.ui.core.session.toProjection
import app.tandemforactual.ui.core.state.booleanValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal suspend fun BudgetEdits.createBudgetAccount(
    budgetId: String,
    name: String,
    offbudget: Boolean,
    accountGroupId: String?,
    encryptionPassword: String?,
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        host.feedback.budgetActionMessage = "Enter an account name."
        return false
    }
    if (cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Account names can be up to 100 characters."
        return false
    }
    val localAccountId = "local-account-${Uuid.random()}"
    return queue.enqueueOfflineBudgetMutation(
        budgetId = budgetId,
        kind = BudgetReorderKind.CREATE_ACCOUNT,
        itemId = localAccountId,
        scopeId = "accounts",
        payload =
            buildJsonObject {
                put("name", cleanName)
                put("offbudget", offbudget)
                put("accountGroupId", accountGroupId?.let(::JsonPrimitive) ?: JsonNull)
            },
        encryptionPassword = encryptionPassword,
        name = cleanName,
    )
}

internal suspend fun BudgetEdits.closeBudgetAccount(
    budgetId: String,
    accountId: String,
    transferAccountId: String?,
    transferCategoryId: String?,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.CLOSE_ACCOUNT,
        accountId,
        "accounts",
        buildJsonObject {
            put("transferAccountId", transferAccountId?.let(::JsonPrimitive) ?: JsonNull)
            put("transferCategoryId", transferCategoryId?.let(::JsonPrimitive) ?: JsonNull)
        },
        encryptionPassword,
    )

internal suspend fun BudgetEdits.renameBudgetAccount(
    budgetId: String,
    accountId: String,
    name: String,
    encryptionPassword: String?,
): Boolean {
    val cleanName = name.trim()
    if (cleanName.isBlank()) {
        host.feedback.budgetActionMessage = "Enter an account name."
        return false
    }
    if (cleanName.length > 100) {
        host.feedback.budgetActionMessage = "Account names can be up to 100 characters."
        return false
    }
    return queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.RENAME_ACCOUNT,
        accountId,
        "accounts",
        buildJsonObject { put("name", cleanName) },
        encryptionPassword,
        cleanName,
    )
}

internal suspend fun BudgetEdits.reopenBudgetAccount(
    budgetId: String,
    accountId: String,
    encryptionPassword: String?,
): Boolean =
    queue.enqueueOfflineBudgetMutation(
        budgetId,
        BudgetReorderKind.REOPEN_ACCOUNT,
        accountId,
        "accounts",
        buildJsonObject {},
        encryptionPassword,
    )

internal suspend fun BudgetEdits.moveBudgetAccount(
    budgetId: String,
    accountId: String,
    targetAccountId: String?,
    encryptionPassword: String?,
    desiredOrder: List<String> = emptyList(),
    baseOrder: List<String> = emptyList(),
): Boolean {
    val payload = buildJsonObject { put("targetAccountId", targetAccountId?.let(::JsonPrimitive) ?: JsonNull) }
    if (desiredOrder.isEmpty()) {
        return queue.enqueueOfflineBudgetMutation(
            budgetId,
            BudgetReorderKind.MOVE_ACCOUNT,
            accountId,
            "accounts",
            payload,
            encryptionPassword,
        )
    }
    if (budgetId.isBlank() || accountId.isBlank() || host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.feedback.budgetActionMessage = "Sign in to the server and account that own this budget before changing it."
        return false
    }
    return queue
        .persistBudgetReorderQueueMutation { queue ->
            val move =
                QueuedBudgetReorder(
                    budgetId = budgetId,
                    scopeId = "accounts",
                    kind = BudgetReorderKind.MOVE_ACCOUNT,
                    itemId = accountId,
                    targetId = targetAccountId,
                    desiredOrder = desiredOrder,
                    encryptionPassword = encryptionPassword,
                    baseOrder = baseOrder,
                    payloadJson = payload.toString(),
                )
            if (move.isOrderDraft()) queue.enqueueOrderDraft(move) else queue.enqueueIfMissing(move)
        }.also { queued -> if (queued) queue.noteOrderDraftQueued() }
}

internal suspend fun BudgetEdits.markTransactionsReconciled(
    budgetId: String,
    accountId: String,
    transactionIds: Set<String>,
    selectedTransactionIds: Set<String>,
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    val localRepository = host.repository
    if (localRepository == null || !host.isAuthenticated || budgetId.isBlank()) {
        host.reportTransactionWriteMessage("Sign in and open a budget before reconciling transactions.")
        return false
    }
    val ids = transactionIds.filter(String::isNotBlank).distinct()
    if (ids.isEmpty()) {
        host.reportTransactionWriteMessage("There are no unreconciled transactions to review.")
        return false
    }
    val selectedIds = selectedTransactionIds.intersect(ids.toSet())
    if (host.isSavingTransaction) return false
    host.markSavingTransaction(true)
    try {
        val requests = mutableListOf<LocalWriteRequest>()
        val recordsById =
            localRepository
                .findRecordsByIds(budgetId, "transaction", ids)
                .associateBy(BudgetRecordEntity::recordId)
        for (transactionId in ids) {
            val record = recordsById[transactionId]
            val baseJson = record?.remoteJson
            val revision = record?.remoteRevision
            if (
                record == null ||
                record.localDeleted ||
                record.remoteDeleted ||
                record.accountId != accountId ||
                record.isParent ||
                baseJson == null ||
                revision.isNullOrBlank() ||
                record.localJson != null
            ) {
                host.reportTransactionWriteMessage("Sync pending edits before reconciling this account.")
                return false
            }
            val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
            if (base == null) {
                host.reportTransactionWriteMessage("Couldn’t read this transaction. Sync the budget and try again.")
                return false
            }
            if (base.booleanValue("reconciled") == true) continue
            val shouldBeCleared = transactionId in selectedIds
            val currentlyCleared = base.booleanValue("cleared") == true
            if (currentlyCleared == shouldBeCleared && !shouldBeCleared) continue
            val patch =
                buildJsonObject {
                    if (currentlyCleared != shouldBeCleared) put("cleared", shouldBeCleared)
                    if (shouldBeCleared) put("reconciled", true)
                }
            val optimistic = JsonObject(base + patch)
            val operationId = Uuid.random().toString()
            requests +=
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = patch.toString(),
                    optimisticJson = optimistic.toString(),
                    createdAtEpochMs = host.nowEpochMillis(),
                    optimisticProjection = optimistic.toProjection(),
                )
        }
        if (requests.isEmpty()) {
            host.reportTransactionWriteMessage("No transaction changes to save.")
            return true
        }
        localRepository.enqueueWrites(requests)
        host.reportTransactionWriteMessage("Reconciliation saved on this device. Sync when you’re ready.")
        host.loadLocalRecords(budgetId)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(localStorageFailureMessage(error, "Couldn’t queue reconciliation changes. Try again."))
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}

internal suspend fun BudgetEdits.createQueuedBudgetAccount(request: QueuedBudgetReorder): String? {
    val payload = request.requirePayload()
    val name = payload.stringValue("name") ?: request.name ?: return null
    var createdId: String? = null
    val saved =
        host.feedback.performBudgetAction(
            budgetId = request.budgetId,
            encryptionPassword = request.encryptionPassword,
            failureMessage = "Couldn’t add this account.",
            messageOwner = null,
        ) {
            createdId =
                host.apiClient.createBudgetAccount(
                    budgetId = request.budgetId,
                    name = name,
                    offbudget = payload.booleanValue("offbudget") ?: false,
                    accountGroupId = payload.optionalString("accountGroupId"),
                    encryptionPassword = request.encryptionPassword,
                    idempotencyKeyOverride = request.operationId,
                )
        }
    return createdId.takeIf { saved || host.feedback.budgetActionWasAppliedButRefreshFailed }
}

internal suspend fun BudgetEdits.remapQueuedAccountReferences(
    createOperationId: String,
    temporaryAccountId: String,
    actualAccountId: String,
) {
    queue.budgetQueuePersistenceMutex.withLock {
        val next =
            queue.budgetReorderQueueState.copy(
                moves =
                    queue.budgetReorderQueueState.moves.map { move ->
                        if (move.operationId == createOperationId) {
                            move
                        } else {
                            move.copy(
                                itemId = if (move.itemId == temporaryAccountId) actualAccountId else move.itemId,
                                targetId = if (move.targetId == temporaryAccountId) actualAccountId else move.targetId,
                                payloadJson =
                                    move.payloadJson?.let { payload ->
                                        runCatching {
                                            rewriteAccountIds(
                                                Json.parseToJsonElement(payload),
                                                temporaryAccountId,
                                                actualAccountId,
                                            ).toString()
                                        }.getOrNull() ?: payload
                                    },
                            )
                        }
                    },
            )
        queue.persistBudgetReorderQueueNow(next)
        queue.budgetReorderQueueState = next
    }
}
