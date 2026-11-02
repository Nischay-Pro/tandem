package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.PairedDevice
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.uuid.Uuid

/** In-memory connection state. Device credentials and budget passwords are never saved here. */
internal class TandemMobileSession(
    private val apiClient: TandemApiClient,
    database: TandemDatabase?,
    private val nowEpochMillis: () -> Long,
) {
    private val repository = database?.let { LocalBudgetRepository(it, nowEpochMillis) }
    private val syncCoordinator =
        repository?.let {
            BudgetSyncCoordinator(
                it,
                app.tandemforactual.api.client
                    .GeneratedBudgetSyncRemote(),
            )
        }

    var pairedDevice by mutableStateOf<PairedDevice?>(null)
        private set

    var budgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var isPairing by mutableStateOf(false)
        private set

    var pairingError by mutableStateOf<String?>(null)
        private set

    var isLoadingBudgets by mutableStateOf(false)
        private set

    var budgetError by mutableStateOf<String?>(null)
        private set

    var isSyncing by mutableStateOf(false)
        private set

    var syncComplete by mutableStateOf(false)
        private set

    var syncError by mutableStateOf<String?>(null)
        private set

    var syncedTransactionCount by mutableStateOf(0)
    var cachedTransactionCount by mutableStateOf(0)
        private set

    var pendingWrites by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

    var isSavingTransaction by mutableStateOf(false)
        private set

    var transactionWriteMessage by mutableStateOf<String?>(null)
        private set

    var lastSyncSummary by mutableStateOf<BudgetSyncSummary?>(null)
        private set

    var transactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var budgetMonthRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var accountRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    val isAuthenticated: Boolean
        get() = apiClient.isAuthenticated

    suspend fun pair(
        serverUrl: String,
        pairingCode: String,
        deviceName: String,
    ) {
        pairingError = null
        isPairing = true
        try {
            pairedDevice = apiClient.pair(serverUrl, pairingCode, deviceName)
            loadBudgets()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            pairingError = error.message ?: "Could not pair this device. Check the server and pairing code."
            pairedDevice = null
        } catch (error: Exception) {
            pairingError = "Could not pair this device. Check the server address and connection."
            pairedDevice = null
        } finally {
            isPairing = false
        }
    }

    suspend fun loadBudgets() {
        budgetError = null
        isLoadingBudgets = true
        try {
            budgets = apiClient.listBudgets()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetError = error.message ?: "Could not load budgets from this server."
        } catch (error: Exception) {
            budgetError = "Could not load budgets from this server. Check the connection and try again."
        } finally {
            isLoadingBudgets = false
        }
    }

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
    ): BudgetSyncSummary? {
        syncError = null
        syncComplete = false
        syncedTransactionCount = 0
        lastSyncSummary = null
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            syncError = "Local storage is unavailable. Restart the app and try again."
            return null
        }

        isSyncing = true
        try {
            val summary = coordinator.synchronize(budgetId, encryptionPassword)
            lastSyncSummary = summary
            loadLocalRecords(budgetId)
            syncedTransactionCount =
                localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            syncComplete = true
            return summary
        } catch (error: CancellationException) {
            throw error
        } catch (error: BudgetSyncRemoteException) {
            syncError =
                when (error.httpStatus) {
                    401 -> "This device needs to be paired again."
                    403 -> "This device is not authorized to open that budget."
                    404 -> "The budget is no longer available on the server."
                    400, 422 -> "Check the budget password and try again."
                    in 500..599 -> "The server could not read this budget. Try again later."
                    else -> "Could not sync this budget. Check the connection and try again."
                }
            return null
        } catch (error: BudgetSyncProtocolException) {
            syncError = "The server returned budget data the app could not read."
            return null
        } catch (error: Exception) {
            syncError = "Could not sync this budget. Your local data is still available."
            return null
        } finally {
            isSyncing = false
        }
    }

    suspend fun updateTransaction(
        budgetId: String,
        transactionId: String,
        amountText: String,
        notes: String,
        originalAmountMinor: Long,
        encryptionPassword: String?,
    ) {
        transactionWriteMessage = null
        val enteredAmount = parseAmountMinorUnits(amountText)
        if (enteredAmount == null || enteredAmount < 0) {
            transactionWriteMessage = "Enter a valid positive amount using this device’s number format."
            return
        }
        val localRepository = repository
        val coordinator = syncCoordinator
        if (localRepository == null || coordinator == null || !isAuthenticated) {
            transactionWriteMessage = "Pair this device and open a budget before editing a transaction."
            return
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "The latest Actual transaction is unavailable. Sync the budget before editing."
            return
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        if (base == null) {
            transactionWriteMessage = "The saved transaction could not be read. Sync the budget before editing."
            return
        }
        val signedAmount = if (originalAmountMinor < 0) -enteredAmount else enteredAmount
        val currentAmount = base.longValue("amount") ?: current.amountMinor
        val currentNotes = base.stringValue("notes") ?: ""
        val patch =
            buildMap<String, kotlinx.serialization.json.JsonElement> {
                if (signedAmount != currentAmount) put("amount", JsonPrimitive(signedAmount))
                if (notes != currentNotes) put("notes", JsonPrimitive(notes))
            }
        if (patch.isEmpty()) {
            transactionWriteMessage = "There are no transaction changes to save."
            return
        }

        isSavingTransaction = true
        try {
            val patchJson = JsonObject(patch)
            val optimistic = JsonObject(base + patch)
            val operationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = patchJson.toString(),
                    optimisticJson = optimistic.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = optimistic.toProjection(),
                ),
            )
            val summary = synchronize(budgetId, encryptionPassword)
            transactionWriteMessage =
                when {
                    summary == null -> "Saved on this device. The server has not confirmed it; check sync status before retrying."
                    summary.writesCommitted > 0 -> "Changes synced to Actual."
                    summary.writesConflicted > 0 -> "The Actual version changed too. Review both versions before continuing."
                    summary.writesUncertain > 0 -> "Actual has not confirmed this change. Check its status before retrying."
                    summary.writesRejected > 0 -> "Actual did not apply this edit. The previous value was restored."
                    else -> "The change is saved on this device and is waiting to sync."
                }
            loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "The edit remains on this device. Check sync status before retrying."
            loadLocalRecords(budgetId)
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun resolveConflict(
        budgetId: String,
        operationId: String,
        keepDeviceVersion: Boolean,
        encryptionPassword: String?,
    ) {
        transactionWriteMessage = null
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local storage is unavailable. Restart the app and try again."
            return
        }
        val operation = localRepository.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
        if (operation == null || operation.status != "conflict") {
            transactionWriteMessage = "This conflict is no longer available. Refresh the sync status."
            loadLocalRecords(budgetId)
            return
        }
        if (!keepDeviceVersion) {
            localRepository.discardWrite(operationId)
            loadLocalRecords(budgetId)
            transactionWriteMessage = "Kept the latest Actual version. The local draft was discarded."
            return
        }
        val current = localRepository.findRecord(budgetId, operation.recordType, operation.recordId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "Actual deleted this transaction. Keep Actual and discard the local draft."
            return
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        val patch = runCatching { Json.parseToJsonElement(operation.patchJson).jsonObject }.getOrNull()
        if (base == null || patch == null || operation.action != "UPDATE") {
            transactionWriteMessage = "This change cannot be safely rebased. Keep Actual and discard the local draft."
            return
        }
        isSavingTransaction = true
        try {
            val rebased = JsonObject(base + patch)
            val newOperationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = newOperationId,
                    idempotencyKey = newOperationId,
                    budgetId = budgetId,
                    recordType = operation.recordType,
                    recordId = operation.recordId,
                    action = LocalWriteAction.UPDATE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = patch.toString(),
                    optimisticJson = rebased.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = rebased.toProjection(),
                ),
            )
            val summary = synchronize(budgetId, encryptionPassword)
            transactionWriteMessage =
                when {
                    summary == null -> "The selected version is saved on this device. Check sync status before retrying."
                    summary.writesCommitted > 0 -> "Your selected version was synced to Actual."
                    summary.writesConflicted > 0 -> "Actual changed again. Review the updated conflict."
                    summary.writesUncertain > 0 -> "Actual has not confirmed the selected version yet. Check its status."
                    summary.writesRejected > 0 -> "Actual did not apply the selected version. The latest value was restored."
                    else -> "Your selected version is waiting to sync."
                }
            loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "The selected version remains on this device. Check sync status before retrying."
            loadLocalRecords(budgetId)
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun discardWrite(
        budgetId: String,
        operationId: String,
    ) {
        val localRepository = repository ?: return
        localRepository.discardWrite(operationId)
        loadLocalRecords(budgetId)
        transactionWriteMessage = "The local change was discarded. Actual was not changed by this action."
    }

    suspend fun retryUncertainWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        val localRepository = repository ?: return
        localRepository.retryUncertainWrite(operationId)
        synchronize(budgetId, encryptionPassword)
        loadLocalRecords(budgetId)
    }

    suspend fun loadLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        transactionRecords = localRepository.findRecentRecords(budgetId, "transaction", limit = 50)
        cachedTransactionCount =
            localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        accountRecords = localRepository.findRecords(budgetId, "account")
        payeeRecords = localRepository.findRecords(budgetId, "payee")
        categoryRecords = localRepository.findRecords(budgetId, "category")
        categoryGroupRecords = localRepository.findRecords(budgetId, "category-group")
        budgetMonthRecords = localRepository.findRecords(budgetId, "budget-month")
        pendingWrites = localRepository.readPendingWrites(budgetId)
    }
}

private fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

private fun JsonObject.toProjection(): LocalRecordProjection =
    LocalRecordProjection(
        recordDate = stringValue("date"),
        accountId = stringValue("account"),
        categoryId = stringValue("category"),
        payeeId = stringValue("payee"),
        amountMinor = longValue("amount"),
        budgetMonth = stringValue("month"),
    )
