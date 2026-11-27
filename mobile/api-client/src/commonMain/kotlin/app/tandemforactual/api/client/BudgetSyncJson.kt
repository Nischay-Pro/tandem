package app.tandemforactual.api.client

import app.tandemforactual.data.actual.ActualSnapshot
import app.tandemforactual.data.actual.jsMathRound
import app.tandemforactual.data.store.BudgetRecordDisplayMetadata
import app.tandemforactual.data.store.BudgetSyncProtocolException
import app.tandemforactual.data.store.LocalRecordProjection
import app.tandemforactual.data.store.RemoteBudgetRecord
import app.tandemforactual.data.store.RemoteBudgetSnapshot
import app.tandemforactual.data.store.RemoteTransactionRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object BudgetSyncJson {
    internal fun projectDirectSnapshot(
        snapshot: ActualSnapshot,
        groupId: String,
        generation: String,
        cursor: String,
    ): RemoteBudgetSnapshot =
        RemoteBudgetSnapshot(
            budgetId = groupId,
            generation = generation,
            cursor = cursor,
            records = snapshot.records.map { it.body.toRemoteRecord(groupId, it.type, it.id, it.revision, generation) },
            displaySettings = snapshot.displaySettings,
        )

    internal fun directTransaction(record: app.tandemforactual.data.actual.ActualRecord): RemoteTransactionRecord =
        RemoteTransactionRecord(
            transactionId = record.id,
            revision = record.revision,
            bodyJson = record.body.toString(),
            projection = record.body.toProjection(),
            displayMetadata = record.body.toDisplayMetadata(),
        )

    fun parseJsonObject(value: String): JsonObject =
        try {
            Json.parseToJsonElement(value).jsonObject
        } catch (error: SerializationException) {
            throw BudgetSyncProtocolException("Outbox payload is not a JSON object", error)
        } catch (error: IllegalArgumentException) {
            throw BudgetSyncProtocolException("Outbox payload is not a JSON object", error)
        }

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.takeUnless { it == JsonNull }?.let { element ->
            val primitive = element.jsonPrimitive
            if (!primitive.isString) {
                throw BudgetSyncProtocolException("Response field $name must be a string")
            }
            primitive.content
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

    private fun JsonObject.toDisplayMetadata(): BudgetRecordDisplayMetadata =
        BudgetRecordDisplayMetadata(
            displayName = optionalString("name")?.takeIf { it.isNotBlank() },
            notes = optionalString("notes")?.takeIf { it.isNotBlank() },
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
            recordDate =
                if (recordType == "schedule") {
                    optionalString("next_date")
                } else {
                    firstString("date", "recordDate")
                },
            accountId = firstString("account", "accountId"),
            categoryId = firstString("category", "categoryId"),
            payeeId = firstString("payee", "payeeId"),
            amountMinor = if (recordType == "schedule") scheduleAmount() else firstLong("amount", "amountMinor"),
            budgetMonth = firstString("month", "budgetMonth"),
            displayMetadata = toDisplayMetadata(),
        )

    /** A schedule posts a fixed amount, or the rounded midpoint of an "is between" range. */
    private fun JsonObject.scheduleAmount(): Long? {
        val range = this["amount"] as? JsonObject ?: return firstLong("amount", "amountMinor")
        val low = range.firstLong("num1") ?: return null
        val high = range.firstLong("num2") ?: return null
        return jsMathRound((low + high) / 2.0).toLong()
    }

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
}
