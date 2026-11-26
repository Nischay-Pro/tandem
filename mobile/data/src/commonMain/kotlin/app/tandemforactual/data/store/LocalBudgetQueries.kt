package app.tandemforactual.data.store

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.BudgetSyncStateEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

suspend fun LocalBudgetRepository.findRecords(
    budgetId: String,
    recordType: String,
    accountId: String? = null,
    categoryId: String? = null,
    payeeId: String? = null,
    fromDate: String? = null,
    throughDate: String? = null,
): List<BudgetRecordEntity> =
    database.budgetRecordDao().findVisible(
        budgetId,
        recordType,
        accountId,
        categoryId,
        payeeId,
        fromDate,
        throughDate,
    )

/** The account's balance on this device through [throughDate], or across every date when it is null, as Actual counts it when closing. */
suspend fun LocalBudgetRepository.readAccountBalance(
    budgetId: String,
    accountId: String,
    throughDate: String? = null,
): Long = observeAccountBalanceThrough(budgetId, accountId, throughDate ?: "9999-12-31").first()

suspend fun LocalBudgetRepository.findRecordsByIds(
    budgetId: String,
    recordType: String,
    recordIds: Collection<String>,
): List<BudgetRecordEntity> {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(recordType, "recordType")
    val ids = recordIds.distinct()
    ids.forEach { requireIdentifier(it, "recordId") }
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(400).flatMap { chunk ->
        database.budgetRecordDao().findVisibleByIds(budgetId, recordType, chunk)
    }
}

suspend fun LocalBudgetRepository.findRecordIds(
    budgetId: String,
    recordType: String,
): List<String> {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(recordType, "recordType")
    return database.budgetRecordDao().findVisibleRecordIds(budgetId, recordType)
}

suspend fun LocalBudgetRepository.findRecentRecords(
    budgetId: String,
    recordType: String,
    limit: Int,
): List<BudgetRecordEntity> {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(recordType, "recordType")
    require(limit in 1..500) { "limit must be between 1 and 500" }
    return database.budgetRecordDao().findRecentVisible(budgetId, recordType, limit)
}

suspend fun LocalBudgetRepository.latestCategoryForPayee(
    budgetId: String,
    payeeId: String,
): String? {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(payeeId, "payeeId")
    return database.budgetRecordDao().findLatestCategoryForPayee(budgetId, payeeId)
}

/** Payees of the most recent transactions, newest first, for putting likely choices at the top of a picker. */
suspend fun LocalBudgetRepository.recentPayeeIds(
    budgetId: String,
    limit: Int,
): List<String> {
    requireIdentifier(budgetId, "budgetId")
    require(limit in 1..50) { "limit must be between 1 and 50" }
    return database.budgetRecordDao().findRecentPayeeIds(budgetId, limit)
}

suspend fun LocalBudgetRepository.findTransactionPage(
    budgetId: String,
    cursorDate: String? = null,
    cursorId: String = "",
    limit: Int = 50,
): List<BudgetRecordEntity> {
    requireIdentifier(budgetId, "budgetId")
    require(limit in 1..200) { "limit must be between 1 and 200" }
    if (cursorDate != null) require(cursorDate.length <= 32) { "cursorDate is too long" }
    if (cursorId.isNotEmpty()) requireIdentifier(cursorId, "cursorId")
    return database.budgetRecordDao().findVisibleTransactionPage(
        budgetId = budgetId,
        cursorDate = cursorDate,
        cursorId = cursorId,
        hasCursor = cursorId.isNotEmpty(),
        limit = limit,
    )
}

suspend fun LocalBudgetRepository.countVisibleRecords(
    budgetId: String,
    recordType: String,
): Long {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(recordType, "recordType")
    return database.budgetRecordDao().countVisible(budgetId, recordType)
}

suspend fun LocalBudgetRepository.findRecord(
    budgetId: String,
    recordType: String,
    recordId: String,
): BudgetRecordEntity? = database.budgetRecordDao().find(budgetId, recordType, recordId)

suspend fun LocalBudgetRepository.readSyncState(budgetId: String): BudgetSyncStateEntity? = database.budgetSyncStateDao().find(budgetId)

/** Lists local snapshots with a completed sync checkpoint, newest successful sync first. */
suspend fun LocalBudgetRepository.findCachedBudgetIds(): List<String> =
    database.budgetSyncStateDao().findCachedBudgets().map {
        it.budgetId
    }

/** Checks for any financial cache or outbox rows, including a snapshot that is still being staged. */
suspend fun LocalBudgetRepository.hasAnyLocalBudgetData(): Boolean = database.budgetSyncStateDao().hasAnyBudgetData()

suspend fun LocalBudgetRepository.readBudgetDisplaySettings(budgetId: String): BudgetDisplaySettings? =
    database.budgetRecordDao().find(budgetId, "budget-settings", "display")?.remoteJson?.let { body ->
        runCatching {
            val settings = Json.parseToJsonElement(body).jsonObject
            BudgetDisplaySettings(
                currencyCode = settings.displayString("currencyCode") ?: return@runCatching null,
                currencySymbol = settings.displayString("currencySymbol") ?: return@runCatching null,
                currencyFractionDigits = settings.displayInt("currencyFractionDigits") ?: return@runCatching null,
                numberFormat = settings.displayString("numberFormat"),
                hideFraction = settings.displayBoolean("hideFraction"),
                currencySymbolPosition = settings.displayString("currencySymbolPosition"),
                currencySpaceBetweenAmountAndSymbol = settings.displayBoolean("currencySpaceBetweenAmountAndSymbol"),
                budgetType = settings.displayString("budgetType")?.takeIf { it == "tracking" } ?: "envelope",
            )
        }.getOrNull()
    }

/** Capture this before requesting a remote snapshot and pass it back to [applyFullSnapshot]. */
suspend fun LocalBudgetRepository.readSyncCheckpoint(budgetId: String): BudgetSyncCheckpoint? {
    requireIdentifier(budgetId, "budgetId")
    return database.budgetSyncStateDao().find(budgetId)?.let { BudgetSyncCheckpoint(it.generation, it.cursor) }
}

/** A successful local snapshot remains usable when its sync cursor needs to be rebuilt. */
suspend fun LocalBudgetRepository.hasCachedSnapshot(budgetId: String): Boolean {
    requireIdentifier(budgetId, "budgetId")
    val state = database.budgetSyncStateDao().find(budgetId) ?: return false
    return state.generation.isNotBlank() && state.lastSyncedAtEpochMs != null
}

suspend fun LocalBudgetRepository.readPendingWrites(budgetId: String): List<OutboxOperationEntity> {
    pruneTerminalWrites()
    return database.outboxDao().findPending(budgetId)
}

/** Removes old terminal history while preserving writes that still need a user or server decision. */
suspend fun LocalBudgetRepository.pruneTerminalWrites(nowEpochMs: Long = now()): Int =
    database.outboxDao().deleteExpiredTerminalWrites(nowEpochMs - TERMINAL_OUTBOX_RETENTION_MILLIS)

suspend fun LocalBudgetRepository.hasUnresolvedWrites(): Boolean = database.outboxDao().hasUnresolvedWrites()

suspend fun LocalBudgetRepository.budgetWrites(budgetId: String): List<OutboxOperationEntity> {
    requireIdentifier(budgetId, "budgetId")
    return database.outboxDao().findAllForBudget(budgetId)
}

suspend fun LocalBudgetRepository.findWrite(operationId: String): OutboxOperationEntity? {
    requireIdentifier(operationId, "operationId")
    return database.outboxDao().find(operationId)
}
