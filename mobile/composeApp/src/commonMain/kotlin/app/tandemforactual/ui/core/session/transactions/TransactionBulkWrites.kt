package app.tandemforactual.ui.core.session.transactions

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.BulkEditContext
import app.tandemforactual.data.store.BulkTarget
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.bulkIdempotencyKey
import app.tandemforactual.data.store.discardWrite
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.enqueueWrites
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.findRecordsByIds
import app.tandemforactual.data.store.findWrite
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.pendingTransactionEditMessage
import app.tandemforactual.ui.core.session.queuedTransactionCreateToEdit
import app.tandemforactual.ui.core.session.stringValue
import app.tandemforactual.ui.core.session.toProjection
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import app.tandemforactual.ui.core.state.isOffBudgetAccount
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.design.feedback.InlineMessageKind
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** The selected transactions with the records behind them and the writes still waiting for each. */
internal class BulkSelection(
    val targets: List<BulkTarget>,
    val records: Map<String, BudgetRecordEntity>,
    val pending: Map<String, List<OutboxOperationEntity>>,
    val group: String = Uuid.random().toString(),
)

/** One write of a bulk action, and the waiting write it replaces, which undo puts back. */
internal class BulkWrite(
    val request: LocalWriteRequest,
    val previous: OutboxOperationEntity? = null,
)

private class BulkUndoStep(
    val operationId: String,
    val previous: OutboxOperationEntity?,
)

private val BLOCKING_STATUSES = setOf("sending", "uncertain", "conflict")
private val CREATE_FIELDS = setOf("account", "amount", "date", "payee", "category", "notes", "cleared", "subtransactions")

/** Refuses a bulk action in read-only mode or for a budget this sign-in doesn't own. */
internal suspend fun TransactionController.canWriteBulk(): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    if (!host.isCurrentIdentityCacheOwner()) {
        host.reportTransactionWriteMessage("Sign in to the server and account that own this budget before editing transactions.")
        return false
    }
    return true
}

/** Reads the selection in the order given, with each split line's split, as the device shows them now. */
internal suspend fun TransactionController.readBulkSelection(
    budgetId: String,
    ids: Collection<String>,
): BulkSelection? {
    val repository = host.repository
    if (repository == null) {
        host.reportTransactionWriteMessage("Budget data isn’t available on this device. Reopen the budget and try again.")
        return null
    }
    val order = ids.withIndex().associate { it.value to it.index }
    val selected = repository.findRecordsByIds(budgetId, "transaction", ids).sortedBy { order[it.recordId] }
    val records = selected.associateByTo(LinkedHashMap<String, BudgetRecordEntity>()) { it.recordId }
    val targets =
        selected.mapNotNull { record ->
            val body = record.currentBody() ?: return@mapNotNull null
            val parent =
                repository.splitParentOf(budgetId, record, body)?.let { parent ->
                    records[parent.recordId] = parent
                    parent.currentBody()?.let { BulkTarget(parent.recordId, it) }
                }
            BulkTarget(record.recordId, body, parent)
        }
    val pending =
        repository
            .readPendingWrites(budgetId)
            .filter { it.recordType == "transaction" && it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
            .groupBy { it.recordId }
    return BulkSelection(targets, records, pending)
}

/** The split a line belongs to. A split Tandem added keeps Tandem's id while its lines name Actual's, so it is found by its lines. */
private suspend fun LocalBudgetRepository.splitParentOf(
    budgetId: String,
    record: BudgetRecordEntity,
    body: JsonObject,
): BudgetRecordEntity? {
    if ((body["isChild"] as? JsonPrimitive)?.content != "true") return null
    val parentId = body.stringValue("parentId") ?: return null
    findRecord(budgetId, "transaction", parentId)?.takeUnless { it.remoteDeleted || it.localDeleted }?.let { return it }
    return findRecords(budgetId, "transaction", accountId = record.accountId, fromDate = record.recordDate, throughDate = record.recordDate)
        .firstOrNull { candidate ->
            candidate.isParent &&
                (candidate.recordBody()?.get("subtransactions") as? JsonArray)
                    ?.any { (it as? JsonObject)?.stringValue("id") == record.recordId } == true
        }
}

internal fun BudgetRecordEntity.currentBody(): JsonObject? =
    (localJson ?: remoteJson)?.let { json -> runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() }

/** The off-budget accounts and transfer payees, for the rules Actual applies to transfers. */
internal fun TransactionController.bulkEditContext(): BulkEditContext =
    BulkEditContext(
        offBudgetAccounts = host.accountRecords.filter { it.recordBody().isOffBudgetAccount() }.mapTo(mutableSetOf()) { it.recordId },
        transferAccountByPayee =
            host.payeeRecords
                .mapNotNull { payee -> payee.recordBody()?.stringValue("transfer_acct")?.let { payee.recordId to it } }
                .toMap(),
    )

/** An edit of [id] carrying [fields] on top of any edit still waiting for it, or null when it can't change now. */
internal fun BulkSelection.updateWrite(
    budgetId: String,
    id: String,
    fields: JsonObject,
    now: Long,
): BulkWrite? {
    val record = records[id] ?: return null
    val waiting = pending[id].orEmpty()
    if (waiting.any { it.status in BLOCKING_STATUSES }) return null
    val current = record.currentBody() ?: return null
    queuedTransactionCreateToEdit(waiting)?.let { create ->
        val createFields = JsonObject(fields - "reconciled")
        if (!CREATE_FIELDS.containsAll(createFields.keys)) return null
        val patch = JsonObject(Json.parseToJsonElement(create.patchJson).jsonObject + createFields)
        val optimistic = JsonObject(current + createFields)
        return BulkWrite(bulkRequest(budgetId, id, LocalWriteAction.CREATE, null, null, patch, optimistic, now), create)
    }
    val baseJson = record.remoteJson
    val revision = record.remoteRevision
    if (record.remoteDeleted || baseJson == null || revision.isNullOrBlank()) return null
    val queued =
        waiting.firstOrNull { it.status == "queued" && it.serverOperationId == null && it.action == LocalWriteAction.UPDATE.name }
    val patch = JsonObject(queued?.let { Json.parseToJsonElement(it.patchJson).jsonObject }.orEmpty() + fields)
    val optimistic = JsonObject(current + fields)
    return BulkWrite(bulkRequest(budgetId, id, LocalWriteAction.UPDATE, revision, baseJson, patch, optimistic, now), queued)
}

/** A delete of [id], or null when it has edits waiting that need to sync first. */
internal fun BulkSelection.deleteWrite(
    budgetId: String,
    id: String,
    now: Long,
): BulkWrite? {
    val record = records[id] ?: return null
    if (pending[id].orEmpty().isNotEmpty()) return null
    val baseJson = record.remoteJson ?: return null
    val revision = record.remoteRevision?.takeIf { it.isNotBlank() } ?: return null
    return BulkWrite(bulkRequest(budgetId, id, LocalWriteAction.DELETE, revision, baseJson, JsonObject(emptyMap()), null, now))
}

internal fun BulkSelection.bulkRequest(
    budgetId: String,
    id: String,
    action: LocalWriteAction,
    revision: String?,
    baseJson: String?,
    patch: JsonObject,
    optimistic: JsonObject?,
    now: Long,
): LocalWriteRequest {
    val operationId = Uuid.random().toString()
    return LocalWriteRequest(
        operationId = operationId,
        idempotencyKey = bulkIdempotencyKey(group, operationId),
        budgetId = budgetId,
        recordType = "transaction",
        recordId = id,
        action = action,
        baseRevision = revision,
        baseSnapshotJson = baseJson,
        patchJson = patch.toString(),
        optimisticJson = optimistic?.toString(),
        createdAtEpochMs = now,
        optimisticProjection = optimistic?.toProjection(),
    )
}

/** Queues every write of a bulk action at once and offers one undo for all of them. */
internal suspend fun TransactionController.enqueueBulk(
    budgetId: String,
    writes: List<BulkWrite>,
    message: String,
): Boolean {
    val repository = host.repository ?: return false
    if (writes.isEmpty()) {
        host.reportTransactionWriteMessage("Nothing to change. These transactions already match, or are waiting to sync.")
        return false
    }
    host.markSavingTransaction(true)
    try {
        val enqueued = repository.enqueueWrites(writes.map(BulkWrite::request))
        val steps = enqueued.zip(writes) { queued, write -> BulkUndoStep(queued.operation.operationId, write.previous) }
        host.loadLocalRecords(budgetId)
        showUndoNotice(message, budgetId) { undoBulk(budgetId, steps) }
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (_: LocalOperationConflictException) {
        host.reportTransactionWriteMessage(pendingTransactionEditMessage())
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(localStorageFailureMessage(error, "Tandem couldn’t change these transactions. Try again."))
    } finally {
        host.markSavingTransaction(false)
    }
    host.loadLocalRecords(budgetId)
    return false
}

/** Takes back every write of a bulk action that is still on this device; the rest are undone from Change History. */
private suspend fun TransactionController.undoBulk(
    budgetId: String,
    steps: List<BulkUndoStep>,
) {
    val repository = host.repository ?: return
    var synced = 0
    for (step in steps) {
        val write = repository.findWrite(step.operationId)
        if (write == null || write.status != "queued" || write.serverOperationId != null) {
            synced++
            continue
        }
        try {
            val previous = step.previous
            if (previous == null) repository.discardWrite(write.operationId) else repository.enqueueWrite(previous.restored())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            synced++
        }
    }
    host.loadLocalRecords(budgetId)
    if (synced > 0) {
        showSaveNotice(budgetId, "Some changes already synced", "Undo them from Change History.", InlineMessageKind.WARNING, null)
    }
}

private fun OutboxOperationEntity.restored(): LocalWriteRequest {
    val operationId = Uuid.random().toString()
    return LocalWriteRequest(
        operationId = operationId,
        idempotencyKey = operationId,
        budgetId = budgetId,
        recordType = recordType,
        recordId = recordId,
        action = LocalWriteAction.valueOf(action),
        baseRevision = baseRevision,
        baseSnapshotJson = baseSnapshotJson,
        patchJson = patchJson,
        optimisticJson = optimisticJson,
        createdAtEpochMs = createdAtEpochMs,
        optimisticProjection =
            LocalRecordProjection(
                optimisticRecordDate,
                optimisticAccountId,
                optimisticCategoryId,
                optimisticPayeeId,
                optimisticAmountMinor,
                optimisticBudgetMonth,
            ),
    )
}
