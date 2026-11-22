package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.OutboxOperationEntity

suspend fun LocalBudgetRepository.importBudgetWrites(writes: List<OutboxOperationEntity>) {
    database.withWriteTransaction {
        val outbox = database.outboxDao()
        writes.forEach { write -> if (outbox.find(write.operationId) == null) outbox.insert(write) }
    }
}

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
