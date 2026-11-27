package app.tandemforactual.ui.core.state

import app.tandemforactual.data.database.OutboxOperationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class LiveScheduleSummary(
    val id: String,
    val name: String,
    val nextDate: String,
    val dateValueJson: String,
    val accountId: String?,
    val accountName: String?,
    val payeeId: String?,
    val amountMinor: Long?,
    val amountRangeMinor: Pair<Long, Long>?,
    val amountOp: String,
    val canSkip: Boolean,
    val ruleId: String? = null,
    val postsTransaction: Boolean = false,
)

/** A schedule created on this device that Actual hasn't confirmed yet, shown from its queued fields. */
internal fun queuedScheduleSummary(
    action: OutboxOperationEntity,
    payees: Map<String, String>,
    accounts: Map<String, String>,
): LiveScheduleSummary? {
    val payload = runCatching { Json.parseToJsonElement(action.patchJson).jsonObject }.getOrNull() ?: return null
    val fields = payload["fields"] as? JsonObject ?: return null
    val date: JsonElement = fields["date"] ?: return null
    val start = (date as? JsonObject)?.stringValue("start") ?: (date as? JsonPrimitive)?.contentOrNull ?: return null
    val range =
        (fields["amountRangeMinor"] as? JsonObject)?.let { value ->
            val low = value.longValue("num1")
            val high = value.longValue("num2")
            if (low != null && high != null) low to high else null
        }
    val payeeId = fields.stringValue("payeeId")
    val accountId = fields.stringValue("accountId")
    return LiveScheduleSummary(
        id = action.recordId,
        name = fields.stringValue("name") ?: payeeId?.let(payees::get) ?: "Scheduled transaction",
        nextDate = start,
        dateValueJson = date.toString(),
        accountId = accountId,
        accountName = accountId?.let(accounts::get),
        payeeId = payeeId,
        amountMinor = if (range == null) fields.longValue("amountMinor") else null,
        amountRangeMinor = range,
        amountOp = fields.stringValue("amountOp") ?: "is",
        canSkip = false,
        postsTransaction = fields.booleanValue("postsTransaction") == true,
    )
}
