package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private const val MAX_SCHEDULE_AMOUNT = 1_000_000_000L
private const val SCHEDULE_CHANGED = "This schedule changed in Actual. Refresh it before trying again."

/** Schedule edits with the gateway's checks and Actual's `schedules/app.ts` and `api/schedule-update` behaviour. */
class ActualScheduleEdits(
    private val writer: ActualWriter,
    private val today: () -> String,
) {
    private val db get() = writer.db

    private fun fail(
        message: String,
        code: String? = null,
    ): Nothing = throw ActualEditException(message, code)

    private fun changed(): Nothing = fail(SCHEDULE_CHANGED, "schedule_changed")

    private fun schedules(): List<JsonObject> =
        ActualBudgetReader(db)
            .read(today())
            .records
            .filter { it.type == "schedule" }
            .map { it.body }

    private fun active(id: String): JsonObject? = schedules().firstOrNull { it.value("id") == id && it.value("completed") != true }

    private fun nameTaken(
        id: String,
        name: String,
    ) = schedules().any { it.value("id") != id && (it.value("name") as String?)?.trim() == name }

    fun nextDate(id: String): String? = schedules().firstOrNull { it.value("id") == id }?.value("next_date") as String?

    fun rename(
        id: String,
        name: String,
    ) = writer.batch {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 100) fail("Schedule names must be between 1 and 100 characters")
        active(id) ?: fail("Choose an active schedule in this budget")
        if (nameTaken(id, clean)) fail("A schedule with this name already exists")
        writer.update("schedules", id, mapOf("name" to clean))
    }

    /** [fields] uses the gateway's names: name, accountId, payeeId, amountMinor, amountRangeMinor, amountOp and date. */
    fun edit(
        id: String,
        fields: JsonObject,
    ) = writer.batch {
        validate(fields)
        val name = fields.value("name")?.let { (it as String).trim() }
        if (fields["name"] != null &&
            (name.isNullOrEmpty() || name.length > 100)
        ) {
            fail("Schedule names must be between 1 and 100 characters")
        }
        val schedule = active(id) ?: fail("Choose an active schedule in this budget")
        if (!name.isNullOrEmpty() && nameTaken(id, name)) fail("A schedule with this name already exists")
        val accountId = fields.value("accountId") as String?
        if (accountId != null) {
            val account = db.first("SELECT closed FROM accounts WHERE id = ? AND tombstone = 0", accountId)
            if (account == null || truthy(account["closed"])) fail("Choose an open account in this budget")
        }
        val payeeId = fields.value("payeeId") as String?
        if (payeeId != null && ActualBudgetReader(db).read(today()).records.none { it.type == "payee" && it.id == payeeId }) {
            fail("Choose a payee in this budget")
        }
        val ruleId = schedule.value("rule") as String
        val rule =
            ActualRules.load(db.query("SELECT * FROM rules WHERE id = ? AND tombstone = 0", ruleId), emptyMap()).firstOrNull()
                ?: fail("No rule found for schedule")
        val old = rule.getValue("conditions").jsonArray.map { it.jsonObject }
        val conditions = old.toMutableList()

        fun indexOf(match: (JsonObject) -> Boolean) = conditions.indexOfFirst(match)

        fun upsert(
            field: String,
            value: String,
        ) {
            val index = indexOf { it.value("op") == "is" && it.value("field") == field }
            val replacement =
                JsonObject(
                    linkedMapOf(
                        "field" to JsonPrimitive(field),
                        "op" to JsonPrimitive("is"),
                        "value" to JsonPrimitive(value),
                    ),
                )
            if (index >= 0) conditions[index] = replacement else conditions += replacement
        }
        if (payeeId != null) upsert("payee", payeeId)
        if (accountId != null) upsert("account", accountId)
        val amount = fields["amountMinor"] ?: fields["amountRangeMinor"]
        val amountOp = fields.value("amountOp") as String?
        if (amount != null || amountOp != null) {
            val index = indexOf { it.value("field") == "amount" && it.value("op") in setOf("is", "isapprox", "isbetween") }
            if (index < 0) fail("Ammount can not be found. There is a bug here")
            var condition = conditions[index]
            if (amountOp != null) condition = JsonObject(condition + ("op" to JsonPrimitive(amountOp)))
            if (amount != null) condition = JsonObject(condition + ("value" to amount))
            conditions[index] = condition
        }
        val date = fields["date"]
        if (date != null) {
            val index = indexOf { it.value("field") == "date" && it.value("op") in setOf("is", "isapprox") }
            if (index < 0) fail("Date can not be found. Schedules can not be created without a date there is a bug here")
            conditions[index] = JsonObject(conditions[index] + ("value" to date))
        }
        val actions = syncAmountActions(conditions, rule.getValue("actions").jsonArray)
        writer.update(
            "rules",
            ruleId,
            linkedMapOf(
                "conditions" to stringifyJson(JsonArray(conditions.map(::internal))),
                "actions" to stringifyJson(JsonArray(actions.map { internal(it.jsonObject) })),
            ),
        )

        fun valueOf(
            list: List<JsonObject>,
            field: String,
        ) = list.firstOrNull { it.value("field") == field }?.let { it["value"] }
        val reset =
            accountId != null ||
                date != null ||
                valueOf(old, "account") != valueOf(conditions, "account") ||
                valueOf(old, "date") != valueOf(conditions, "date")
        if (reset) setNextDate(id, conditions, advance = false)
        writer.update(
            "schedules",
            id,
            linkedMapOf(
                "posts_transaction" to (
                    if (schedule.value("posts_transaction") ==
                        true
                    ) {
                        1L
                    } else {
                        0L
                    }
                ),
                "name" to (name ?: schedule.value("name")),
            ),
        )
    }

    fun skip(
        id: String,
        expectedNextDate: String,
    ) = writer.batch {
        val schedule = active(id) ?: changed()
        if (schedule["date"] !is JsonObject ||
            (schedule["date"] as JsonObject)["frequency"] == null
        ) {
            fail("A one-time schedule has no next date to skip")
        }
        if (schedule.value("next_date") != expectedNextDate) changed()
        val rule =
            ActualRules
                .load(
                    db.query("SELECT * FROM rules WHERE id = ? AND tombstone = 0", schedule.value("rule")),
                    emptyMap(),
                ).single()
        setNextDate(id, rule.getValue("conditions").jsonArray.map { it.jsonObject }, advance = true)
    }

    fun delete(id: String) =
        writer.batch {
            val schedule = schedules().firstOrNull { it.value("id") == id } ?: fail("Schedule does not exist in this budget")
            writer.delete("rules", schedule.value("rule") as String)
            writer.delete("schedules", id)
        }

    /** `setNextDate`: recomputes the base date after an edit, or the local date when an occurrence is skipped. */
    private fun setNextDate(
        id: String,
        conditions: List<JsonObject>,
        advance: Boolean,
    ) {
        val dateCondition = conditions.firstOrNull { it.value("field") == "date" && it.value("op") in setOf("is", "isapprox") } ?: return
        val current = db.first("SELECT next_date FROM v_schedules WHERE id = ?", id)?.get("next_date") as Number?
        val currentDay = current?.let { Day.parse(ActualMonths.dateString(it.toLong())) }
        val next =
            if (advance) {
                ActualRecurrence.nextDateAfter(dateCondition["value"], currentDay ?: Day.parse(today()))
            } else {
                ActualRecurrence.nextDateFrom(dateCondition["value"], Day.parse(today()))
            } ?: return
        if (next == currentDay) return
        val row = db.first("SELECT id, base_next_date_ts FROM schedules_next_date WHERE schedule_id = ?", id) ?: return
        val repr = ActualMonths.dateInt(next.toString())
        if (advance) {
            writer.update(
                "schedules_next_date",
                row["id"] as String,
                linkedMapOf(
                    "local_next_date" to repr,
                    "local_next_date_ts" to row["base_next_date_ts"],
                ),
            )
        } else {
            writer.update(
                "schedules_next_date",
                row["id"] as String,
                linkedMapOf(
                    "base_next_date" to repr,
                    "base_next_date_ts" to writer.nowMillis(),
                ),
            )
        }
    }

    /** `updateActions`: keeps a rule's fixed "set amount" actions equal to the amount the schedule posts. */
    private fun syncAmountActions(
        conditions: List<JsonObject>,
        actions: JsonArray,
    ): JsonArray {
        val amountCondition =
            conditions.firstOrNull { it.value("field") == "amount" && it.value("op") in setOf("is", "isapprox", "isbetween") }
                ?: return actions
        val amount = scheduledAmount(amountCondition["value"])
        return JsonArray(
            actions.map { element ->
                val action = element.jsonObject
                val options = action["options"] as? JsonObject
                if (action.value("op") == "set" &&
                    action.value("field") == "amount" &&
                    options?.get("template") == null &&
                    options?.get("formula") == null
                ) {
                    JsonObject(action + ("value" to JsonPrimitive(amount)))
                } else {
                    action
                }
            },
        )
    }

    private val internalFields =
        mapOf(
            "is_parent" to "isParent",
            "is_child" to "isChild",
            "account" to "acct",
            "imported_id" to "financial_id",
            "imported_payee" to "imported_description",
            "transfer_id" to "transferred_id",
            "payee" to "description",
        )

    private fun internal(item: JsonObject): JsonObject {
        val field = (item["field"] as? JsonPrimitive)?.contentOrNull ?: return item
        return internalFields[field]?.let { JsonObject(item + ("field" to JsonPrimitive(it))) } ?: item
    }

    private fun validate(fields: JsonObject) {
        if (fields.isEmpty()) fail("Add at least one schedule change")
        (fields.value("accountId") as String?)?.let { if (it.isBlank()) fail("Choose an account") }
        (fields.value("payeeId") as String?)?.let { if (it.isBlank()) fail("Choose a payee") }
        (fields.value("name") as String?)?.let { name ->
            if (name.any { it.code < 0x20 || it.code == 0x7f }) fail("Schedule names must not contain control characters")
        }
        val single = fields["amountMinor"]
        val range = fields["amountRangeMinor"] as? JsonObject
        val op = fields.value("amountOp") as String?
        if (single != null && range != null) fail("Choose a single amount or an amount range")
        if (single != null || range != null) {
            if (op == null) fail("Choose an amount rule")
            if ((range != null) != (op == "isbetween")) fail("The amount rule does not match the amount values")
            val values = if (range != null) listOf(range["num1"], range["num2"]) else listOf(single)
            val numbers = values.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.longOrNull }
            if (numbers.any {
                    it == null || kotlin.math.abs(it) > MAX_SCHEDULE_AMOUNT
                }
            ) {
                fail("Schedule amounts must be whole cents within the supported limit")
            }
            if (range != null && numbers[0]!! > numbers[1]!!) fail("The minimum amount must not exceed the maximum")
        } else if (op != null) {
            fail("An amount rule requires an amount")
        }
        fields["date"]?.let(::validateDate)
    }

    private fun validateDate(value: JsonElement) {
        if (value is JsonPrimitive) {
            if (!value.isString || !isIsoDate(value.content)) fail("Enter a schedule date as YYYY-MM-DD")
            return
        }
        val config = value as? JsonObject ?: fail("Enter a schedule date as YYYY-MM-DD")
        val start = config.value("start") as? String
        if (config.value("frequency") !in setOf("daily", "weekly", "monthly", "yearly") || start == null || !isIsoDate(start)) {
            fail("Choose a valid recurrence and start date")
        }
        config["interval"]?.let { interval ->
            val n = (interval as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (n == null || n < 1 || n > 999) fail("Repeat interval must be between 1 and 999")
        }
        config["skipWeekend"]?.let {
            if (it !is JsonPrimitive ||
                it.isString ||
                it.contentOrNull !in setOf("true", "false")
            ) {
                fail("Invalid weekend setting")
            }
        }
        config["weekendSolveMode"]?.let { if (scalar(it) !in setOf("before", "after")) fail("Choose a valid weekend adjustment") }
        config["patterns"]?.let { patterns ->
            val list = patterns as? JsonArray ?: fail("Choose a valid repeat pattern")
            val valid =
                list.size <= 31 &&
                    list.all { pattern ->
                        val obj = pattern as? JsonObject
                        val n = (obj?.get("value") as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
                        n != null && n in -31..31 && obj.value("type") in setOf("SU", "MO", "TU", "WE", "TH", "FR", "SA", "day")
                    }
            if (!valid) fail("Choose a valid repeat pattern")
        }
        val endMode = config.value("endMode")
        if (config["endMode"] != null && endMode !in setOf("never", "after_n_occurrences", "on_date")) fail("Choose a valid repeat end")
        if (endMode == "after_n_occurrences") {
            val n = (config["endOccurrences"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (n == null || n < 1 || n > 100_000) fail("Occurrence count must be between 1 and 100,000")
        }
        if (endMode == "on_date") {
            val end = config.value("endDate") as? String
            if (end == null || !isIsoDate(end) || end < start) fail("The end date must be on or after the start date")
        }
    }

    private fun isIsoDate(value: String): Boolean =
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value) && parseIsoMillis("${value}T00:00:00.000Z") != null
}

/** `getScheduledAmount`: a fixed amount, or the rounded midpoint of a range. */
internal fun scheduledAmount(value: JsonElement?): Long =
    when (value) {
        is JsonPrimitive -> value.longOrNull ?: value.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        is JsonObject -> {
            val low = (value["num1"] as? JsonPrimitive)?.longOrNull ?: 0L
            val high = (value["num2"] as? JsonPrimitive)?.longOrNull ?: 0L
            kotlin.math.round((low + high) / 2.0).toLong()
        }
        else -> 0L
    }
