package app.tandemforactual.ui.core.automation

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.data.store.discardWrite
import app.tandemforactual.data.store.enqueueWrite
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import app.tandemforactual.ui.core.state.booleanValue
import app.tandemforactual.ui.core.state.recordBody
import app.tandemforactual.ui.core.state.recordName
import app.tandemforactual.ui.core.state.stringValue
import kotlin.uuid.Uuid

enum class AutomationUndo {
    /** The transaction hadn't synced yet, so it's gone. */
    REMOVED,

    /** The transaction had synced; its deletion syncs next. */
    DELETING,

    /** It changed since, or is still sending; only Tandem can sort it out. */
    OPEN_TANDEM,
}

/** Takes back an automated transaction through the outbox, the way deleting it in Tandem does. */
suspend fun undoAutomatedTransaction(
    database: TandemDatabase,
    budgetId: String,
    transactionId: String,
    nowEpochMs: Long,
): AutomationUndo {
    val repository = LocalBudgetRepository(database) { nowEpochMs }
    val pending =
        repository.readPendingWrites(budgetId).filter {
            it.recordType == "transaction" && it.recordId == transactionId && it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES
        }
    val create = pending.lastOrNull { it.action == LocalWriteAction.CREATE.name }
    if (create != null && create.status == "queued" && create.serverOperationId == null && pending.size == 1) {
        repository.discardWrite(create.operationId)
        return AutomationUndo.REMOVED
    }
    val current = repository.findRecord(budgetId, "transaction", transactionId)
    val base = current?.remoteJson
    val revision = current?.remoteRevision
    if (pending.isNotEmpty() || current == null || current.remoteDeleted || base == null || revision == null) {
        return AutomationUndo.OPEN_TANDEM
    }
    val operationId = Uuid.random().toString()
    repository.enqueueWrite(
        LocalWriteRequest(
            operationId = operationId,
            idempotencyKey = operationId,
            budgetId = budgetId,
            recordType = "transaction",
            recordId = transactionId,
            action = LocalWriteAction.DELETE,
            baseRevision = revision,
            baseSnapshotJson = base,
            patchJson = "{}",
            optimisticJson = null,
            createdAtEpochMs = nowEpochMs,
        ),
    )
    return AutomationUndo.DELETING
}

/** Names an automation app's settings screen can offer, from this device's copy of the budget. */
data class AutomationChoices(
    val accounts: List<String>,
    val categories: List<String>,
    val payees: List<String>,
)

suspend fun automationChoices(
    database: TandemDatabase,
    budgetId: String,
): AutomationChoices {
    val repository = LocalBudgetRepository(database) { 0L }

    suspend fun names(
        recordType: String,
        keep: (kotlinx.serialization.json.JsonObject?) -> Boolean,
    ) = repository
        .findRecords(budgetId, recordType)
        .filter { keep(it.recordBody()) }
        .mapNotNull { it.recordName() }
        .distinct()
        .sortedBy { it.lowercase() }
    return AutomationChoices(
        accounts = names("account") { it?.booleanValue("closed") != true },
        categories = names("category") { it?.booleanValue("hidden") != true },
        payees = names("payee") { it?.stringValue("transfer_acct") == null },
    )
}
