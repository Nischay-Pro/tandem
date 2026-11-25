package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.store.countVisibleRecords
import app.tandemforactual.data.store.findRecentRecords
import app.tandemforactual.data.store.findRecordIds
import app.tandemforactual.data.store.findRecords
import app.tandemforactual.data.store.hasCachedSnapshot
import app.tandemforactual.data.store.observeUncategorizedTransactions
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.readPendingWrites
import app.tandemforactual.data.store.readSyncState
import app.tandemforactual.ui.core.session.budget.payeeRecordsWithPendingCreates
import kotlinx.coroutines.flow.first

internal suspend fun TandemMobileSession.loadCachedBudgetRecords(budgetId: String) {
    val localRepository = repository ?: return
    if (!isCurrentIdentityCacheOwner()) {
        clearCachedBudgetPresentation()
        return
    }
    cachedSnapshotBudgetId = budgetId
    hasCachedSnapshot = localRepository.hasCachedSnapshot(budgetId)
    budgetDisplaySettingsBudgetId = budgetId
    budgetDisplaySettings = localRepository.readBudgetDisplaySettings(budgetId)
    localSyncState = localRepository.readSyncState(budgetId)
    transactionRecords = localRepository.findRecentRecords(budgetId, "transaction", limit = 50)
    uncategorizedTransactionRecords = localRepository.observeUncategorizedTransactions(budgetId).first()
    cachedTransactionCount =
        localRepository.countVisibleRecords(budgetId, "transaction").coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    accountRecords = localRepository.findRecords(budgetId, "account")
    accountGroupRecords = localRepository.findRecords(budgetId, "account-group")
    scheduleRecords = localRepository.findRecords(budgetId, "schedule")
    ruleRecords = localRepository.findRecords(budgetId, "rule")
    payeeRecords = budgetQueue.payeeRecordsWithPendingCreates(budgetId, localRepository.findRecords(budgetId, "payee"))
    categoryRecords = localRepository.findRecords(budgetId, "category")
    categoryGroupRecords = localRepository.findRecords(budgetId, "category-group")
    budgetMonthIds = localRepository.findRecordIds(budgetId, "budget-month")
    pendingWrites = localRepository.readPendingWrites(budgetId)
    conflictRecordsByRecordId = findConflictRecords(budgetId, pendingWrites)
    activity.refreshActivityTransactionPages(budgetId)
}

internal fun TandemMobileSession.clearCachedBudgetRecords() {
    budgets = emptyList()
    cachedBudgets = emptyList()
    budgetCatalogLoaded = false
    budgetCatalogVersion += 1
    hasCachedSnapshot = false
    cachedSnapshotBudgetId = ""
    budgetDisplaySettings = null
    budgetDisplaySettingsBudgetId = null
    localSyncState = null
    transactionRecords = emptyList()
    uncategorizedTransactionRecords = emptyList()
    activity.reset()
    activitySearchRecords = emptyList()
    activitySearchRecordsBudgetId = ""
    payeeRecords = emptyList()
    categoryRecords = emptyList()
    categoryGroupRecords = emptyList()
    budgetMonthIds = emptyList()
    selectedBudgetMonthRecord = null
    isLoadingSelectedBudgetMonth = false
    selectedBudgetMonthObservationKey = ""
    accountRecords = emptyList()
    accountGroupRecords = emptyList()
    scheduleRecords = emptyList()
    ruleRecords = emptyList()
    pendingWrites = emptyList()
    conflictRecordsByRecordId = emptyMap()
    cachedTransactionCount = 0
    syncedTransactionCount = 0
}
