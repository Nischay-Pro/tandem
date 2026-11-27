package app.tandemforactual.ui.core.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.tandemforactual.api.client.StatementImportItem
import app.tandemforactual.data.store.LocalRecordProjection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun parseQueuePayload(encoded: String?): JsonObject? =
    encoded?.let {
        runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull()
    }

internal fun QueuedBudgetReorder.requirePayload(): JsonObject = parseQueuePayload(payloadJson) ?: JsonObject(emptyMap())

internal fun JsonObject.requiredString(key: String): String = stringValue(key) ?: ""

internal fun JsonObject.requiredLong(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: -1L

internal fun JsonObject.requiredBoolean(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

internal fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true

internal fun JsonObject.optionalString(key: String): String? = this[key]?.takeUnless { it == JsonNull }?.jsonPrimitive?.contentOrNull

internal fun JsonObject.requiredAssignments(): List<Pair<String, Long>> =
    (this["assignments"] as? JsonArray)
        ?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val categoryId = item.stringValue("categoryId") ?: return@mapNotNull null
            val amountMinor = item["amountMinor"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            categoryId to amountMinor
        }.orEmpty()

internal fun JsonObject.goalTemplateCategoryIds(): List<String>? =
    (this["categoryIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

internal fun JsonObject.requiredStatementItems(): List<StatementImportItem> =
    (this["transactions"] as? JsonArray)
        ?.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val date = item.stringValue("date") ?: return@mapNotNull null
            val amount = item["amountMinor"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val payeeName = item.stringValue("payeeName") ?: return@mapNotNull null
            val importedId = item.stringValue("importedId") ?: return@mapNotNull null
            StatementImportItem(date, amount, payeeName, item.optionalString("notes"), importedId, item.optionalString("matchId"))
        }.orEmpty()

internal fun rewriteAccountIds(
    element: JsonElement,
    temporaryId: String,
    actualId: String,
): JsonElement =
    when (element) {
        is JsonObject ->
            JsonObject(
                element.mapValues { (key, value) ->
                    if (key in ACCOUNT_REFERENCE_FIELDS && (value as? JsonPrimitive)?.contentOrNull == temporaryId) {
                        JsonPrimitive(actualId)
                    } else {
                        rewriteAccountIds(value, temporaryId, actualId)
                    }
                },
            )
        is JsonArray -> JsonArray(element.map { rewriteAccountIds(it, temporaryId, actualId) })
        else -> element
    }

internal val ACCOUNT_REFERENCE_FIELDS = setOf("account", "accountId", "targetAccountId", "transferAccountId")

internal fun BudgetReorderQueueState.enqueueIfMissing(move: QueuedBudgetReorder): BudgetReorderQueueState =
    if (moves.any { it.operationId == move.operationId }) this else enqueue(move)

internal fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.longValue(name: String): Long? =
    (this[name] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.toProjection(): LocalRecordProjection =
    LocalRecordProjection(
        recordDate = stringValue("date"),
        accountId = stringValue("account"),
        categoryId = stringValue("category"),
        payeeId = stringValue("payee"),
        amountMinor = longValue("amount"),
        budgetMonth = stringValue("month"),
    )
