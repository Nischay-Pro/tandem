package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `schedule/create` with the conditions Actual's schedule form builds (`updateScheduleConditions`): payee, account, an
 * approximate date and the amount. [fields] uses the names [ActualScheduleEdits.edit] takes, plus `linkTransactionIds`
 * for the transactions the new schedule should claim. Creating a schedule that already exists does nothing, so a resent
 * create can't add a second one.
 */
fun ActualScheduleEdits.create(
    id: String,
    fields: JsonObject,
) = writer.batch {
    if (db.first("SELECT id FROM schedules WHERE id = ?", id) != null) return@batch
    validate(fields)
    val date = fields["date"] ?: fail("Choose a date for this schedule")
    val amount = fields["amountMinor"] ?: fields["amountRangeMinor"] ?: fail("Enter an amount for this schedule")
    val accountId = fields.value("accountId") as String?
    val payeeId = fields.value("payeeId") as String?
    val categoryId = fields.value("categoryId") as String?
    requireEditTargets(accountId, payeeId, categoryId)
    val conditions =
        listOfNotNull(
            scheduleCondition("is", "payee", payeeId?.let { JsonPrimitive(it) } ?: JsonNull),
            accountId?.let { scheduleCondition("is", "account", JsonPrimitive(it)) },
            scheduleCondition("isapprox", "date", date),
            scheduleCondition(fields.value("amountOp") as String, "amount", amount),
        )
    insertSchedule(id, fields, conditions, listOfNotNull(categoryId?.let { setAction("category", it) }))
    linkTransactions(id, fields["linkTransactionIds"])
}

/** Inserts the schedule's rule, next date and row in the order `createSchedule` writes them. */
internal fun ActualScheduleEdits.insertSchedule(
    id: String,
    fields: JsonObject,
    conditions: List<JsonObject>,
    extraActions: List<JsonObject>,
) {
    val name = (fields.value("name") as String?)?.trim()?.ifEmpty { null }
    if (name != null && name.length > 100) fail("Schedule names must be between 1 and 100 characters")
    if (name != null && schedules().nameTaken(id, name)) fail("A schedule with this name already exists")
    val dateValue = conditions.first { it.value("field") == "date" }["value"]
    val next = ActualRecurrence.nextDateFrom(dateValue, Day.parse(today()))?.let { ActualMonths.dateInt(it.toString()) }
    val actions = listOf(linkAction(id)) + extraActions
    val ruleId =
        writer.insert(
            "rules",
            linkedMapOf(
                "stage" to null,
                "conditions_op" to "and",
                "conditions" to stringifyJson(JsonArray(conditions.map { internal(it) })),
                "actions" to stringifyJson(JsonArray(actions.map { internal(it) })),
            ),
        )
    val now = writer.nowMillis()
    writer.insert(
        "schedules_next_date",
        linkedMapOf(
            "schedule_id" to id,
            "local_next_date" to next,
            "local_next_date_ts" to now,
            "base_next_date" to next,
            "base_next_date_ts" to now,
        ),
    )
    val posts = fields.value("postsTransaction") as Boolean?
    writer.insert(
        "schedules",
        linkedMapOf<String, Any?>("id" to id).apply {
            name?.let { put("name", it) }
            posts?.let { put("posts_transaction", if (it) 1L else 0L) }
            put("rule", ruleId)
        },
    )
    if (posts == true) allowAutoPostingToRunAgainToday()
}

/** `transactions-batch-update` setting `schedule` on each live transaction in [ids]. */
internal fun ActualScheduleEdits.linkTransactions(
    scheduleId: String,
    ids: JsonElement?,
) {
    val list = (ids as? JsonArray)?.map { scalar(it) as? String ?: fail("Choose transactions in this budget") }.orEmpty()
    list.distinct().forEach { transactionId ->
        db.first("SELECT id FROM transactions WHERE id = ? AND tombstone = 0", transactionId)
            ?: fail("Choose transactions in this budget")
        writer.update("transactions", transactionId, mapOf("schedule" to scheduleId))
    }
}

internal fun scheduleCondition(
    op: String,
    field: String,
    value: JsonElement,
): JsonObject =
    JsonObject(
        linkedMapOf(
            "op" to JsonPrimitive(op),
            "field" to JsonPrimitive(field),
            "value" to value,
        ),
    )

private fun linkAction(scheduleId: String): JsonObject =
    JsonObject(linkedMapOf("op" to JsonPrimitive("link-schedule"), "value" to JsonPrimitive(scheduleId)))
