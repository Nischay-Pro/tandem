package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.database.closeDatabase
import app.tandemforactual.data.store.LocalBudgetRepository
import app.tandemforactual.data.store.budgetWrites
import app.tandemforactual.data.store.importBudgetWrites
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.data.store.removeBudget
import app.tandemforactual.data.store.unsentWrites
import app.tandemforactual.ui.core.session.budget.pendingBudgetMutationCount
import app.tandemforactual.ui.core.state.UNSYNCED_TRANSACTION_WRITE_STATUSES
import kotlinx.coroutines.CancellationException
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
    sealedInboxSource = null
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

/** Why [budgetId] can't be removed from this device yet, or null when it can. */
internal suspend fun TandemMobileSession.removeBudgetBlocker(budgetId: String): String? =
    if (unsentChangeCount(budgetId) > 0) "Sync or resolve this budget’s changes before removing it from this device." else null

internal suspend fun TandemMobileSession.removeBudgetFromDevice(
    budgetId: String,
    vault: EncryptedBudgetVault?,
): String? {
    removeBudgetBlocker(budgetId)?.let { return it }
    if (boundEncryptedBudgetId == budgetId) unbindEncryptedBudgetDatabase()
    mainStorage?.repository?.removeBudget(budgetId)
    apiClient.removeBudget(budgetId)
    vault?.delete(budgetId)
    forgetSealedInboxKeys(budgetId)
    cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
    loadLocalRecords(budgetId)
    return null
}

/** Edits to [budgetId] on this device that Actual doesn't have yet. */
internal suspend fun TandemMobileSession.unsentChangeCount(budgetId: String): Int =
    budgetQueue.pendingBudgetMutationCount(budgetId) +
        repository?.readPendingWrites(budgetId).orEmpty().count { it.status in UNSYNCED_TRANSACTION_WRITE_STATUSES }

/**
 * Why [budgetId] can't be downloaded again without losing edits Actual doesn't have yet, or null when it can. An
 * encrypted budget's edits live in its vault, so it must be unlocked to carry them over to the new copy.
 */
internal suspend fun TandemMobileSession.downloadAgainBlocker(
    budgetId: String,
    password: String?,
): String? =
    when {
        encryptedStorageMissing(budgetId) -> "Unlock this budget first so Tandem can keep the changes Actual doesn’t have yet."
        boundEncryptedBudgetId == budgetId &&
            replayPassword(budgetId, password) == null &&
            repository?.unsentWrites(budgetId).orEmpty().isNotEmpty() ->
            "Lock and unlock this budget again first so Tandem can keep the changes Actual doesn’t have yet."
        else -> null
    }

private fun TandemMobileSession.replayPassword(
    budgetId: String,
    password: String?,
): String? = scheduleActionEncryptionPasswords[budgetId]?.takeIf { it.isNotBlank() } ?: password?.takeIf { it.isNotBlank() }

/**
 * Recovers from a reset in Actual: the device's copy is set aside, the budget is downloaded fresh, and edits it could not
 * send are queued again to replay against the new copy. Returns how many unsent edits there were, or null when
 * [downloadAgainBlocker] says it would lose them and nothing was changed.
 */
internal suspend fun TandemMobileSession.downloadBudgetAgain(
    budgetId: String,
    vault: EncryptedBudgetVault?,
    password: String? = null,
): Int? {
    if (downloadAgainBlocker(budgetId, password) != null) return null
    if (boundEncryptedBudgetId == budgetId) applySealedInbox(budgetId, replayPassword(budgetId, password))
    val unsent = unsentChangeCount(budgetId)
    val replay = repository?.unsentWrites(budgetId).orEmpty()
    val encrypted = boundEncryptedBudgetId == budgetId
    val vaultPassword = replayPassword(budgetId, password)
    if (encrypted) unbindEncryptedBudgetDatabase()
    apiClient.retireBudget(budgetId)
    mainStorage?.repository?.removeBudget(budgetId)
    vault?.delete(budgetId)
    forgetSealedInboxKeys(budgetId)
    if (encrypted) {
        if (!storeReplayInNewVault(budgetId, vault, vaultPassword, replay)) writesToReplay[budgetId] = replay
    } else {
        mainStorage?.repository?.importBudgetWrites(replay)
    }
    cachedBudgets = cachedBudgets.filterNot { it.budgetId == budgetId }
    loadLocalRecords(budgetId)
    loadBudgets()
    return unsent
}

/**
 * Puts an encrypted budget's unsent edits straight into a new, empty vault, so they survive until it's opened and
 * downloaded again instead of living only in memory. False when there's no vault or password to make one with.
 */
private suspend fun TandemMobileSession.storeReplayInNewVault(
    budgetId: String,
    vault: EncryptedBudgetVault?,
    password: String?,
    replay: List<OutboxOperationEntity>,
): Boolean {
    if (replay.isEmpty()) return true
    if (vault == null || password.isNullOrBlank()) return false
    val fresh = vault.create(budgetId, password)
    try {
        LocalBudgetRepository(fresh.database, nowEpochMillis).importBudgetWrites(replay)
        fresh.commit()
    } catch (error: CancellationException) {
        fresh.discard()
        throw error
    } catch (_: Exception) {
        fresh.discard()
        return false
    } finally {
        runCatching { fresh.database.closeDatabase() }
    }
    return true
}

internal suspend fun TandemMobileSession.mainBudgetWrites(budgetId: String): List<OutboxOperationEntity> =
    mainStorage?.repository?.budgetWrites(budgetId).orEmpty()

internal suspend fun TandemMobileSession.removeBudgetFromMainStorage(budgetId: String) {
    mainStorage?.repository?.removeBudget(budgetId)
}
