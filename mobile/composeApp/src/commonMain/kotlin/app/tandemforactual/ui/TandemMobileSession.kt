package app.tandemforactual.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.BudgetCatalogItem
import app.tandemforactual.api.client.PairedDevice
import app.tandemforactual.api.client.TandemApiClient
import app.tandemforactual.api.client.TandemApiException
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.BudgetSyncCoordinator
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.BudgetSyncRemoteException
import app.tandemforactual.data.store.LocalBudgetRepository
import kotlinx.coroutines.CancellationException

/** In-memory connection state. Device credentials and budget passwords are never saved here. */
internal class TandemMobileSession(
    private val apiClient: TandemApiClient,
    database: TandemDatabase?,
    nowEpochMillis: () -> Long,
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
        private set

    var transactionRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var payeeRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
        private set

    var categoryRecords by mutableStateOf<List<BudgetRecordEntity>>(emptyList())
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
    ) {
        syncError = null
        syncComplete = false
        syncedTransactionCount = 0
        val coordinator = syncCoordinator
        val localRepository = repository
        if (coordinator == null || localRepository == null) {
            syncError = "Local storage is unavailable. Restart the app and try again."
            return
        }

        isSyncing = true
        try {
            coordinator.synchronize(budgetId, encryptionPassword)
            loadLocalRecords(budgetId)
            syncedTransactionCount = transactionRecords.size
            syncComplete = true
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
        } catch (error: BudgetSyncProtocolException) {
            syncError = "The server returned budget data the app could not read."
        } catch (error: Exception) {
            syncError = "Could not sync this budget. Your local data is still available."
        } finally {
            isSyncing = false
        }
    }

    suspend fun loadLocalRecords(budgetId: String) {
        val localRepository = repository ?: return
        transactionRecords = localRepository.findRecentRecords(budgetId, "transaction", limit = 50)
        accountRecords = localRepository.findRecords(budgetId, "account")
        payeeRecords = localRepository.findRecords(budgetId, "payee")
        categoryRecords = localRepository.findRecords(budgetId, "category")
    }
}
