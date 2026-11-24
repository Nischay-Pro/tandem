package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity

suspend fun LocalBudgetRepository.importBudgetWrites(writes: List<OutboxOperationEntity>) {
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        writes.forEach { write -> if (outbox.find(write.operationId) == null) outbox.insert(write) }
    }
}

/** Edits that never reached Actual, queued again so they replay against a fresh download of the budget. */
suspend fun LocalBudgetRepository.unsentWrites(budgetId: String): List<OutboxOperationEntity> =
    budgetWrites(budgetId).filter { it.status in UNSENT_STATUSES }.map {
        it.copy(status = "queued", attemptCount = 0, serverOperationId = null, lastErrorCode = null, updatedAtEpochMs = now())
    }

private val UNSENT_STATUSES = setOf("queued", "sending", "uncertain", "conflict", "rejected")

suspend fun LocalBudgetRepository.removeBudget(budgetId: String) {
    requireIdentifier(budgetId, "budgetId")
    database.withWriteTransaction {
        database.budgetRecordDao().deleteBudget(budgetId)
        database.budgetSnapshotDao().deleteRecords(budgetId)
        database.budgetSnapshotDao().deleteProgress(budgetId)
        database.budgetSyncStateDao().deleteBudget(budgetId)
        database.outboxDao().deleteBudget(budgetId)
    }
}

suspend fun LocalBudgetRepository.clearAllLocalData() {
    val preservedPreferences =
        database.appPreferenceDao().readAll().filterNot {
            it.name == AppPreferencesStore.PENDING_BUDGET_REORDERS_KEY
        }
    database.clearAllTables()
    if (preservedPreferences.isNotEmpty()) {
        database.appPreferenceDao().putAll(preservedPreferences)
    }
}
