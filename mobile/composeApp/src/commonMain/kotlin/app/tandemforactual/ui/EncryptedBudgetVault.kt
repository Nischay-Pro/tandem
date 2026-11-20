package app.tandemforactual.ui

import app.tandemforactual.data.database.TandemDatabase
import app.tandemforactual.data.store.LocalBudgetRepository

interface EncryptedBudgetVault {
    fun hasStore(budgetId: String): Boolean

    suspend fun open(
        budgetId: String,
        password: String,
    ): TandemDatabase?

    suspend fun create(
        budgetId: String,
        password: String,
    ): PendingEncryptedBudget

    fun delete(budgetId: String)

    fun deleteAll()
}

interface PendingEncryptedBudget {
    val database: TandemDatabase

    suspend fun commit()

    suspend fun discard()
}

internal enum class EncryptedBudgetUnlockResult {
    UNLOCKED,
    WRONG_PASSWORD,
    TOO_MANY_ATTEMPTS,
    NEEDS_CONNECTION,
    FAILED,
}

internal suspend fun TandemMobileSession.unlockEncryptedBudget(
    vault: EncryptedBudgetVault,
    budgetId: String,
    password: String,
): EncryptedBudgetUnlockResult {
    val existing = vault.open(budgetId, password)
    if (existing != null) {
        bindEncryptedBudgetDatabase(budgetId, existing)
        removeBudgetFromMainStorage(budgetId)
        synchronizeInBackground(budgetId, password)
        return EncryptedBudgetUnlockResult.UNLOCKED
    }

    val hadStore = vault.hasStore(budgetId)
    val pending = vault.create(budgetId, password)
    if (!hadStore) {
        val legacyWrites = mainBudgetWrites(budgetId)
        if (legacyWrites.isNotEmpty()) {
            LocalBudgetRepository(pending.database, nowEpochMillis).importBudgetWrites(legacyWrites)
        }
    }
    bindEncryptedBudgetDatabase(budgetId, pending.database)
    synchronize(budgetId = budgetId, encryptionPassword = password)
    if (syncComplete) {
        pending.commit()
        removeBudgetFromMainStorage(budgetId)
        return EncryptedBudgetUnlockResult.UNLOCKED
    }
    val reachable = isServerReachable
    val rejection = budgetPasswordRejection
    unbindEncryptedBudgetDatabase()
    pending.discard()
    return when {
        rejection == BUDGET_PASSWORD_ATTEMPTS_EXCEEDED -> EncryptedBudgetUnlockResult.TOO_MANY_ATTEMPTS
        rejection != null -> EncryptedBudgetUnlockResult.WRONG_PASSWORD
        !reachable && hadStore -> EncryptedBudgetUnlockResult.WRONG_PASSWORD
        !reachable -> EncryptedBudgetUnlockResult.NEEDS_CONNECTION
        else -> EncryptedBudgetUnlockResult.FAILED
    }
}
