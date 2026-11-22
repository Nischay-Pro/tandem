package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

internal fun TandemMobileSession.encryptedStorageMissing(budgetId: String): Boolean =
    budgetId in encryptedStorageRequired && budgetStorage?.budgetId != budgetId

internal suspend fun TandemMobileSession.bindEncryptedBudgetDatabase(
    budgetId: String,
    database: TandemDatabase?,
) {
    val previous =
        synchronizationMutex.withLock {
            val replaced = budgetStorage
            budgetStorage = database?.let { BudgetStorage(it, budgetId) }
            budgetQueue.budgetQueueLoaded = false
            budgetQueue.budgetReorderQueueState = BudgetReorderQueueState()
            databaseGeneration += 1
            replaced
        }
    if (previous != null && previous.budgetId != null) {
        sessionScope.launch {
            delay(ENCRYPTED_DATABASE_CLOSE_DELAY_MILLIS)
            runCatching { previous.database.closeDatabase() }
        }
    }
    loadLocalRecords(budgetId)
}

internal suspend fun TandemMobileSession.unbindEncryptedBudgetDatabase() {
    val budgetId = budgetStorage?.budgetId ?: return
    scheduleActionEncryptionPasswords.remove(budgetId)
    bindEncryptedBudgetDatabase(budgetId, null)
}

internal fun TandemMobileSession.synchronizeInBackground(
    budgetId: String,
    encryptionPassword: String,
) {
    sessionScope.launch { synchronize(budgetId = budgetId, encryptionPassword = encryptionPassword) }
}

internal fun TandemMobileSession.forgetEncryptionPasswords() {
    scheduleActionEncryptionPasswords.clear()
}

internal suspend fun TandemMobileSession.removeBudgetFromDevice(
    budgetId: String,
    vault: EncryptedBudgetVault?,
): String? {
    val unsynced =
        budgetQueue.pendingBudgetMutationCount(budgetId) > 0 ||
            repository?.readPendingWrites(budgetId).orEmpty().any { it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
    if (unsynced) return "Sync or resolve this budget’s changes before removing it from this device."
    if (boundEncryptedBudgetId == budgetId) unbindEncryptedBudgetDatabase()
    mainStorage?.repository?.removeBudget(budgetId)
    apiClient.removeBudget(budgetId)
    vault?.delete(budgetId)
    cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
    loadLocalRecords(budgetId)
    return null
}

/**
 * Recovers from a reset in Actual: the device's copy and any edits it could not send are kept as a backup, and the
 * budget is downloaded fresh. Returns how many unsent edits the backup holds.
 */
internal suspend fun TandemMobileSession.downloadBudgetAgain(
    budgetId: String,
    vault: EncryptedBudgetVault?,
): Int {
    val unsent =
        budgetQueue.pendingBudgetMutationCount(budgetId) +
            repository?.readPendingWrites(budgetId).orEmpty().count { it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }
    if (boundEncryptedBudgetId == budgetId) unbindEncryptedBudgetDatabase()
    apiClient.retireBudget(budgetId)
    mainStorage?.repository?.removeBudget(budgetId)
    vault?.delete(budgetId)
    cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
    loadLocalRecords(budgetId)
    loadBudgets()
    return unsent
}

internal suspend fun TandemMobileSession.mainBudgetWrites(budgetId: String): List<OutboxOperationEntity> =
    mainStorage?.repository?.budgetWrites(budgetId).orEmpty()

internal suspend fun TandemMobileSession.removeBudgetFromMainStorage(budgetId: String) {
    mainStorage?.repository?.removeBudget(budgetId)
}
