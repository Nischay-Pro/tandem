package app.tandemforactual.api.client

import app.tandemforactual.data.store.BudgetChange
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.RemoteBudgetChangePage
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.RemoteBudgetSnapshot
import app.tandemforactual.data.store.RemoteOperationResult
import app.tandemforactual.data.store.RemoteOperationStatus
import app.tandemforactual.data.store.RemoteTransactionRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object BudgetSyncJson {
    fun parseSnapshot(
        payload: Map<String, JsonElement>,
        expectedBudgetId: String,
    ): RemoteBudgetSnapshot {
        val root = payload.toJsonObject()
        val budgetId = root.requiredString("budgetId")
        val generation = root.requiredString("generation")
        val cursor = root.requiredCursor("cursor")
        requireBudgetId(budgetId, expectedBudgetId)
        val records =
            root.requiredArray("records").map { element ->
                val record = element.requireObject("snapshot record")
                val type = record.requiredString("type")
                val id = record.requiredString("id")
                val revision = record.requiredString("revision")
                val body = record.requiredObject("body")
                body.toRemoteRecord(budgetId, type, id, revision, generation)
            }
        if (records.map { it.recordType to it.recordId }.toSet().size != records.size) {
            throw BudgetSyncProtocolException("Snapshot contains duplicate record identities")
        }
        return RemoteBudgetSnapshot(budgetId, generation, cursor, records, root.optionalDisplaySettings())
    }

    fun parseChanges(
        payload: Map<String, JsonElement>,
        expectedBudgetId: String,
    ): RemoteBudgetChangePage {
        val root = payload.toJsonObject()
        val budgetId = root.requiredString("budgetId")
        requireBudgetId(budgetId, expectedBudgetId)
        val generation = root.requiredString("generation")
        val currentCursor = root.requiredCursor("currentCursor")
        val throughCursor = root.requiredCursor("throughCursor")
        val nextCursor = root.requiredCursor("nextCursor")
        val hasMore = root.requiredBoolean("hasMore")
        val resetRequired = root.requiredBoolean("resetRequired")
        val changes =
            root.requiredArray("changes").map { element ->
                val change = element.requireObject("budget change")
                val recordType = change.requiredString("type")
                val recordId = change.requiredString("id")
                val operation = change.requiredString("operation")
                val revision = change.requiredString("revision")
                when (operation) {
                    "upsert" -> {
                        val body = change.requiredObject("body")
                        BudgetChange.Upsert(body.toRemoteRecord(budgetId, recordType, recordId, revision, generation))
                    }
                    "delete" -> {
                        if (change["body"] != null && change["body"] != JsonNull) {
                            throw BudgetSyncProtocolException("A delete change must have a null body")
                        }
                        BudgetChange.Delete(budgetId, recordType, recordId, revision)
                    }
                    else -> throw BudgetSyncProtocolException("Budget change has an unknown operation")
                }
            }
        return RemoteBudgetChangePage(
            budgetId,
            generation,
            currentCursor,
            throughCursor,
            nextCursor,
            hasMore,
            resetRequired,
            changes,
            root.optionalDisplaySettings(),
        )
    }

    fun parseTransaction(
        payload: Map<String, JsonElement>,
        expectedTransactionId: String,
    ): RemoteTransactionRecord {
        val root = payload.toJsonObject()
        val body = root.requiredObject("transaction")
        val id = body.requiredString("id")
        if (id != expectedTransactionId) {
            throw BudgetSyncProtocolException("Transaction response has a different ID")
        }
        return RemoteTransactionRecord(
            transactionId = id,
            revision = root.requiredString("revision"),
            bodyJson = body.toString(),
            projection = body.toProjection(),
        )
    }

    fun parseOperation(payload: Map<String, JsonElement>): RemoteOperationResult {
        val root = payload.toJsonObject()
        val operation = root.requiredObject("operation")
        val status =
            when (operation.requiredString("status")) {
                "queued" -> RemoteOperationStatus.QUEUED
                "applying" -> RemoteOperationStatus.APPLYING
                "committed" -> RemoteOperationStatus.COMMITTED
                "conflict" -> RemoteOperationStatus.CONFLICT
                "uncertain" -> RemoteOperationStatus.UNCERTAIN
                "rejected" -> RemoteOperationStatus.REJECTED
                else -> throw BudgetSyncProtocolException("Operation response has an unknown status")
            }
        return RemoteOperationResult(
            operationId = operation.requiredString("operationId"),
            budgetId = operation.requiredString("budgetId"),
            transactionId = operation.requiredString("transactionId"),
            status = status,
            errorCode = operation.optionalString("errorCode"),
        )
    }

    fun parseJsonObject(value: String): JsonObject =
        try {
            Json.parseToJsonElement(value).jsonObject
        } catch (error: SerializationException) {
            throw BudgetSyncProtocolException("Outbox payload is not a JSON object", error)
        } catch (error: IllegalArgumentException) {
            throw BudgetSyncProtocolException("Outbox payload is not a JSON object", error)
        }

    private fun Map<String, JsonElement>.toJsonObject(): JsonObject = JsonObject(this)

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name)?.takeIf { it.isNotBlank() }
            ?: throw BudgetSyncProtocolException("Response is missing a valid $name")

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.takeUnless { it == JsonNull }?.let { element ->
            val primitive = element.jsonPrimitive
            if (!primitive.isString) {
                throw BudgetSyncProtocolException("Response field $name must be a string")
            }
            primitive.content
        }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val primitive =
            try {
                this[name]?.jsonPrimitive
            } catch (error: IllegalArgumentException) {
                throw BudgetSyncProtocolException("Response field $name must be a boolean", error)
            }
        if (primitive == null || primitive.isString) {
            throw BudgetSyncProtocolException("Response is missing a valid $name")
        }
        return primitive.booleanOrNull
            ?: throw BudgetSyncProtocolException("Response is missing a valid $name")
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value =
            try {
                this[name]?.jsonPrimitive?.intOrNull
            } catch (error: IllegalArgumentException) {
                throw BudgetSyncProtocolException("Response field $name must be an integer", error)
            }
        return value ?: throw BudgetSyncProtocolException("Response is missing a valid $name")
    }

    private fun JsonObject.optionalDisplaySettings(): BudgetDisplaySettings? {
        val element = this["displaySettings"]?.takeUnless { it == JsonNull } ?: return null
        val settings = element.requireObject("displaySettings")
        val currencyCode = settings.requiredString("currencyCode")
        if (!currencyCode.matches(Regex("[A-Z]{3}"))) {
            throw BudgetSyncProtocolException("Response has an invalid currencyCode")
        }
        val currencySymbol = settings.requiredString("currencySymbol")
        val currencyFractionDigits = settings.requiredInt("currencyFractionDigits")
        if (currencyFractionDigits !in 0..6) {
            throw BudgetSyncProtocolException("Response has an invalid currencyFractionDigits")
        }
        val numberFormat = settings.optionalString("numberFormat")
        if (numberFormat != null && numberFormat !in setOf("comma-dot", "dot-comma", "space-comma", "apostrophe-dot", "comma-dot-in")) {
            throw BudgetSyncProtocolException("Response has an unsupported numberFormat")
        }
        val currencySymbolPosition = settings.optionalString("currencySymbolPosition")
        if (currencySymbolPosition != null && currencySymbolPosition !in setOf("before", "after")) {
            throw BudgetSyncProtocolException("Response has an unsupported currencySymbolPosition")
        }
        return BudgetDisplaySettings(
            currencyCode = currencyCode,
            currencySymbol = currencySymbol,
            currencyFractionDigits = currencyFractionDigits,
            numberFormat = numberFormat,
            hideFraction = settings.requiredBoolean("hideFraction"),
            currencySymbolPosition = currencySymbolPosition,
            currencySpaceBetweenAmountAndSymbol = settings.requiredBoolean("currencySpaceBetweenAmountAndSymbol"),
        )
    }

    private fun JsonObject.requiredCursor(name: String): String {
        val value = requiredString(name)
        if (!value.isDecimalCursor()) {
            throw BudgetSyncProtocolException("Response has an invalid $name")
        }
        return value
    }

    private fun JsonObject.requiredArray(name: String): JsonArray =
        try {
            this[name]?.jsonArray ?: throw BudgetSyncProtocolException("Response is missing a valid $name")
        } catch (error: IllegalArgumentException) {
            throw BudgetSyncProtocolException("Response field $name must be an array", error)
        }

    private fun JsonObject.requiredObject(name: String): JsonObject = this[name].requireObject(name)

    private fun JsonElement?.requireObject(name: String): JsonObject =
        try {
            this?.jsonObject ?: throw BudgetSyncProtocolException("Response is missing a valid $name")
        } catch (error: IllegalArgumentException) {
            throw BudgetSyncProtocolException("Response field $name must be an object", error)
        }

    private fun JsonObject.toProjection(): LocalRecordProjection =
        LocalRecordProjection(
            recordDate = firstString("date", "recordDate"),
            accountId = firstString("account", "accountId"),
            categoryId = firstString("category", "categoryId"),
            payeeId = firstString("payee", "payeeId"),
            amountMinor = firstLong("amount", "amountMinor"),
            budgetMonth = firstString("month", "budgetMonth"),
        )

    private fun JsonObject.toRemoteRecord(
        budgetId: String,
        recordType: String,
        recordId: String,
        revision: String,
        generation: String,
    ): RemoteBudgetRecord =
        RemoteBudgetRecord(
            budgetId = budgetId,
            recordType = recordType,
            recordId = recordId,
            revision = revision,
            bodyJson = toString(),
            generation = generation,
            recordDate = firstString("date", "recordDate"),
            accountId = firstString("account", "accountId"),
            categoryId = firstString("category", "categoryId"),
            payeeId = firstString("payee", "payeeId"),
            amountMinor = firstLong("amount", "amountMinor"),
            budgetMonth = firstString("month", "budgetMonth"),
        )

    private fun JsonObject.firstString(vararg names: String): String? = names.firstNotNullOfOrNull { name -> optionalString(name) }

    private fun JsonObject.firstLong(vararg names: String): Long? =
        names.firstNotNullOfOrNull { name ->
            val element = this[name]?.takeUnless { it == JsonNull } ?: return@firstNotNullOfOrNull null
            val primitive =
                try {
                    element.jsonPrimitive
                } catch (error: IllegalArgumentException) {
                    throw BudgetSyncProtocolException("Response field $name must be an integer", error)
                }
            if (primitive.isString) {
                throw BudgetSyncProtocolException("Response field $name must be an integer")
            }
            primitive.longOrNull ?: throw BudgetSyncProtocolException("Response field $name must be an integer")
        }

    private fun requireBudgetId(
        actual: String,
        expected: String,
    ) {
        if (actual != expected) {
            throw BudgetSyncProtocolException("Response belongs to a different budget")
        }
    }

    private fun String.isDecimalCursor(): Boolean =
        isNotEmpty() &&
            all { it in '0'..'9' } &&
            (this == "0" || first() != '0')
}
