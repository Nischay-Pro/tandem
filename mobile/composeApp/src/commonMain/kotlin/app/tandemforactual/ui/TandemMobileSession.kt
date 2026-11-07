package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.PairedDevice
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.BudgetSyncSummary
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.LocalWriteAction
import app.tandemforactual.data.store.LocalWriteRequest
import app.tandemforactual.ui.model.booleanValue
import app.tandemforactual.ui.model.recordBody
import app.tandemforactual.ui.reports.ReportsViewModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.uuid.Uuid

internal data class SyncPresentationState(
    val isSyncing: Boolean = false,
    val error: String? = null,
) {
    fun begin(): SyncPresentationState = copy(isSyncing = true)

    fun succeeded(): SyncPresentationState = SyncPresentationState()

    fun failed(message: String): SyncPresentationState = SyncPresentationState(error = message)

    fun stopped(): SyncPresentationState = copy(isSyncing = false)
}

internal data class TransactionEditDetails(
    val accountId: String,
    val payeeId: String?,
    val categoryId: String?,
    val date: String,
)

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

/** In-memory session state. Device credentials use a platform store; budget passwords are never persisted. */
internal class TandemMobileSession(
    private val apiClient: TandemApiClient,
    database: TandemDatabase?,
    private val nowEpochMillis: () -> Long,
    private val credentialStore: DeviceCredentialStore? = null,
) {
    private val repository = database?.let { LocalBudgetRepository(it, nowEpochMillis) }
    internal val reportViewModels = repository?.let(::ReportsViewModels)
    private val syncCoordinator =
        repository?.let {
            BudgetSyncCoordinator(
                it,
                app.tandemforactual.api.client
                    .GeneratedBudgetSyncRemote(apiClient),
            )
        }

    var pairedDevice by mutableStateOf<PairedDevice?>(null)
        private set

    var budgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var cachedBudgets by mutableStateOf<List<BudgetCatalogItem>>(emptyList())
        private set

    var budgetCatalogLoaded by mutableStateOf(false)
        private set

    var budgetCatalogVersion by mutableStateOf(0)
        private set

    var isServerReachable by mutableStateOf(false)
        private set

    var hasCachedSnapshot by mutableStateOf(false)
        private set

    var cachedSnapshotBudgetId by mutableStateOf("")
        private set

    var isPairing by mutableStateOf(false)
        private set

    var pairingError by mutableStateOf<String?>(null)
        private set

    var credentialStorageError by mutableStateOf<String?>(null)
        private set

    var deviceRevocationError by mutableStateOf<String?>(null)
        private set

    var isRevokingDevice by mutableStateOf(false)
        private set

    var isCredentialStored by mutableStateOf(false)
        private set

    var isRestoringStoredSession by mutableStateOf(false)
        private set

    var isLoadingBudgets by mutableStateOf(false)
        private set

    var budgetError by mutableStateOf<String?>(null)
        private set

    private var syncPresentation by mutableStateOf(SyncPresentationState())

    val isSyncing: Boolean get() = syncPresentation.isSyncing

    var syncComplete by mutableStateOf(false)
        private set

    val syncError: String? get() = syncPresentation.error

    var syncedTransactionCount by mutableStateOf(0)
    var cachedTransactionCount by mutableStateOf(0)
        private set

    var pendingWrites by mutableStateOf<List<OutboxOperationEntity>>(emptyList())
        private set

    var checkedWriteOperation by mutableStateOf<OutboxOperationEntity?>(null)
        private set

    var checkedWriteOperationId by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckError by mutableStateOf<String?>(null)
        private set

    var uncertainWriteCheckErrorOperationId by mutableStateOf<String?>(null)
        private set

    var isSavingTransaction by mutableStateOf(false)
        private set

    var transactionWriteMessage by mutableStateOf<String?>(null)
        private set

    var isPerformingBudgetAction by mutableStateOf(false)
        private set

    var budgetActionMessage by mutableStateOf<String?>(null)
        private set

    private var readOnlyMode = false

    fun setReadOnlyMode(enabled: Boolean) {
        readOnlyMode = enabled
    }

    fun clearBudgetActionMessage() {
        budgetActionMessage = null
    }

    fun clearTransactionWriteMessage() {
        transactionWriteMessage = null
    }

    private fun refuseWriteInReadOnlyMode(): Boolean {
        if (!readOnlyMode) return false
        transactionWriteMessage = "Read-only mode is on. Turn it off before changing this data."
        return true
    }

    var lastSyncSummary by mutableStateOf<BudgetSyncSummary?>(null)
        private set

    var localSyncState by mutableStateOf<BudgetSyncStateEntity?>(null)
        private set

    var budgetDisplaySettings by mutableStateOf<BudgetDisplaySettings?>(null)
        private set

    var budgetDisplaySettingsBudgetId by mutableStateOf<String?>(null)
        private set

    var transactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var activitySearchRecords by mutableStateOf<List<SearchableBudgetTransaction>>(emptyList())
        private set

    var activitySearchRecordsBudgetId by mutableStateOf("")
        private set

    var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var budgetMonthIds by mutableStateOf<List<String>>(emptyList())
        private set

    var selectedBudgetMonthRecord by mutableStateOf<BudgetRecordEntity?>(null)
        private set

    var accountRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var accountGroupRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var scheduleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var ruleRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    val isAuthenticated: Boolean
        get() = apiClient.isAuthenticated

    suspend fun restoreStoredSession(): String? {
        val store = credentialStore ?: return null
        if (isRestoringStoredSession) return null
        isRestoringStoredSession = true
        try {
            credentialStorageError = null
            val storedSession =
                try {
                    withContext(Dispatchers.Default) { store.read() }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    credentialStorageError = "Couldn’t access this device’s saved pairing. Retry or pair this device again."
                    return null
                } ?: return null

            pairedDevice =
                try {
                    apiClient.restoreStoredSession(storedSession)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    apiClient.forgetSession()
                    pairedDevice = null
                    budgets = emptyList()
                    credentialStorageError = "This device’s saved pairing is invalid. Pair this device again."
                    clearStoredCredential()
                    return null
                }
            isCredentialStored = true
            return apiClient.currentServerUrl.takeIf { apiClient.isAuthenticated }
        } finally {
            isRestoringStoredSession = false
        }
    }

    suspend fun forgetDevice() {
        if (pairedDevice == null || isRevokingDevice) return
        deviceRevocationError = null
        isRevokingDevice = true
        try {
            try {
                apiClient.revokeCurrentDevice()
            } catch (error: CancellationException) {
                throw error
            } catch (error: TandemApiException) {
                if (error.httpStatus != 401 && error.httpStatus != 404) {
                    deviceRevocationError = "Check your connection and try again."
                    return
                }
            } catch (_: Exception) {
                deviceRevocationError = "Check your connection and try again."
                return
            }
            clearDeviceSession()
        } finally {
            isRevokingDevice = false
        }
    }

    suspend fun forgetDeviceLocally() {
        if (pairedDevice == null || isRevokingDevice) return
        deviceRevocationError = null
        clearDeviceSession()
    }

    suspend fun pair(
        serverUrl: String,
        pairingCode: String,
        deviceName: String,
    ) {
        pairingError = null
        deviceRevocationError = null
        isPairing = true
        try {
            pairedDevice = apiClient.pair(serverUrl, pairingCode, deviceName)
            isServerReachable = true
            persistCurrentSession()
            loadBudgets()
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            pairingError = error.message ?: "Couldn’t pair this device. Check the server and pairing code."
            pairedDevice = null
        } catch (error: Exception) {
            pairingError = "Couldn’t pair this device. Check the server address and connection."
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
            budgetCatalogLoaded = true
            budgetCatalogVersion += 1
            isServerReachable = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            isServerReachable = error.httpStatus != null
            budgetError = error.message ?: "Couldn’t load budgets from this server."
            if (error.errorCode == "invalid_device_credential") {
                apiClient.forgetSession()
                pairedDevice = null
                budgets = emptyList()
                credentialStorageError = "This device pairing is no longer valid. Pair this device again."
                clearStoredCredential()
            }
        } catch (error: Exception) {
            isServerReachable = false
            budgetError = "Couldn’t load budgets from this server. Check the connection and try again."
        } finally {
            isLoadingBudgets = false
        }
    }

    suspend fun synchronize(
        budgetId: String,
        encryptionPassword: String?,
        sendQueuedWrites: Boolean = true,
        reconcileUncertainOperationId: String? = null,
    ): BudgetSyncSummary? {
        syncComplete = false
        syncedTransactionCount = 0
        lastSyncSummary = null
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            syncPresentation = syncPresentation.failed("Local storage is unavailable. Restart the app and try again.")
            return null
        }

        // Keep an earlier error visible while retrying; clear it only when the retry succeeds.
        syncPresentation = syncPresentation.begin()
        try {
            val summary =
                apiClient.withExclusiveSession {
                    coordinator.synchronize(
                        budgetId,
                        encryptionPassword,
                        sendQueuedWrites,
                        reconcileUncertainOperationId,
                    )
                }
            isServerReachable = true
            lastSyncSummary = summary
            loadLocalRecords(budgetId)
            syncedTransactionCount =
                localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            syncComplete = true
            syncPresentation = syncPresentation.succeeded()
            return summary
        } catch (error: CancellationException) {
            throw error
        } catch (error: BudgetSyncRemoteException) {
            isServerReachable = error.httpStatus != null
            syncPresentation =
                syncPresentation.failed(
                    when (error.httpStatus) {
                        401 -> "This device needs to be paired again."
                        403 -> "This device can’t open that budget."
                        404 -> "The budget is no longer available on the server."
                        400, 422 -> "Check the budget password and try again."
                        in 500..599 -> "The server couldn’t read this budget. Try again later."
                        else -> "Couldn’t sync this budget. Check the connection and try again."
                    },
                )
            return null
        } catch (error: BudgetSyncProtocolException) {
            isServerReachable = true
            syncPresentation = syncPresentation.failed("Tandem couldn’t read this budget. Check the server and try again.")
            return null
        } catch (error: Exception) {
            isServerReachable = false
            syncPresentation = syncPresentation.failed("Couldn’t sync this budget. Your saved data is still available.")
            return null
        } finally {
            if (syncPresentation.isSyncing) syncPresentation = syncPresentation.stopped()
        }
    }

    suspend fun runBankSync(
        budgetId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Actual bank sync failed. Check its provider setup and try again.") {
            apiClient.runBudgetBankSync(budgetId, encryptionPassword)
        }

    suspend fun createBudgetAccount(
        budgetId: String,
        name: String,
        offbudget: Boolean,
        accountGroupId: String?,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter an account name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Account names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t add this account. Try again.") {
            apiClient.createBudgetAccount(budgetId, cleanName, offbudget, accountGroupId, encryptionPassword)
        }
    }

    suspend fun closeBudgetAccount(
        budgetId: String,
        accountId: String,
        transferAccountId: String?,
        transferCategoryId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t close this account. Check its balance and try again.") {
            apiClient.closeBudgetAccount(
                budgetId,
                accountId,
                transferAccountId,
                transferCategoryId,
                encryptionPassword,
            )
        }

    suspend fun renameBudgetAccount(
        budgetId: String,
        accountId: String,
        name: String,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter an account name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Account names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t rename this account. Try again.") {
            apiClient.renameBudgetAccount(budgetId, accountId, cleanName, encryptionPassword)
        }
    }

    suspend fun reopenBudgetAccount(
        budgetId: String,
        accountId: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reopen this account. Try again.") {
            apiClient.reopenBudgetAccount(budgetId, accountId, encryptionPassword)
        }

    suspend fun moveBudgetAccount(
        budgetId: String,
        accountId: String,
        targetAccountId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reorder this account. Try again.") {
            apiClient.moveBudgetAccount(budgetId, accountId, targetAccountId, encryptionPassword)
        }

    suspend fun markTransactionsReconciled(
        budgetId: String,
        accountId: String,
        transactionIds: Set<String>,
        selectedTransactionIds: Set<String>,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val localRepository = repository
        if (localRepository == null || !isAuthenticated || budgetId.isBlank()) {
            transactionWriteMessage = "Open a paired budget before reconciling transactions."
            return false
        }
        val ids = transactionIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) {
            transactionWriteMessage = "There are no unreconciled transactions to review."
            return false
        }
        val selectedIds = selectedTransactionIds.intersect(ids.toSet())
        if (isSavingTransaction) return false
        isSavingTransaction = true
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
                    transactionWriteMessage = "Sync pending edits before reconciling this account."
                    return false
                }
                val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
                if (base == null) {
                    transactionWriteMessage = "Couldn’t read this transaction. Sync the budget and try again."
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
                        createdAtEpochMs = nowEpochMillis(),
                        optimisticProjection = optimistic.toProjection(),
                    )
            }
            if (requests.isEmpty()) {
                transactionWriteMessage = "No transaction changes to save."
                return true
            }
            localRepository.enqueueWrites(requests)
            transactionWriteMessage = "Reconciliation saved on this device. Sync when you’re ready."
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Couldn’t queue reconciliation changes. Try again."
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun renameBudgetCategory(
        budgetId: String,
        categoryId: String,
        name: String,
        encryptionPassword: String?,
    ): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a category name."
            return false
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Category names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t rename this category. Try again.") {
            apiClient.renameBudgetCategory(budgetId, categoryId, cleanName, encryptionPassword)
        }
    }

    suspend fun createBudgetCategory(
        budgetId: String,
        name: String,
        categoryGroupId: String,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a category name."
            return null
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Category names can be up to 100 characters."
            return null
        }
        var categoryId: String? = null
        val created =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category. Try again.") {
                categoryId = apiClient.createBudgetCategory(budgetId, categoryGroupId, cleanName, encryptionPassword)
            }
        return categoryId.takeIf { created }
    }

    suspend fun createBudgetCategoryGroup(
        budgetId: String,
        name: String,
        isIncome: Boolean,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank() || cleanName.length > 100) {
            budgetActionMessage = "Enter a category group name up to 100 characters."
            return null
        }
        var groupId: String? = null
        val created =
            performBudgetAction(budgetId, encryptionPassword, "Couldn’t create this category group. Try again.") {
                groupId = apiClient.createBudgetCategoryGroup(budgetId, cleanName, isIncome, encryptionPassword)
            }
        return groupId.takeIf { created }
    }

    suspend fun manageBudgetCategoryGroup(
        budgetId: String,
        groupId: String,
        action: String,
        name: String?,
        targetId: String?,
        encryptionPassword: String?,
    ): Boolean {
        if (action == "rename" && name.isNullOrBlank()) {
            budgetActionMessage = "Enter a category group name."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category group. Try again.") {
            apiClient.manageBudgetCategoryGroup(budgetId, groupId, action, name?.trim(), targetId, encryptionPassword)
        }
    }

    suspend fun manageBudgetCategory(
        budgetId: String,
        categoryId: String,
        action: String,
        targetId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this category. Try again.") {
            apiClient.manageBudgetCategory(budgetId, categoryId, action, targetId, encryptionPassword)
        }

    suspend fun createBudgetPayee(
        budgetId: String,
        name: String,
        encryptionPassword: String?,
    ): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            budgetActionMessage = "Enter a payee name."
            return null
        }
        if (cleanName.length > 100) {
            budgetActionMessage = "Payee names can be up to 100 characters."
            return null
        }
        budgetActionMessage = null
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return null
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            budgetActionMessage = "Pair this device and open a budget before changing it."
            return null
        }
        if (isPerformingBudgetAction) return null
        isPerformingBudgetAction = true
        try {
            val payeeId = apiClient.withExclusiveSession { apiClient.createBudgetPayee(budgetId, cleanName, encryptionPassword) }
            if (synchronize(budgetId, encryptionPassword) == null) {
                budgetActionMessage = "The payee was created, but this device couldn’t refresh the budget."
                return null
            }
            return payeeId
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetActionMessage = error.message ?: "Couldn’t add this payee. Try again."
            return null
        } catch (_: Exception) {
            budgetActionMessage = "Couldn’t add this payee. Try again."
            return null
        } finally {
            isPerformingBudgetAction = false
        }
    }

    suspend fun setBudgetCategoryAmount(
        budgetId: String,
        categoryId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean {
        if (amountMinor < 0L) {
            budgetActionMessage = "Assigned amounts can’t be negative."
            return false
        }
        if (amountMinor > 9_007_199_254_740_991L) {
            budgetActionMessage = "Enter a smaller assigned amount."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this assigned amount. Try again.") {
            apiClient.setBudgetCategoryAmount(budgetId, categoryId, month, amountMinor, encryptionPassword)
        }
    }

    suspend fun setBudgetCategoryCarryover(
        budgetId: String,
        categoryId: String,
        month: String,
        enabled: Boolean,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t update carryover. Try again.") {
            apiClient.setBudgetCategoryCarryover(budgetId, categoryId, month, enabled, encryptionPassword)
        }

    suspend fun copyBudgetMonth(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t copy last month’s assignments. Try again.") {
            apiClient.copyBudgetMonth(budgetId, month, encryptionPassword)
        }

    suspend fun holdBudgetForNextMonth(
        budgetId: String,
        month: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t hold this money for next month. Try again.") {
            apiClient.holdBudgetForNextMonth(budgetId, month, amountMinor, encryptionPassword)
        }

    suspend fun resetBudgetHold(
        budgetId: String,
        month: String,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t reset the next-month hold. Try again.") {
            apiClient.resetBudgetHold(budgetId, month, encryptionPassword)
        }

    suspend fun moveBudgetMoney(
        budgetId: String,
        month: String,
        fromCategoryId: String,
        toCategoryId: String,
        amountMinor: Long,
        encryptionPassword: String?,
    ): Boolean {
        if (fromCategoryId == toCategoryId || amountMinor <= 0L) {
            budgetActionMessage = "Choose two categories and a positive amount."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t move money. Try again.") {
            apiClient.moveMoneyBetweenCategories(
                budgetId,
                month,
                fromCategoryId,
                toCategoryId,
                amountMinor,
                encryptionPassword,
            )
        }
    }

    suspend fun deleteBudgetCategory(
        budgetId: String,
        categoryId: String,
        transferCategoryId: String?,
        encryptionPassword: String?,
    ): Boolean =
        performBudgetAction(budgetId, encryptionPassword, "Couldn’t delete this category. Try again.") {
            apiClient.deleteBudgetCategory(budgetId, categoryId, transferCategoryId, encryptionPassword)
        }

    suspend fun manageBudgetSchedule(
        budgetId: String,
        scheduleId: String,
        action: String,
        name: String?,
        encryptionPassword: String?,
        fields: JsonObject? = null,
    ): Boolean {
        val cleanName = name?.trim()
        if (action == "rename" && cleanName.isNullOrBlank()) {
            budgetActionMessage = "Enter a schedule name."
            return false
        }
        if (cleanName != null && cleanName.length > 100) {
            budgetActionMessage = "Schedule names can be up to 100 characters."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this schedule. Try again.") {
            apiClient.manageBudgetSchedule(
                budgetId,
                scheduleId,
                action,
                cleanName,
                encryptionPassword,
                fields,
            )
        }
    }

    suspend fun manageBudgetPayee(
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
            budgetActionMessage = "Enter a payee name."
            return false
        }
        if (cleanName != null && cleanName.length > 100) {
            budgetActionMessage = "Payee names can be up to 100 characters."
            return false
        }
        if (action == "merge" && (targetPayeeId.isNullOrBlank() || targetPayeeId == payeeId)) {
            budgetActionMessage = "Choose another payee to merge into."
            return false
        }
        return performBudgetAction(budgetId, encryptionPassword, "Couldn’t update this payee. Try again.") {
            apiClient.manageBudgetPayee(
                budgetId,
                payeeId,
                action,
                cleanName,
                targetPayeeId,
                encryptionPassword,
                categoryId,
            )
        }
    }

    private suspend fun performBudgetAction(
        budgetId: String,
        encryptionPassword: String?,
        failureMessage: String,
        action: suspend () -> Unit,
    ): Boolean {
        budgetActionMessage = null
        if (refuseWriteInReadOnlyMode()) {
            budgetActionMessage = transactionWriteMessage
            return false
        }
        if (budgetId.isBlank() || !apiClient.isAuthenticated) {
            budgetActionMessage = "Pair this device and open a budget before changing it."
            return false
        }
        if (isPerformingBudgetAction) return false
        isPerformingBudgetAction = true
        try {
            apiClient.withExclusiveSession { action() }
            if (synchronize(budgetId, encryptionPassword) == null) {
                budgetActionMessage = syncError ?: "Actual was updated, but this device couldn’t refresh the budget."
                return false
            }
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: TandemApiException) {
            budgetActionMessage = error.message ?: failureMessage
            return false
        } catch (_: Exception) {
            budgetActionMessage = failureMessage
            return false
        } finally {
            isPerformingBudgetAction = false
        }
    }

    suspend fun updateTransaction(
        budgetId: String,
        transactionId: String,
        amountText: String,
        notes: String,
        originalAmountMinor: Long,
        displaySettings: BudgetDisplaySettings? = null,
        accountId: String? = null,
        payeeId: String? = null,
        categoryId: String? = null,
        date: String? = null,
        updateDetails: Boolean = false,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val enteredAmount = parseAmountMinorUnits(amountText, displaySettings)
        if (enteredAmount == null || enteredAmount < 0) {
            transactionWriteMessage = "Enter a valid positive amount using this device’s number format."
            return false
        }
        if (enteredAmount > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local budget storage is unavailable. Reopen the budget and try again."
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "The latest Actual transaction is unavailable. Sync the budget before editing."
            return false
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        if (base == null) {
            transactionWriteMessage = "Couldn’t read this transaction. Sync the budget before editing."
            return false
        }
        val requestedDate = if (updateDetails) normalizeTransactionDate(date.orEmpty()) else null
        val requestedAccountId = if (updateDetails) accountId?.takeIf(String::isNotBlank) else null
        if (updateDetails && requestedAccountId == null) {
            transactionWriteMessage = "Choose an account from this budget before saving."
            return false
        }
        if (requestedAccountId != null) {
            val account = accountRecords.firstOrNull { it.recordId == requestedAccountId }
            val accountBody = account?.recordBody()
            if (account == null || accountBody?.booleanValue("closed") == true) {
                transactionWriteMessage = "Choose an open account from this budget."
                return false
            }
            if (accountBody?.booleanValue("offbudget") == true || accountBody?.booleanValue("offBudget") == true) {
                transactionWriteMessage = "Choose an on-budget account for this transaction."
                return false
            }
        }
        if (updateDetails && payeeId != null && payeeRecords.none { it.recordId == payeeId }) {
            transactionWriteMessage = "Choose an existing payee or clear the payee field."
            return false
        }
        if (updateDetails && categoryId != null && categoryRecords.none { it.recordId == categoryId }) {
            transactionWriteMessage = "Choose a category from this budget before saving."
            return false
        }
        if (updateDetails && requestedDate == null) {
            transactionWriteMessage = "Choose a valid transaction date."
            return false
        }
        if (requestedDate != null && requestedDate < "1995-01-01") {
            transactionWriteMessage = "Choose a date on or after ${formatDate(SampleDate(1995, 1, 1))}."
            return false
        }
        val signedAmount = if (originalAmountMinor < 0) -enteredAmount else enteredAmount
        val patch =
            buildTransactionEditPatch(
                base = base,
                signedAmount = signedAmount,
                fallbackCurrentAmount = current.amountMinor,
                notes = notes,
                details =
                    if (updateDetails) {
                        TransactionEditDetails(
                            accountId = checkNotNull(requestedAccountId),
                            payeeId = payeeId,
                            categoryId = categoryId,
                            date = checkNotNull(requestedDate),
                        )
                    } else {
                        null
                    },
            )
        if (patch.isEmpty()) {
            transactionWriteMessage = "There are no transaction changes to save."
            return false
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
            transactionWriteMessage = null
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Couldn’t save this edit on this device. Try again."
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun deleteTransaction(
        budgetId: String,
        transactionId: String,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local budget storage is unavailable. Reopen the budget and try again."
            return false
        }
        val pendingForRecord =
            localRepository
                .readPendingWrites(budgetId)
                .filter { it.recordType == "transaction" && it.recordId == transactionId }
                .maxByOrNull { it.updatedAtEpochMs }
        if (pendingForRecord?.action == LocalWriteAction.CREATE.name && pendingForRecord.status == "queued") {
            isSavingTransaction = true
            try {
                localRepository.discardWrite(pendingForRecord.operationId)
                transactionWriteMessage = null
                loadLocalRecords(budgetId)
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                transactionWriteMessage = "Couldn’t remove this transaction. Review Pending Changes."
                loadLocalRecords(budgetId)
                return false
            } finally {
                isSavingTransaction = false
            }
        }
        if (pendingForRecord != null && pendingForRecord.status != "queued") {
            transactionWriteMessage = "Review this transaction in Pending Changes before deleting it."
            return false
        }
        val current = localRepository.findRecord(budgetId, "transaction", transactionId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "The latest Actual transaction is unavailable. Sync the budget before deleting it."
            return false
        }

        isSavingTransaction = true
        try {
            val operationId = Uuid.random().toString()
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.DELETE,
                    baseRevision = revision,
                    baseSnapshotJson = baseJson,
                    patchJson = "{}",
                    optimisticJson = null,
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection = null,
                ),
            )
            transactionWriteMessage = null
            loadLocalRecords(budgetId)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Couldn’t delete this transaction. Review Pending Changes."
            loadLocalRecords(budgetId)
            return false
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun createTransaction(
        budgetId: String,
        accountId: String,
        payeeId: String?,
        categoryId: String?,
        amountText: String,
        dateText: String,
        notes: String,
        encryptionPassword: String?,
        displaySettings: BudgetDisplaySettings? = null,
    ): Boolean {
        transactionWriteMessage = null
        if (refuseWriteInReadOnlyMode()) return false
        val amount = parseAmountMinorUnits(amountText, displaySettings)
        if (amount == null || amount == 0L) {
            transactionWriteMessage = "Enter an amount greater than zero."
            return false
        }
        if (amount == Long.MIN_VALUE || kotlin.math.abs(amount) > MAX_TRANSACTION_AMOUNT_MINOR_UNITS) {
            transactionWriteMessage = "Largest amount is ${formatCurrency(MAX_TRANSACTION_AMOUNT_MINOR_UNITS, displaySettings)}."
            return false
        }
        val date = normalizeTransactionDate(dateText)
        if (date == null) {
            transactionWriteMessage = "Enter the date as YYYY-MM-DD."
            return false
        }
        val localRepository = repository
        val coordinator = syncCoordinator
        if (localRepository == null || coordinator == null || !isAuthenticated) {
            transactionWriteMessage = "Pair this device and open a budget before adding a transaction."
            return false
        }
        val account = accountRecords.firstOrNull { it.recordId == accountId }
        if (account == null) {
            transactionWriteMessage = "Choose an account from this budget before saving."
            return false
        }
        val accountBody = account.recordBody()
        if (accountBody?.booleanValue("offbudget") == true || accountBody?.booleanValue("offBudget") == true) {
            transactionWriteMessage = "Choose an on-budget account for this transaction."
            return false
        }
        if (payeeId != null && payeeRecords.none { it.recordId == payeeId }) {
            transactionWriteMessage = "Choose an existing payee or clear the payee field."
            return false
        }
        if (categoryId != null && categoryRecords.none { it.recordId == categoryId }) {
            transactionWriteMessage = "Choose a category from this budget before saving."
            return false
        }
        val transactionId = "tandem:${Uuid.random()}"
        val operationId = Uuid.random().toString()
        val patch =
            buildJsonObject {
                put("account", account.recordId)
                put("amount", amount)
                put("date", date)
                put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
                put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
                put("notes", notes)
                put("cleared", false)
            }
        val optimistic =
            buildJsonObject {
                put("id", transactionId)
                put("account", account.recordId)
                put("amount", amount)
                put("date", date)
                put("payee", payeeId?.let(::JsonPrimitive) ?: JsonNull)
                put("category", categoryId?.let(::JsonPrimitive) ?: JsonNull)
                put("notes", notes)
                put("cleared", false)
                put("reconciled", JsonNull)
                put("importedId", JsonNull)
                put("importedPayee", JsonNull)
                put("startingBalance", JsonNull)
                put("schedule", JsonNull)
                put("transferId", JsonNull)
                put("isParent", false)
                put("isChild", false)
                put("parentId", JsonNull)
                put("subtransactions", JsonArray(emptyList()))
            }
        isSavingTransaction = true
        try {
            localRepository.enqueueWrite(
                LocalWriteRequest(
                    operationId = operationId,
                    idempotencyKey = operationId,
                    budgetId = budgetId,
                    recordType = "transaction",
                    recordId = transactionId,
                    action = LocalWriteAction.CREATE,
                    baseRevision = null,
                    baseSnapshotJson = null,
                    patchJson = patch.toString(),
                    optimisticJson = optimistic.toString(),
                    createdAtEpochMs = nowEpochMillis(),
                    optimisticProjection =
                        LocalRecordProjection(
                            recordDate = date,
                            accountId = account.recordId,
                            categoryId = categoryId,
                            payeeId = payeeId,
                            amountMinor = amount,
                            budgetMonth = date.take(7),
                        ),
                ),
            )
            val summary = synchronize(budgetId, encryptionPassword)
            val savedWrite = localRepository.findWrite(operationId)
            transactionWriteMessage =
                when (savedWrite?.status) {
                    "committed" -> "Transaction added to Actual."
                    "conflict" -> "This transaction is already in Actual. Sync and try again."
                    "uncertain", "sending" -> "Status unknown. Check before retrying."
                    "rejected" -> "Actual didn’t add this transaction. The previous value was kept."
                    else ->
                        if (summary == null) {
                            "Saved on this device. Status unknown; check before retrying."
                        } else {
                            "The transaction is saved on this device and is waiting to sync."
                        }
                }
            loadLocalRecords(budgetId)
            return savedWrite?.status == "committed"
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "The transaction is saved on this device. Status unknown; check before retrying."
            loadLocalRecords(budgetId)
            return false
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
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository
        if (localRepository == null) {
            transactionWriteMessage = "Local storage is unavailable. Restart the app and try again."
            return
        }
        val operation = localRepository.readPendingWrites(budgetId).firstOrNull { it.operationId == operationId }
        if (operation == null || operation.status != "conflict") {
            transactionWriteMessage = "This edit no longer needs review. Sync and try again."
            loadLocalRecords(budgetId)
            return
        }
        if (!keepDeviceVersion) {
            localRepository.discardWrite(operationId)
            loadLocalRecords(budgetId)
            transactionWriteMessage = "Kept the latest Actual version and discarded your edit."
            return
        }
        val current = localRepository.findRecord(budgetId, operation.recordType, operation.recordId)
        val baseJson = current?.remoteJson
        val revision = current?.remoteRevision
        if (current == null || current.remoteDeleted || baseJson == null || revision.isNullOrBlank()) {
            transactionWriteMessage = "This transaction was deleted in Actual. Keep Actual to discard your edit."
            return
        }
        val base = runCatching { Json.parseToJsonElement(baseJson).jsonObject }.getOrNull()
        val patch = runCatching { Json.parseToJsonElement(operation.patchJson).jsonObject }.getOrNull()
        if (base == null || patch == null || operation.action != "UPDATE") {
            transactionWriteMessage = "This edit can’t be applied to the latest Actual version. Keep Actual and discard your edit."
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
                    summary == null -> "Your edit is saved on this device. Status unknown; check before retrying."
                    summary.writesCommitted > 0 -> "Your edit was synced to Actual."
                    summary.writesConflicted > 0 -> "Actual changed this transaction again. Review the latest versions."
                    summary.writesUncertain > 0 -> "Status unknown. Check before retrying."
                    summary.writesRejected > 0 -> "Actual didn’t apply your edit. The previous value was restored."
                    else -> "Your edit is waiting to sync."
                }
            loadLocalRecords(budgetId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            transactionWriteMessage = "Your edit is saved on this device. Status unknown; check before retrying."
            loadLocalRecords(budgetId)
        } finally {
            isSavingTransaction = false
        }
    }

    suspend fun discardWrite(
        budgetId: String,
        operationId: String,
    ) {
        transactionWriteMessage = null
        clearUncertainWriteCheck()
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        localRepository.discardWrite(operationId)
        loadLocalRecords(budgetId)
        transactionWriteMessage = "Your edit was discarded. Actual wasn’t changed."
    }

    suspend fun retryUncertainWrite(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        transactionWriteMessage = null
        clearUncertainWriteCheck()
        if (refuseWriteInReadOnlyMode()) return
        val localRepository = repository ?: return
        localRepository.retryUncertainWrite(operationId)
        synchronize(budgetId, encryptionPassword)
        loadLocalRecords(budgetId)
    }

    suspend fun checkUncertainWriteStatus(
        budgetId: String,
        operationId: String,
        encryptionPassword: String?,
    ) {
        if (isSyncing) return
        clearUncertainWriteCheck()
        val summary =
            synchronize(
                budgetId = budgetId,
                encryptionPassword = encryptionPassword,
                sendQueuedWrites = false,
                reconcileUncertainOperationId = operationId,
            )
        if (summary == null) {
            uncertainWriteCheckError = syncError ?: "Couldn’t check the server. Try again."
            uncertainWriteCheckErrorOperationId = operationId
            return
        }

        checkedWriteOperationId = operationId
        if (summary.operationStatusErrorCode != null && summary.operationStatusErrorCode != "http-404") {
            uncertainWriteCheckError =
                when (summary.operationStatusErrorCode) {
                    "http-401" -> "Pair this device again to check the status."
                    "http-403" -> "This device can’t open this budget."
                    "http-503" -> "Tandem is temporarily unavailable. Try again shortly."
                    "network-error" -> "Couldn’t reach Tandem. Check the connection and try again."
                    else -> "Couldn’t confirm the server status. Try again."
                }
            uncertainWriteCheckErrorOperationId = operationId
        }
        try {
            checkedWriteOperation = repository?.findWrite(operationId)
            if (checkedWriteOperation == null) {
                uncertainWriteCheckError = "This edit is no longer waiting to sync."
                uncertainWriteCheckErrorOperationId = operationId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            uncertainWriteCheckError = "Couldn’t check this edit’s status on this device."
            uncertainWriteCheckErrorOperationId = operationId
        }
    }

    private fun clearUncertainWriteCheck() {
        checkedWriteOperation = null
        checkedWriteOperationId = null
        uncertainWriteCheckError = null
        uncertainWriteCheckErrorOperationId = null
    }

    suspend fun loadLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        cachedSnapshotBudgetId = budgetId
        hasCachedSnapshot = localRepository.hasCachedSnapshot(budgetId)
        budgetDisplaySettingsBudgetId = budgetId
        budgetDisplaySettings = localRepository.readBudgetDisplaySettings(budgetId)
        localSyncState = localRepository.readSyncState(budgetId)
        transactionRecords = localRepository.findRecentRecords(budgetId, "transaction", limit = 50)
        cachedTransactionCount =
            localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        accountRecords = localRepository.findRecords(budgetId, "account")
        accountGroupRecords = localRepository.findRecords(budgetId, "account-group")
        scheduleRecords = localRepository.findRecords(budgetId, "schedule")
        ruleRecords = localRepository.findRecords(budgetId, "rule")
        payeeRecords = localRepository.findRecords(budgetId, "payee")
        categoryRecords = localRepository.findRecords(budgetId, "category")
        categoryGroupRecords = localRepository.findRecords(budgetId, "category-group")
        budgetMonthIds = localRepository.findRecordIds(budgetId, "budget-month")
        selectedBudgetMonthRecord = null
        pendingWrites = localRepository.readPendingWrites(budgetId)
    }

    fun observeAccountActivityRecords(
        budgetId: String,
        accountId: String,
        limit: Int = 100,
    ): Flow<List<BudgetRecordEntity>>? = repository?.observeRecentTransactionsForAccount(budgetId, accountId, limit)

    fun observeClearedAccountBalance(
        budgetId: String,
        accountId: String,
    ): Flow<Long>? = repository?.observeClearedAccountBalance(budgetId, accountId)

    suspend fun observeLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        cachedSnapshotBudgetId = budgetId
        hasCachedSnapshot = localRepository.hasCachedSnapshot(budgetId)
        budgetDisplaySettingsBudgetId = budgetId
        budgetDisplaySettings = localRepository.readBudgetDisplaySettings(budgetId)
        coroutineScope {
            launch {
                localRepository.observeRecentRecords(budgetId, "transaction", 50).collect { transactionRecords = it }
            }
            launch {
                localRepository.observeVisibleRecordCount(budgetId, "transaction").collect {
                    cachedTransactionCount = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
            }
            launch { localRepository.observeRecords(budgetId, "account").collect { accountRecords = it } }
            launch { localRepository.observeRecords(budgetId, "account-group").collect { accountGroupRecords = it } }
            launch { localRepository.observeRecords(budgetId, "schedule").collect { scheduleRecords = it } }
            launch { localRepository.observeRecords(budgetId, "rule").collect { ruleRecords = it } }
            launch { localRepository.observeRecords(budgetId, "payee").collect { payeeRecords = it } }
            launch { localRepository.observeRecords(budgetId, "category").collect { categoryRecords = it } }
            launch { localRepository.observeRecords(budgetId, "category-group").collect { categoryGroupRecords = it } }
            launch { localRepository.observeRecordIds(budgetId, "budget-month").collect { budgetMonthIds = it } }
            launch { localRepository.observePendingWrites(budgetId).collect { pendingWrites = it } }
            launch { localRepository.observeSyncState(budgetId).collect { localSyncState = it } }
        }
    }

    suspend fun observeSelectedBudgetMonth(
        budgetId: String,
        monthId: String,
    ) {
        val localRepository = repository ?: return
        selectedBudgetMonthRecord = null
        if (budgetId.isBlank() || monthId.isBlank()) return
        localRepository.observeRecord(budgetId, "budget-month", monthId).collect { selectedBudgetMonthRecord = it }
    }

    suspend fun observeActivitySearchRecords(budgetId: String) {
        val localRepository = repository
        if (localRepository == null || budgetId.isBlank()) {
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = budgetId
            return
        }
        if (activitySearchRecordsBudgetId != budgetId) {
            activitySearchRecords = emptyList()
            activitySearchRecordsBudgetId = budgetId
        }
        localRepository.observeSearchableTransactions(budgetId).collect { records ->
            if (records != activitySearchRecords) activitySearchRecords = records
        }
    }

    suspend fun hasCachedBudgetSnapshot(budgetId: String): Boolean = repository?.hasCachedSnapshot(budgetId) == true

    suspend fun loadCachedBudgets(): List<BudgetCatalogItem> {
        val localRepository = repository ?: return emptyList()
        val cached =
            localRepository.findCachedBudgetIds().map { budgetId ->
                val currencyCode = localRepository.readBudgetDisplaySettings(budgetId)?.currencyCode
                BudgetCatalogItem(
                    budgetId = budgetId,
                    name = currencyCode?.let { "Saved budget · $it" } ?: "Saved budget",
                    isEncrypted = false,
                )
            }
        cachedBudgets = cached
        return cached
    }

    private suspend fun persistCurrentSession() {
        val store = credentialStore ?: return
        val storedSession = apiClient.exportStoredSession() ?: return
        credentialStorageError = null
        try {
            withContext(Dispatchers.Default) { store.write(storedSession) }
            isCredentialStored = true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            isCredentialStored = false
            credentialStorageError = "This pairing will end when the app closes because saved access is unavailable."
        }
    }

    private suspend fun clearStoredCredential() {
        val store = credentialStore ?: return
        try {
            withContext(Dispatchers.Default) { store.clear() }
            isCredentialStored = false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            credentialStorageError = "Couldn’t remove this device’s saved access."
        }
    }

    private suspend fun clearDeviceSession() {
        apiClient.forgetSession()
        pairedDevice = null
        budgets = emptyList()
        budgetCatalogLoaded = false
        isServerReachable = false
        budgetError = null
        clearStoredCredential()
    }
}

private suspend fun LocalBudgetRepository.hasCachedSnapshot(budgetId: String): Boolean {
    val checkpoint = readSyncCheckpoint(budgetId) ?: return false
    return checkpoint.generation.isNotBlank() && checkpoint.cursor != null
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

private fun normalizeTransactionDate(value: String): String? {
    val text = value.trim()
    if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(text)) return null
    val year = text.substring(0, 4).toIntOrNull() ?: return null
    val month = text.substring(5, 7).toIntOrNull() ?: return null
    val day = text.substring(8, 10).toIntOrNull() ?: return null
    if (year == 0 || month !in 1..12) return null
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth =
        when (month) {
            2 -> if (leapYear) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    return text.takeIf { day in 1..daysInMonth }
}
