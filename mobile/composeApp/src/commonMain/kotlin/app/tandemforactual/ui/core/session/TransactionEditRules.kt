package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.ui.core.format.signedAmount
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun pendingTransactionEditMessage(): String = "This transaction is waiting to sync. Try again after it syncs."

internal fun blockedTransactionEditMessage(status: String): String =
    when (status) {
        "conflict" -> "Review this transaction in Pending Changes before editing it."
        "uncertain" -> "This transaction’s status is unknown. Check Pending Changes before editing it again."
        else -> "This transaction is syncing. Wait for it to finish before editing."
    }

internal fun queuedTransactionEditToDiscardForNoOp(
    pendingWrites: List<OutboxOperationEntity>,
    editingLocalCreate: Boolean,
): String? =
    if (editingLocalCreate) {
        null
    } else {
        pendingWrites
            .firstOrNull {
                it.action == LocalWriteAction.UPDATE.name &&
                    it.status == "queued" &&
                    it.serverOperationId == null
            }?.operationId
    }

internal fun queuedTransactionCreateToEdit(pendingWrites: List<OutboxOperationEntity>): OutboxOperationEntity? =
    pendingWrites.firstOrNull {
        it.action == LocalWriteAction.CREATE.name &&
            it.status == "queued" &&
            it.serverOperationId == null
    }

internal fun buildTransactionEditPatch(
    base: JsonObject,
    signedAmount: Long,
    fallbackCurrentAmount: Long?,
    notes: String,
    details: TransactionEditDetails?,
): JsonObject =
    buildJsonObject {
        val currentAmount = base.longValue("amount") ?: fallbackCurrentAmount
        val currentNotes = base.stringValue("notes") ?: ""
        if (signedAmount != currentAmount) put("amount", JsonPrimitive(signedAmount))
        if (notes != currentNotes) put("notes", JsonPrimitive(notes))
        details?.let { requested ->
            if (requested.accountId != base.stringValue("account")) put("account", JsonPrimitive(requested.accountId))
            if (requested.payeeId != base.stringValue("payee")) {
                put("payee", requested.payeeId?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (requested.categoryId != base.stringValue("category")) {
                put("category", requested.categoryId?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (requested.date != base.stringValue("date")) put("date", JsonPrimitive(requested.date))
        }
    }

internal fun discardedPendingWriteMessage(operationStatus: String?): String =
    if (operationStatus == "uncertain") {
        "This pending change was removed from this device. Actual may already have saved it; removing it here doesn’t undo it."
    } else {
        "Your edit was discarded. Actual wasn’t changed."
    }
