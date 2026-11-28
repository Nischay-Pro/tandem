package app.tandemforactual.data.store

import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal fun JsonObject.toProjection(): LocalRecordProjection =
    LocalRecordProjection(
        recordDate = firstString("date", "recordDate"),
        accountId = firstString("account", "accountId"),
        categoryId = firstString("category", "categoryId"),
        payeeId = firstString("payee", "payeeId"),
        amountMinor = firstLong("amount", "amountMinor"),
        budgetMonth = firstString("month", "budgetMonth"),
    )

private fun JsonObject.firstString(vararg names: String): String? =
    names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

private fun JsonObject.firstLong(vararg names: String): Long? =
    names.firstNotNullOfOrNull { name ->
        (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
    }

internal suspend fun LocalBudgetRepository.validateWriteBase(
    request: LocalWriteRequest,
    current: BudgetRecordEntity?,
) {
    if (request.action == LocalWriteAction.CREATE) {
        if (current != null && !current.remoteDeleted && current.remoteJson != null) {
            throw InvalidLocalWriteException("A record with this ID already exists")
        }
        return
    }
    if (current == null || current.remoteDeleted || current.remoteJson == null) {
        throw InvalidLocalWriteException("A remote base record is required for this write")
    }
    if (current.remoteRevision != request.baseRevision) {
        throw InvalidLocalWriteException("The cached transaction has changed")
    }
}

internal suspend fun LocalBudgetRepository.applyLocalOverlay(
    dao: app.tandemforactual.data.database.BudgetRecordDao,
    current: BudgetRecordEntity?,
    request: LocalWriteRequest,
) {
    val localDeleted = request.action == LocalWriteAction.DELETE
    val projection = request.optimisticProjection
    val metadata = request.optimisticJson?.let(::parseDisplayMetadata)
    val searchMetadata = request.optimisticJson?.takeIf { request.recordType == "transaction" }?.let(::parseTransactionSearchMetadata)
    val entity =
        current?.copy(
            localJson = request.optimisticJson,
            localDeleted = localDeleted,
            recordDate = if (projection == null) current.recordDate else projection.recordDate,
            accountId = if (projection == null) current.accountId else projection.accountId,
            categoryId = if (projection == null) current.categoryId else projection.categoryId,
            payeeId = if (projection == null) current.payeeId else projection.payeeId,
            amountMinor = if (projection == null) current.amountMinor else projection.amountMinor,
            budgetMonth = if (projection == null) current.budgetMonth else projection.budgetMonth,
            displayName = if (request.optimisticJson == null) current.displayName else metadata?.displayName,
            notes = if (request.optimisticJson == null) current.notes else metadata?.notes,
            cleared = if (request.optimisticJson == null) current.cleared else searchMetadata?.cleared,
            reconciled = if (request.optimisticJson == null) current.reconciled else searchMetadata?.reconciled,
            approved = if (request.optimisticJson == null) current.approved else searchMetadata?.approved,
            matched = if (request.optimisticJson == null) current.matched else searchMetadata?.matched,
            transfer = if (request.optimisticJson == null) current.transfer else searchMetadata?.transfer,
        ) ?: BudgetRecordEntity(
            budgetId = request.budgetId,
            recordType = request.recordType,
            recordId = request.recordId,
            remoteJson = null,
            remoteRevision = null,
            localJson = request.optimisticJson,
            localDeleted = localDeleted,
            remoteDeleted = false,
            syncGeneration = "",
            recordDate = projection?.recordDate,
            accountId = projection?.accountId,
            categoryId = projection?.categoryId,
            payeeId = projection?.payeeId,
            amountMinor = projection?.amountMinor,
            budgetMonth = projection?.budgetMonth,
            displayName = metadata?.displayName,
            notes = metadata?.notes,
            cleared = searchMetadata?.cleared,
            reconciled = searchMetadata?.reconciled,
            approved = searchMetadata?.approved,
            matched = searchMetadata?.matched,
            transfer = searchMetadata?.transfer,
        )
    dao.put(entity)
}

internal fun LocalBudgetRepository.validateWriteRequest(request: LocalWriteRequest) {
    requireIdentifier(request.operationId, "operationId")
    requireIdentifier(request.idempotencyKey, "idempotencyKey")
    requireIdentifier(request.budgetId, "budgetId")
    requireIdentifier(request.recordType, "recordType")
    requireIdentifier(request.recordId, "recordId")
    validateJsonObject(request.patchJson, "patchJson")
    when (request.action) {
        LocalWriteAction.CREATE -> {
            if (request.baseRevision != null || request.baseSnapshotJson != null) {
                throw InvalidLocalWriteException("Creates cannot include a remote base")
            }
            validateJsonObject(request.optimisticJson, "optimisticJson")
            requireOptimisticProjection(request)
        }

        LocalWriteAction.UPDATE -> {
            requireIdentifier(request.baseRevision, "baseRevision")
            validateJsonObject(request.baseSnapshotJson, "baseSnapshotJson")
            validateJsonObject(request.optimisticJson, "optimisticJson")
            requireOptimisticProjection(request)
        }

        LocalWriteAction.DELETE -> {
            requireIdentifier(request.baseRevision, "baseRevision")
            validateJsonObject(request.baseSnapshotJson, "baseSnapshotJson")
            if (request.optimisticJson != null) {
                throw InvalidLocalWriteException("Deletes cannot include an optimistic record")
            }
            if (request.optimisticProjection != null) {
                throw InvalidLocalWriteException("Deletes cannot include an optimistic projection")
            }
        }
    }
}

private fun LocalBudgetRepository.requireOptimisticProjection(request: LocalWriteRequest) {
    if (request.optimisticProjection == null) {
        throw InvalidLocalWriteException("Creates and updates require an optimistic index projection")
    }
}

internal fun LocalBudgetRepository.validateJsonObject(
    value: String?,
    name: String,
): JsonObject {
    if (value == null) {
        throw InvalidLocalWriteException("$name is required")
    }
    return try {
        Json.parseToJsonElement(value).jsonObject
    } catch (error: SerializationException) {
        throw InvalidLocalWriteException("$name must contain a JSON object", error)
    } catch (error: IllegalArgumentException) {
        throw InvalidLocalWriteException("$name must contain a JSON object", error)
    }
}

internal fun LocalBudgetRepository.requireIdentifier(
    value: String?,
    name: String,
) {
    if (value.isNullOrBlank() || value != value.trim()) {
        throw InvalidLocalWriteException("$name must be a non-empty, trimmed value")
    }
}

internal fun LocalBudgetRepository.matchesRequest(
    operation: OutboxOperationEntity,
    request: LocalWriteRequest,
): Boolean =
    operation.operationId == request.operationId &&
        operation.budgetId == request.budgetId &&
        operation.recordType == request.recordType &&
        operation.recordId == request.recordId &&
        operation.action == request.action.name &&
        operation.baseRevision == request.baseRevision &&
        operation.baseSnapshotJson == request.baseSnapshotJson &&
        operation.patchJson == request.patchJson &&
        operation.optimisticJson == request.optimisticJson &&
        operation.optimisticRecordDate == request.optimisticProjection?.recordDate &&
        operation.optimisticAccountId == request.optimisticProjection?.accountId &&
        operation.optimisticCategoryId == request.optimisticProjection?.categoryId &&
        operation.optimisticPayeeId == request.optimisticProjection?.payeeId &&
        operation.optimisticAmountMinor == request.optimisticProjection?.amountMinor &&
        operation.optimisticBudgetMonth == request.optimisticProjection?.budgetMonth

internal fun LocalWriteRequest.toEntity(): OutboxOperationEntity =
    OutboxOperationEntity(
        operationId = operationId,
        idempotencyKey = idempotencyKey,
        budgetId = budgetId,
        recordType = recordType,
        recordId = recordId,
        action = action.name,
        baseRevision = baseRevision,
        baseSnapshotJson = baseSnapshotJson,
        patchJson = patchJson,
        optimisticJson = optimisticJson,
        optimisticRecordDate = optimisticProjection?.recordDate,
        optimisticAccountId = optimisticProjection?.accountId,
        optimisticCategoryId = optimisticProjection?.categoryId,
        optimisticPayeeId = optimisticProjection?.payeeId,
        optimisticAmountMinor = optimisticProjection?.amountMinor,
        optimisticBudgetMonth = optimisticProjection?.budgetMonth,
        status = LocalWriteStatus.QUEUED.name.lowercase(),
        attemptCount = 0,
        serverOperationId = null,
        lastErrorCode = null,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = createdAtEpochMs,
    )
