package app.tandemforactual.data.store

import androidx.room3.withWriteTransaction
import app.tandemforactual.data.database.BudgetRecordEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

suspend fun LocalBudgetRepository.saveLocalPayee(
    budgetId: String,
    payeeId: String,
    name: String,
) {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(payeeId, "payeeId")
    require(name.isNotBlank())
    database.withWriteTransaction {
        val dao = database.budgetRecordDao()
        val localJson =
            buildJsonObject {
                put("id", payeeId)
                put("name", name)
            }.toString()
        val existing = dao.find(budgetId, "payee", payeeId)
        if (existing != null) {
            dao.put(existing.copy(localJson = localJson, localDeleted = false, remoteDeleted = false, displayName = name))
        } else {
            dao.put(
                BudgetRecordEntity(
                    budgetId = budgetId,
                    recordType = "payee",
                    recordId = payeeId,
                    remoteJson = null,
                    remoteRevision = null,
                    localJson = localJson,
                    localDeleted = false,
                    remoteDeleted = false,
                    syncGeneration = "local-payee",
                    recordDate = null,
                    accountId = null,
                    categoryId = null,
                    payeeId = null,
                    amountMinor = null,
                    budgetMonth = null,
                    displayName = name,
                ),
            )
        }
    }
}

suspend fun LocalBudgetRepository.remapPendingPayeeReferences(
    budgetId: String,
    temporaryPayeeId: String,
    actualPayeeId: String,
): Int {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(temporaryPayeeId, "temporaryPayeeId")
    requireIdentifier(actualPayeeId, "actualPayeeId")
    return database.withWriteTransaction {
        val outbox = database.outboxDao()
        val records = database.budgetRecordDao()
        var updatedCount = 0
        outbox
            .findPending(budgetId)
            .filter { it.recordType == "transaction" && it.status == LocalWriteStatus.QUEUED.name.lowercase() }
            .forEach { operation ->
                val patch = rewritePayeeReference(operation.patchJson, temporaryPayeeId, actualPayeeId) ?: return@forEach
                val optimistic = operation.optimisticJson?.let { rewritePayeeReference(it, temporaryPayeeId, actualPayeeId) }
                outbox.put(
                    operation.copy(
                        patchJson = patch,
                        optimisticJson = optimistic,
                        optimisticPayeeId =
                            if (operation.optimisticPayeeId == temporaryPayeeId) actualPayeeId else operation.optimisticPayeeId,
                        updatedAtEpochMs = now(),
                    ),
                )
                val localRecord = records.find(budgetId, "transaction", operation.recordId)
                if (localRecord?.localJson != null && optimistic != null) {
                    records.put(localRecord.copy(localJson = optimistic, payeeId = actualPayeeId))
                }
                updatedCount += 1
            }
        records.deleteLocalOverlay(budgetId, "payee", temporaryPayeeId)
        updatedCount
    }
}

suspend fun LocalBudgetRepository.remapPendingAccountReferences(
    budgetId: String,
    temporaryAccountId: String,
    actualAccountId: String,
): Int {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(temporaryAccountId, "temporaryAccountId")
    requireIdentifier(actualAccountId, "actualAccountId")
    return database.withWriteTransaction {
        val outbox = database.outboxDao()
        val records = database.budgetRecordDao()
        var updatedCount = 0
        outbox
            .findPending(budgetId)
            .filter { it.recordType == "transaction" && it.status == LocalWriteStatus.QUEUED.name.lowercase() }
            .forEach { operation ->
                val patch = rewriteReference(operation.patchJson, "account", temporaryAccountId, actualAccountId) ?: return@forEach
                val optimistic = operation.optimisticJson?.let { rewriteReference(it, "account", temporaryAccountId, actualAccountId) }
                outbox.put(
                    operation.copy(
                        patchJson = patch,
                        optimisticJson = optimistic,
                        optimisticAccountId =
                            if (operation.optimisticAccountId == temporaryAccountId) actualAccountId else operation.optimisticAccountId,
                        updatedAtEpochMs = now(),
                    ),
                )
                val localRecord = records.find(budgetId, "transaction", operation.recordId)
                if (localRecord?.localJson != null && optimistic != null) {
                    records.put(localRecord.copy(localJson = optimistic, accountId = actualAccountId))
                }
                updatedCount += 1
            }
        records.deleteLocalOverlay(budgetId, "account", temporaryAccountId)
        updatedCount
    }
}

/** Replaces a temporary category id in unsent transaction edits after the category is created remotely. */
suspend fun LocalBudgetRepository.remapPendingCategoryReferences(
    budgetId: String,
    temporaryCategoryId: String,
    actualCategoryId: String,
): Int {
    requireIdentifier(budgetId, "budgetId")
    requireIdentifier(temporaryCategoryId, "temporaryCategoryId")
    requireIdentifier(actualCategoryId, "actualCategoryId")
    return database.withWriteTransaction {
        val outbox = database.outboxDao()
        val records = database.budgetRecordDao()
        var updatedCount = 0
        outbox
            .findPending(budgetId)
            .filter { it.recordType == "transaction" && it.status == LocalWriteStatus.QUEUED.name.lowercase() }
            .forEach { operation ->
                val patch = rewriteCategoryReference(operation.patchJson, temporaryCategoryId, actualCategoryId) ?: return@forEach
                val optimistic = operation.optimisticJson?.let { rewriteCategoryReference(it, temporaryCategoryId, actualCategoryId) }
                outbox.put(
                    operation.copy(
                        patchJson = patch,
                        optimisticJson = optimistic,
                        optimisticCategoryId =
                            if (operation.optimisticCategoryId == temporaryCategoryId) {
                                actualCategoryId
                            } else {
                                operation.optimisticCategoryId
                            },
                        updatedAtEpochMs = now(),
                    ),
                )
                val localRecord = records.find(budgetId, "transaction", operation.recordId)
                if (localRecord?.localJson != null && optimistic != null) {
                    records.put(localRecord.copy(localJson = optimistic, categoryId = actualCategoryId))
                }
                updatedCount += 1
            }
        updatedCount
    }
}

private fun LocalBudgetRepository.rewriteCategoryReference(
    bodyJson: String,
    temporaryCategoryId: String,
    actualCategoryId: String,
): String? =
    runCatching {
        val body = Json.parseToJsonElement(bodyJson).jsonObject
        if (body["category"]?.let { (it as? JsonPrimitive)?.contentOrNull } != temporaryCategoryId) {
            return@runCatching null
        }
        JsonObject(body + ("category" to JsonPrimitive(actualCategoryId))).toString()
    }.getOrNull()

private fun LocalBudgetRepository.rewritePayeeReference(
    bodyJson: String,
    temporaryPayeeId: String,
    actualPayeeId: String,
): String? =
    runCatching {
        val body = Json.parseToJsonElement(bodyJson).jsonObject
        if (body["payee"]?.let { (it as? JsonPrimitive)?.contentOrNull } != temporaryPayeeId) {
            return@runCatching null
        }
        JsonObject(body + ("payee" to JsonPrimitive(actualPayeeId))).toString()
    }.getOrNull()

private fun LocalBudgetRepository.rewriteReference(
    bodyJson: String,
    field: String,
    temporaryId: String,
    actualId: String,
): String? =
    runCatching {
        val body = Json.parseToJsonElement(bodyJson).jsonObject
        if ((body[field] as? JsonPrimitive)?.contentOrNull != temporaryId) return@runCatching null
        JsonObject(body + (field to JsonPrimitive(actualId))).toString()
    }.getOrNull()
