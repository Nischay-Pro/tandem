package app.tandemforactual.ui.core.session.transactions

import app.tandemforactual.data.store.LocalOperationConflictException
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.session.localStorageFailureMessage
import app.tandemforactual.ui.core.session.pendingTransactionEditMessage
import app.tandemforactual.ui.core.session.toProjection
import app.tandemforactual.ui.core.state.SplitEditLine
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** The edit a split sends: its new lines, or none with [unsplitCategoryId] to make it one transaction again. */
internal data class SplitEdit(
    val parentId: String,
    val lines: List<SplitEditLine>,
    val amountMinor: Long,
    val notes: String,
    val payeeId: String?,
    val unsplitCategoryId: String? = null,
)

/**
 * Replaces a split's lines, and its amount, payee and notes when they changed, in one queued edit of the parent, so
 * Actual never sees a half-edited split.
 */
internal suspend fun TransactionController.updateSplit(
    budgetId: String,
    edit: SplitEdit,
): Boolean {
    host.reportTransactionWriteMessage(null)
    if (host.refuseWriteInReadOnlyMode()) return false
    val localRepository = host.repository
    val current = localRepository?.findRecord(budgetId, "transaction", edit.parentId)
    val baseJson = current?.remoteJson
    if (localRepository == null || current == null || baseJson == null || current.remoteRevision.isNullOrBlank()) {
        host.reportTransactionWriteMessage("This split isn’t up to date. Sync the budget before editing.")
        return false
    }
    if (localRepository.readPendingWrites(budgetId).any { it.recordType == "transaction" && it.recordId == edit.parentId }) {
        host.reportTransactionWriteMessage(pendingTransactionEditMessage())
        return false
    }
    val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull() ?: return false
    val patch = splitEditPatch(base, edit)
    host.markSavingTransaction(true)
    try {
        val optimistic = JsonObject(base + patch + ("isParent" to JsonPrimitive(edit.lines.isNotEmpty())))
        val operationId = Uuid.random().toString()
        localRepository.enqueueWrite(
            LocalWriteRequest(
                operationId = operationId,
                idempotencyKey = operationId,
                budgetId = budgetId,
                recordType = "transaction",
                recordId = edit.parentId,
                action = LocalWriteAction.UPDATE,
                baseRevision = current.remoteRevision,
                baseSnapshotJson = baseJson,
                patchJson = patch.toString(),
                optimisticJson = optimistic.toString(),
                createdAtEpochMs = host.nowEpochMillis(),
                optimisticProjection = optimistic.toProjection(),
            ),
        )
        host.loadLocalRecords(budgetId)
        return true
    } catch (error: CancellationException) {
        throw error
    } catch (_: LocalOperationConflictException) {
        host.reportTransactionWriteMessage(pendingTransactionEditMessage())
        return false
    } catch (error: Exception) {
        host.reportTransactionWriteMessage(localStorageFailureMessage(error, "Tandem couldn’t save this split. Try again."))
        return false
    } finally {
        host.markSavingTransaction(false)
    }
}

/** The lines, plus the parent's amount, payee and notes where they differ from [base]. */
internal fun splitEditPatch(
    base: JsonObject,
    edit: SplitEdit,
): JsonObject =
    buildJsonObject {
        put(
            "subtransactions",
            JsonArray(
                edit.lines.map { line ->
                    buildJsonObject {
                        line.id?.let { put("id", it) }
                        put("amount", line.amountMinor)
                        put("category", line.categoryId)
                    }
                },
            ),
        )
        if ((base["amount"] as? JsonPrimitive)?.longOrNull != edit.amountMinor) put("amount", edit.amountMinor)
        val notes = edit.notes.trim().ifEmpty { null }
        if ((base["notes"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content != notes) put("notes", notes)
        if ((base["payee"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content != edit.payeeId) put("payee", edit.payeeId)
        if (edit.lines.isEmpty()) put("category", edit.unsplitCategoryId)
    }
