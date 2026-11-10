package app.tandemforactual.data.store

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val LOCAL_CONFLICT_DETAILS_PREFIX = "tandem-conflict-v1:"

data class BudgetDisplaySettings(
    val currencyCode: String,
    val currencySymbol: String,
    val currencyFractionDigits: Int,
    val numberFormat: String?,
    val hideFraction: Boolean,
    val currencySymbolPosition: String?,
    val currencySpaceBetweenAmountAndSymbol: Boolean,
    val budgetType: String = "envelope",
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
    val conflictType: String? = null,
    val conflictingFields: List<String> = emptyList(),
)

data class LocalWriteConflictDetails(
    val errorCode: String?,
    val conflictType: String?,
    val conflictingFields: List<String>,
)

fun encodeLocalWriteConflictDetails(
    errorCode: String,
    conflictType: String?,
    conflictingFields: List<String>,
): String {
    if (conflictType == null && conflictingFields.isEmpty()) return errorCode
    val encoded =
        kotlinx.serialization.json.buildJsonObject {
            put("errorCode", errorCode)
            conflictType?.let { put("conflictType", it) }
            put("conflictingFields", JsonArray(conflictingFields.map(::JsonPrimitive)))
        }
    return LOCAL_CONFLICT_DETAILS_PREFIX + encoded
}

fun parseLocalWriteConflictDetails(value: String?): LocalWriteConflictDetails? {
    if (value?.startsWith(LOCAL_CONFLICT_DETAILS_PREFIX) != true) return null
    val root =
        runCatching {
            Json.parseToJsonElement(value.removePrefix(LOCAL_CONFLICT_DETAILS_PREFIX)).jsonObject
        }.getOrNull()
            ?: return null
    val fields =
        (root["conflictingFields"] as? JsonArray)
            ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
            .orEmpty()
    return LocalWriteConflictDetails(
        errorCode = root["errorCode"]?.jsonPrimitive?.contentOrNull,
        conflictType = root["conflictType"]?.jsonPrimitive?.contentOrNull,
        conflictingFields = fields,
    )
}

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
    ): RemoteBudgetSnapshotPage

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

    suspend fun readOperation(
        operationId: String,
        encryptionPassword: String?,
    ): RemoteOperationResult = readOperation(operationId)

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
