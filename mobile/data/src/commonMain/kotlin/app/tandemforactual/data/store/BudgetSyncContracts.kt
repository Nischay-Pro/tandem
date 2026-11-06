package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity

data class BudgetDisplaySettings(
    val currencyCode: String,
    val currencySymbol: String,
    val currencyFractionDigits: Int,
    val numberFormat: String?,
    val hideFraction: Boolean,
    val currencySymbolPosition: String?,
    val currencySpaceBetweenAmountAndSymbol: Boolean,
)

data class RemoteBudgetSnapshot(
    val budgetId: String,
    val generation: String,
    val cursor: String,
    val records: List<RemoteBudgetRecord>,
    val displaySettings: BudgetDisplaySettings? = null,
)

data class RemoteBudgetSnapshotPage(
    val budgetId: String,
    val generation: String,
    val cursor: String,
    val records: List<RemoteBudgetRecord>,
    val hasMore: Boolean,
    val resetRequired: Boolean,
    val nextAfterType: String?,
    val nextAfterId: String?,
    val displaySettings: BudgetDisplaySettings? = null,
)

data class RemoteBudgetChangePage(
    val budgetId: String,
    val generation: String,
    val currentCursor: String,
    val throughCursor: String,
    val nextCursor: String,
    val hasMore: Boolean,
    val resetRequired: Boolean,
    val changes: List<BudgetChange>,
    val displaySettings: BudgetDisplaySettings? = null,
)

data class RemoteTransactionRecord(
    val transactionId: String,
    val revision: String,
    val bodyJson: String,
    val projection: LocalRecordProjection,
    val displayMetadata: BudgetRecordDisplayMetadata? = null,
)

data class BudgetRecordDisplayMetadata(
    val displayName: String?,
    val notes: String?,
)

enum class RemoteOperationStatus {
    QUEUED,
    APPLYING,
    COMMITTED,
    CONFLICT,
    UNCERTAIN,
    REJECTED,
}

data class RemoteOperationResult(
    val operationId: String,
    val budgetId: String,
    val transactionId: String,
    val status: RemoteOperationStatus,
    val errorCode: String?,
)

interface BudgetSyncRemote {
    suspend fun readSnapshot(
        budgetId: String,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshot

    suspend fun readSnapshotPage(
        budgetId: String,
        generation: String?,
        cursor: String?,
        afterType: String?,
        afterId: String?,
        limit: Int,
        encryptionPassword: String?,
    ): RemoteBudgetSnapshotPage {
        val snapshot = readSnapshot(budgetId, encryptionPassword)
        if (generation != null &&
            cursor != null &&
            (generation != snapshot.generation || cursor != snapshot.cursor)
        ) {
            return RemoteBudgetSnapshotPage(
                budgetId = snapshot.budgetId,
                generation = snapshot.generation,
                cursor = snapshot.cursor,
                records = emptyList(),
                hasMore = false,
                resetRequired = true,
                nextAfterType = null,
                nextAfterId = null,
            )
        }
        val records =
            snapshot.records
                .filter { record ->
                    afterType == null ||
                        record.recordType > afterType ||
                        (record.recordType == afterType && record.recordId > afterId.orEmpty())
                }.take(limit + 1)
        val hasMore = records.size > limit
        val page = if (hasMore) records.dropLast(1) else records
        val last = page.lastOrNull()
        return RemoteBudgetSnapshotPage(
            budgetId = snapshot.budgetId,
            generation = snapshot.generation,
            cursor = snapshot.cursor,
            records = page,
            hasMore = hasMore,
            resetRequired = false,
            nextAfterType = last?.recordType?.takeIf { hasMore },
            nextAfterId = last?.recordId?.takeIf { hasMore },
            displaySettings = snapshot.displaySettings.takeIf { afterType == null },
        )
    }

    suspend fun readChanges(
        budgetId: String,
        generation: String,
        cursor: String,
        throughCursor: String?,
        encryptionPassword: String?,
    ): RemoteBudgetChangePage

    suspend fun submitOperation(
        operation: OutboxOperationEntity,
        encryptionPassword: String?,
    ): RemoteOperationResult

    suspend fun readOperation(operationId: String): RemoteOperationResult

    /** Returns null only for an authoritative HTTP 404 response. */
    suspend fun readTransaction(
        budgetId: String,
        transactionId: String,
        encryptionPassword: String?,
    ): RemoteTransactionRecord?
}

class BudgetSyncRemoteException(
    val errorCode: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(errorCode, cause) {
    val isDefinitiveClientError: Boolean
        get() = httpStatus != null && httpStatus in setOf(400, 409, 422)

    val isRetryableBeforeSubmission: Boolean
        get() = httpStatus != null && httpStatus in setOf(401, 403, 404)
}

class BudgetSyncProtocolException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
