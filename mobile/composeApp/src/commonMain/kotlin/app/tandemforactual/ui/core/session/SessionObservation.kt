package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import app.tandemforactual.data.store.findRecord
import app.tandemforactual.data.store.hasCachedSnapshot
import app.tandemforactual.data.store.observeClearedAccountBalance
import app.tandemforactual.data.store.observePendingWrites
import app.tandemforactual.data.store.observeRecentRecords
import app.tandemforactual.data.store.observeRecentTransactionsForAccount
import app.tandemforactual.data.store.observeRecord
import app.tandemforactual.data.store.observeRecordIds
import app.tandemforactual.data.store.observeRecords
import app.tandemforactual.data.store.observeSearchableTransactions
import app.tandemforactual.data.store.observeSyncState
import app.tandemforactual.data.store.observeVisibleRecordCount
import app.tandemforactual.data.store.readBudgetDisplaySettings
import app.tandemforactual.data.store.recentPayeeIds
import app.tandemforactual.ui.core.session.budget.payeeRecordsWithPendingCreates
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal suspend fun TandemMobileSession.findConflictRecords(
    budgetId: String,
    writes: List<OutboxOperationEntity>,
): Map<String, BudgetRecordEntity> {
    val localRepository = repository ?: return emptyMap()
    val records = mutableMapOf<String, BudgetRecordEntity>()
    writes.filter { it.status == "conflict" }.forEach { operation ->
        localRepository.findRecord(budgetId, operation.recordType, operation.recordId)?.let { record ->
            records[operation.recordId] = record
        }
    }
    return records
}

internal fun TandemMobileSession.observeAccountActivityRecords(
    budgetId: String,
    accountId: String,
    limit: Int = 100,
    offset: Int = 0,
): Flow<List<BudgetRecordEntity>>? = repository?.observeRecentTransactionsForAccount(budgetId, accountId, limit, offset)

internal fun TandemMobileSession.observeClearedAccountBalance(
    budgetId: String,
    accountId: String,
): Flow<Long>? = repository?.observeClearedAccountBalance(budgetId, accountId)

internal suspend fun TandemMobileSession.observeLocalRecords(budgetId: String) {
    val localRepository = repository ?: return
    if (!isCurrentIdentityCacheOwner()) {
        clearCachedBudgetPresentation()
        return
    }
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
        launch {
            localRepository
                .observeRecords(
                    budgetId,
                    "payee",
                ).collect { payeeRecords = budgetQueue.payeeRecordsWithPendingCreates(budgetId, it) }
        }
        launch { localRepository.observeRecords(budgetId, "category").collect { categoryRecords = it } }
        launch { localRepository.observeRecords(budgetId, "category-group").collect { categoryGroupRecords = it } }
        launch { localRepository.observeRecordIds(budgetId, "budget-month").collect { budgetMonthIds = it } }
        launch {
            localRepository.observePendingWrites(budgetId).collect { writes ->
                pendingWrites = writes
                conflictRecordsByRecordId = findConflictRecords(budgetId, writes)
            }
        }
        launch { localRepository.observeSyncState(budgetId).collect { localSyncState = it } }
        with(planning) { observe(budgetId) }
    }
}

internal suspend fun TandemMobileSession.observeSelectedBudgetMonth(
    budgetId: String,
    monthId: String,
) {
    val observationKey = "$budgetId/$monthId"
    selectedBudgetMonthObservationKey = observationKey
    selectedBudgetMonthRecord = null
    isLoadingSelectedBudgetMonth = true
    val localRepository = repository
    if (localRepository == null || budgetId.isBlank() || monthId.isBlank() || !isCurrentIdentityCacheOwner()) {
        isLoadingSelectedBudgetMonth = false
        return
    }
    localRepository.observeRecord(budgetId, "budget-month", monthId).collect { record ->
        if (selectedBudgetMonthObservationKey == observationKey) {
            selectedBudgetMonthRecord = record
            isLoadingSelectedBudgetMonth = false
        }
    }
}

internal suspend fun TandemMobileSession.observeActivitySearchRecords(budgetId: String) {
    val localRepository = repository
    if (localRepository == null || budgetId.isBlank()) {
        activitySearchRecords = emptyList()
        activitySearchRecordsBudgetId = budgetId
        return
    }
    if (!isCurrentIdentityCacheOwner()) {
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

internal suspend fun TandemMobileSession.recentPayeeIds(budgetId: String): List<String> =
    runCatching { repository?.recentPayeeIds(budgetId, RECENT_PAYEE_COUNT) }.getOrNull().orEmpty()

internal suspend fun TandemMobileSession.budgetMonthBody(
    budgetId: String,
    monthId: String,
): String? = repository?.findRecord(budgetId, "budget-month", monthId)?.let { it.localJson ?: it.remoteJson }

/** Whether the observed records all belong to this budget, so its screens can show without filling in piece by piece. */
internal fun TandemMobileSession.hasLoadedBudget(budgetId: String): Boolean =
    localSyncState?.budgetId == budgetId &&
        listOf(accountRecords, categoryRecords, transactionRecords, scheduleRecords).all { records ->
            records.all {
                it.budgetId ==
                    budgetId
            }
        }
