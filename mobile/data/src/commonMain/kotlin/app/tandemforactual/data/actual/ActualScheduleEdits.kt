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
    private val transactions: ActualTransactions = ActualTransactions(writer),
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
        val payeeId = fields.value("payeeId") as String?
        requireEditTargets(accountId, payeeId)
        val ruleId = schedule.value("rule") as String
        val rule =
            ActualRules.load(db.query("SELECT * FROM rules WHERE id = ? AND tombstone = 0", ruleId), emptyMap()).firstOrNull()
                ?: fail("No rule found for schedule")
        val old = rule.getValue("conditions").jsonArray.map { it.jsonObject }
        val date = fields["date"]
        val conditions = editedConditions(old, fields, payeeId, accountId, date)
        val actions = syncAmountActions(conditions, rule.getValue("actions").jsonArray)
        writer.update(
            "rules",
            ruleId,
            linkedMapOf(
                "conditions" to stringifyJson(JsonArray(conditions.map(::internal))),
                "actions" to stringifyJson(JsonArray(actions.map { internal(it.jsonObject) })),
            ),
        )

        fun condition(
            list: List<JsonObject>,
            field: String,
        ) = list.firstOrNull { it.value("field") == field }?.let { JsonObject(it - "type" - "field") }
        if (condition(old, "account") != condition(conditions, "account") || condition(old, "date") != condition(conditions, "date")) {
            setNextDate(id, conditions, NextDate.RESET)
        }
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

    private fun requireEditTargets(
        accountId: String?,
        payeeId: String?,
    ) {
        if (accountId != null) {
            val account = db.first("SELECT closed FROM accounts WHERE id = ? AND tombstone = 0", accountId)
            if (account == null || truthy(account["closed"])) fail("Choose an open account in this budget")
        }
        if (payeeId != null && ActualBudgetReader(db).read(today()).records.none { it.type == "payee" && it.id == payeeId }) {
            fail("Choose a payee in this budget")
        }
    }

    /** [old] with the edit's payee, account, amount and date applied. */
    private fun editedConditions(
        old: List<JsonObject>,
        fields: JsonObject,
        payeeId: String?,
        accountId: String?,
        date: JsonElement?,
    ): MutableList<JsonObject> {
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
        if (date != null) {
            val index = indexOf { it.value("field") == "date" && it.value("op") in setOf("is", "isapprox") }
            if (index < 0) fail("Date can not be found. Schedules can not be created without a date there is a bug here")
            conditions[index] = JsonObject(conditions[index] + ("value" to date))
        }
        return conditions
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
        setNextDate(id, rule.getValue("conditions").jsonArray.map { it.jsonObject }, NextDate.ADVANCE)
    }

    /** `schedule/post-transaction`: adds the scheduled transaction on its next date, running the budget's rules. */
    fun post(
        id: String,
        expectedNextDate: String,
    ): String =
        writer.batch {
            val schedule = active(id) ?: changed()
            if (schedule.value("next_date") != expectedNextDate) changed()
            val account = schedule.value("account") as String? ?: fail("Choose an account before posting this schedule")
            val transaction =
                linkedMapOf(
                    "payee" to schedule.value("payee"),
                    "account" to account,
                    "amount" to scheduledAmount(schedule["amount"]),
                    "date" to expectedNextDate,
                    "schedule" to id,
                    "cleared" to false,
                )
            transactions.addTransactions(account, listOf(transaction), runTransfers = true).first()
        }

    fun delete(id: String) =
        writer.batch {
            val schedule = schedules().firstOrNull { it.value("id") == id } ?: fail("Schedule does not exist in this budget")
            writer.delete("rules", schedule.value("rule") as String)
            writer.delete("schedules", id)
        }

    private enum class NextDate { RESET, ADVANCE, REFRESH }

    /** `setNextDate`: RESET recomputes the base date after an edit, ADVANCE skips an occurrence, REFRESH follows today. */
    private fun setNextDate(
        id: String,
        conditions: List<JsonObject>,
        mode: NextDate,
    ) {
        val dateCondition = conditions.firstOrNull { it.value("field") == "date" && it.value("op") in setOf("is", "isapprox") } ?: return
        val current = db.first("SELECT next_date FROM v_schedules WHERE id = ?", id)?.get("next_date") as Number?
        val currentDay = current?.let { Day.parse(ActualMonths.dateString(it.toLong())) }
        val next =
            if (mode == NextDate.ADVANCE) {
                ActualRecurrence.nextDateAfter(dateCondition["value"], currentDay ?: Day.parse(today()))
            } else {
                ActualRecurrence.nextDateFrom(dateCondition["value"], Day.parse(today()))
            } ?: return
        if (next == currentDay) return
        val row = db.first("SELECT id, base_next_date_ts FROM schedules_next_date WHERE schedule_id = ?", id) ?: return
        val repr = ActualMonths.dateInt(next.toString())
        if (mode == NextDate.RESET) {
            writer.update(
                "schedules_next_date",
                row["id"] as String,
                linkedMapOf(
                    "base_next_date" to repr,
                    "base_next_date_ts" to writer.nowMillis(),
                ),
            )
        } else {
            writer.update(
                "schedules_next_date",
                row["id"] as String,
                linkedMapOf(
                    "local_next_date" to repr,
                    "local_next_date_ts" to row["base_next_date_ts"],
                ),
            )
        }
    }

    private fun conditionsOf(schedule: JsonObject): List<JsonObject> =
        ActualRules
            .load(db.query("SELECT * FROM rules WHERE id = ? AND tombstone = 0", schedule.value("rule")), emptyMap())
            .firstOrNull()
            ?.getValue("conditions")
            ?.jsonArray
            ?.map { it.jsonObject }
            .orEmpty()

    private fun isRecurring(schedule: JsonObject) = (schedule["date"] as? JsonObject)?.get("frequency") != null

    private fun hasTransaction(schedule: JsonObject): Boolean {
        val next = schedule.value("next_date") as String? ?: return false
        val exactDate = conditionsOf(schedule).firstOrNull { it.value("field") == "date" }?.value("op") == "is"
        val from = if (exactDate || schedule.value("posts_transaction") == true) next else Day.parse(next).plus(-2).toString()
        return db.first(
            "SELECT 1 AS found FROM v_transactions_internal_alive WHERE schedule = ? AND date >= ?",
            schedule.value("id"),
            ActualMonths.dateInt(from),
        ) !=
            null
    }

    private fun status(schedule: JsonObject): String {
        val next = schedule.value("next_date") as String?
        val day = today()
        return when {
            schedule.value("completed") == true -> "completed"
            hasTransaction(schedule) -> "paid"
            next == day -> "due"
            next != null && next < day -> "missed"
            else -> "scheduled"
        }
    }

    /**
     * `advanceSchedulesService`, which every Actual client runs after a sync: moves paid recurring schedules on,
     * completes paid one-time schedules, and posts due transactions for schedules set to post automatically.
     */
    fun runService(syncSuccess: Boolean) =
        writer.batch {
            val open =
                schedules().filter { schedule ->
                    val account = schedule.value("account") as String?
                    schedule.value("completed") != true &&
                        (
                            account == null ||
                                db.first("SELECT closed FROM accounts WHERE id = ?", account)?.let { !truthy(it["closed"]) } == true
                        )
                }
            for (start in open) {
                var schedule = start
                var current = status(schedule)
                val posts = schedule.value("posts_transaction") == true && schedule.value("account") != null
                if (posts && (current != "paid" || isRecurring(schedule)) && current in setOf("paid", "due", "missed")) {
                    while (current in setOf("paid", "due", "missed")) {
                        if (current != "paid") {
                            if (!syncSuccess) break
                            post(schedule.value("id") as String, schedule.value("next_date") as String)
                            if (current == "due" || !isRecurring(schedule)) break
                        } else if (schedule.value("next_date") == today()) {
                            break
                        }
                        val previous = schedule.value("next_date")
                        setNextDate(schedule.value("id") as String, conditionsOf(schedule), NextDate.ADVANCE)
                        schedule = schedules().firstOrNull { it.value("id") == schedule.value("id") } ?: break
                        if (schedule.value("next_date") == previous) break
                        current = status(schedule)
                    }
                } else if (current == "paid") {
                    if (isRecurring(schedule)) {
                        setNextDate(schedule.value("id") as String, conditionsOf(schedule), NextDate.REFRESH)
                    } else if (((schedule["date"] as? JsonPrimitive)?.contentOrNull ?: "9999") < today()) {
                        writer.update("schedules", schedule.value("id") as String, mapOf("completed" to 1L))
                    }
                }
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
}

internal fun isIsoDate(value: String): Boolean =
    Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value) && parseIsoMillis("${value}T00:00:00.000Z") != null

/** `getScheduledAmount`: a fixed amount, or the rounded midpoint of a range. */
internal fun scheduledAmount(value: JsonElement?): Long =
    when (value) {
        is JsonPrimitive -> value.longOrNull ?: value.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L
        is JsonObject -> {
            val low = (value["num1"] as? JsonPrimitive)?.longOrNull ?: 0L
            val high = (value["num2"] as? JsonPrimitive)?.longOrNull ?: 0L
            jsMathRound((low + high) / 2.0).toLong()
        }
        else -> 0L
    }
