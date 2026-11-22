package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A rule action Tandem can't run on the device yet, such as an Actual formula or template. */
class UnsupportedRuleException(
    message: String,
) : IllegalStateException(message)

/**
 * Actual's rule engine (`rules/condition.ts`, `rules/action.ts`, `rules/rule.ts`, `runRules`): ranks the budget's rules,
 * evaluates conditions against a transaction in Actual's public field names, and applies the actions, including
 * split actions. Formula and template actions are refused rather than approximated.
 */
class ActualRuleEngine(
    private val db: ActualDatabase,
    private val insertPayee: (String) -> String,
) {
    private sealed class Value {
        data class Literal(
            val value: Any?,
        ) : Value()

        data class Number(
            val value: Double,
        ) : Value()

        data class Between(
            val low: Double,
            val high: Double,
        ) : Value()

        data class Date(
            val kind: String,
            val date: String,
        ) : Value()

        data class Recur(
            val config: RecurConfig,
        ) : Value()
    }

    private class Condition(
        val op: String,
        val field: String,
        val type: String,
        val value: Value,
        val options: JsonObject?,
    )

    private class Action(
        val op: String,
        val field: String?,
        val value: Any?,
        val options: JsonObject?,
    ) {
        val splitIndex: Int get() = (options?.get("splitIndex") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val method: String? get() = (options?.get("method") as? JsonPrimitive)?.contentOrNull
    }

    private class Rule(
        val id: String,
        val stage: String?,
        val conditionsOp: String?,
        val conditions: List<Condition>,
        val actions: List<Action>,
    )

    private fun parseValue(
        type: String,
        op: String,
        raw: JsonElement?,
    ): Value =
        when (type) {
            "string" ->
                when {
                    op == "oneOf" || op == "notOneOf" ->
                        Value.Literal(
                            (raw as JsonArray).mapNotNull {
                                (it as? JsonPrimitive)
                                    ?.contentOrNull
                                    ?.takeIf(
                                        String::isNotEmpty,
                                    )?.lowercase()
                            },
                        )
                    op == "hasTags" || op == "hasAnyTag" -> Value.Literal((raw as JsonPrimitive).content)
                    else -> Value.Literal((raw as? JsonPrimitive)?.contentOrNull?.lowercase())
                }
            "number" ->
                if (op == "isbetween") {
                    val range = raw as JsonObject
                    Value.Between(range.value("num1").asDouble(), range.value("num2").asDouble())
                } else {
                    Value.Number((raw as JsonPrimitive).content.toDouble())
                }
            "date" ->
                when (raw) {
                    is JsonObject -> Value.Recur(RecurConfig.parse(raw))
                    else -> {
                        val text = (raw as JsonPrimitive).content
                        Value.Date(
                            when (text.length) {
                                10 -> "date"
                                7 -> "month"
                                else -> "year"
                            },
                            text,
                        )
                    }
                }
            else -> Value.Literal(scalar(raw).let { if (it is JsonArray) it.map(::scalar) else it })
        }

    private fun Any?.asDouble(): Double = (this as Number).toDouble()

    private fun loadRules(): List<Rule> {
        val mappings =
            (
                db.query("SELECT id, transferId AS target FROM category_mapping") +
                    db.query("SELECT id, targetId AS target FROM payee_mapping")
            ).mapNotNull { row -> (row["target"] as String?)?.let { row["id"] as String to it } }
                .toMap()
        return ActualRules
            .load(
                db.query("SELECT * FROM rules WHERE conditions IS NOT NULL AND actions IS NOT NULL AND tombstone = 0"),
                mappings,
            ).mapNotNull { json -> runCatching { parseRule(json) }.getOrNull() }
    }

    private fun parseRule(json: JsonObject): Rule =
        Rule(
            id = json.value("id") as String,
            stage = json.value("stage") as String?,
            conditionsOp = json.value("conditionsOp") as String?,
            conditions =
                json.getValue("conditions").jsonArray.map {
                    val c = it.jsonObject
                    val type = c.value("type") as String
                    Condition(
                        c.value("op") as String,
                        c.value("field") as String,
                        type,
                        parseValue(type, c.value("op") as String, c["value"]),
                        c["options"] as? JsonObject,
                    )
                },
            actions =
                json.getValue("actions").jsonArray.map {
                    val a = it.jsonObject
                    Action(a.value("op") as String, a.value("field") as String?, scalar(a["value"]), a["options"] as? JsonObject)
                },
        )

    private val opScores =
        mapOf(
            "is" to 10,
            "isNot" to 10,
            "oneOf" to 9,
            "notOneOf" to 9,
            "isapprox" to 5,
            "isbetween" to 5,
            "gt" to 1,
            "gte" to 1,
            "lt" to 1,
            "lte" to 1,
        )

    private fun score(rule: Rule): Int {
        val initial = rule.conditions.sumOf { opScores[it.op] ?: 0 }
        val exact = rule.conditions.all { it.op in setOf("is", "isNot", "isapprox", "oneOf", "notOneOf") }
        return if (exact) initial * 2 else initial
    }

    private fun rank(rules: List<Rule>): List<Rule> {
        fun sort(list: List<Rule>) = list.sortedWith(compareBy<Rule> { score(it) }.thenBy { it.id })
        return sort(rules.filter { it.stage == "pre" }) + sort(rules.filter { it.stage != "pre" && it.stage != "post" }) +
            sort(rules.filter { it.stage == "post" })
    }

    private fun eval(
        condition: Condition,
        transaction: Map<String, Any?>,
    ): Boolean {
        if (condition.type != "string" && !transaction.containsKey(condition.field)) return false
        var field: Any? = transaction[condition.field]
        if (condition.type == "string" && field == null) field = ""
        if (field is String) field = field.lowercase()
        if (condition.type == "number" && condition.options != null && field is Number) {
            val number = field.toDouble()
            if (condition.options["outflow"]?.let(::truthyJson) == true) {
                if (number > 0) return false
                field = -number
            } else if (condition.options["inflow"]?.let(::truthyJson) == true && number < 0) {
                return false
            }
        }
        val value = condition.value
        return when (condition.op) {
            "is", "isapprox" -> isMatch(condition.op, value, field)
            "isNot" -> field != (value as Value.Literal).value
            "isbetween" -> {
                val between = value as Value.Between
                val n = num(field) ?: return false
                n >= minOf(between.low, between.high) && n <= maxOf(between.low, between.high)
            }
            "contains" -> field != null && (value as Value.Literal).value.toString() in jsString(field)
            "doesNotContain" -> field != null && (value as Value.Literal).value.toString() !in jsString(field)
            "oneOf" -> field != null && field in ((value as Value.Literal).value as List<*>)
            "notOneOf" -> field != null && field !in ((value as Value.Literal).value as List<*>)
            "hasTags", "hasAnyTag" -> {
                if (field == null) return false
                val tags = extractTags((value as Value.Literal).value as String)
                val matches = tags.map { tag -> Regex("(?<!#)${Regex.escape(tag.lowercase())}([\\s#]|$)").containsMatchIn(jsString(field)) }
                if (condition.op == "hasTags") matches.all { it } else matches.any { it }
            }
            "gt", "gte", "lt", "lte" -> compare(condition.op, value, field)
            "matches" ->
                field != null &&
                    runCatching { Regex((value as Value.Literal).value as String).containsMatchIn(jsString(field)) }.getOrDefault(false)
            "onBudget", "offBudget" -> {
                val account = transaction["_account"] as? Map<*, *> ?: return false
                (account["offbudget"] as Number?)?.toLong() == if (condition.op == "onBudget") 0L else 1L
            }
            else -> false
        }
    }

    private fun num(any: Any?) = (any as? Number)?.toDouble()

    private fun isMatch(
        op: String,
        value: Value,
        field: Any?,
    ): Boolean =
        when (value) {
            is Value.Recur -> {
                val day = (field as? String)?.let(Day::parse) ?: return false
                if (op == "isapprox") {
                    ActualRecurrence.occursBetween(value.config, day.plus(-2), day.plus(2))
                } else {
                    ActualRecurrence.occursBetween(value.config, day, day)
                }
            }
            is Value.Date -> {
                val day = field as? String ?: return false
                if (op == "isapprox") {
                    val center = Day.parse(value.date)
                    day >= center.plus(-2).toString() && day <= center.plus(2).toString()
                } else {
                    when (value.kind) {
                        "date" -> day == value.date
                        "month" -> day.take(7) == value.date
                        else -> day.take(4) == value.date
                    }
                }
            }
            is Value.Number -> {
                val n = num(field) ?: return false
                if (op == "isapprox") {
                    val threshold = kotlin.math.round(kotlin.math.abs(value.value) * 0.075)
                    n >= value.value - threshold && n <= value.value + threshold
                } else {
                    n == value.value
                }
            }
            else -> field == (value as Value.Literal).value
        }

    private fun compare(
        op: String,
        value: Value,
        field: Any?,
    ): Boolean {
        if (field == null) return false
        val comparison =
            when (value) {
                is Value.Date -> (field as String).compareTo(value.date)
                else -> num(field)?.compareTo((value as Value.Number).value) ?: return false
            }
        return when (op) {
            "gt" -> comparison > 0
            "gte" -> comparison >= 0
            "lt" -> comparison < 0
            else -> comparison <= 0
        }
    }

    private fun truthyJson(element: JsonElement) = (element as? JsonPrimitive)?.contentOrNull == "true"

    private fun jsString(value: Any?): String =
        when (value) {
            is Double -> formatNumber(value)
            else -> value.toString()
        }

    private fun extractTags(value: String): List<String> =
        Regex("#*([^#\\s]+)")
            .findAll(value)
            .map { "#" + it.groupValues[1] }
            .distinct()
            .toList()

    private fun matches(
        rule: Rule,
        transaction: Map<String, Any?>,
    ): Boolean {
        if (rule.conditions.isEmpty()) return false
        return if (rule.conditionsOp ==
            "or"
        ) {
            rule.conditions.any { eval(it, transaction) }
        } else {
            rule.conditions.all { eval(it, transaction) }
        }
    }

    private fun exec(
        action: Action,
        transaction: MutableMap<String, Any?>,
    ) {
        if (action.options?.get("formula") != null || action.options?.get("template") != null) {
            throw UnsupportedRuleException("A rule uses a formula or template. Make this change in Actual.")
        }
        when (action.op) {
            "set" -> {
                transaction[action.field!!] = action.value
                if (action.field == "payee_name") transaction["payee"] = "new"
            }
            "set-split-amount" ->
                when (action.method) {
                    "fixed-amount" -> transaction["amount"] = (action.value as Number).toLong()
                    "formula" -> throw UnsupportedRuleException("A rule uses a formula or template. Make this change in Actual.")
                }
            "link-schedule" -> transaction["schedule"] = action.value
            "prepend-notes", "append-notes" -> {
                val current = transaction["notes"] as String?
                transaction["notes"] =
                    when {
                        current.isNullOrEmpty() -> action.value
                        action.op == "prepend-notes" -> "${action.value}$current"
                        else -> "$current${action.value}"
                    }
            }
            "delete-transaction" -> transaction["tombstone"] = 1L
        }
    }

    private fun execActions(
        actions: List<Action>,
        transaction: Map<String, Any?>,
        newId: () -> String,
    ): Map<String, Any?> {
        val parentActions = actions.filter { it.splitIndex == 0 }
        val childActions = actions.filter { it.splitIndex != 0 }
        val result = LinkedHashMap(transaction)
        parentActions.forEach { exec(it, result) }
        if (actions.maxOfOrNull { it.splitIndex } ?: 0 == 0 || truthy(result["is_child"])) return result
        return split(childActions, result, newId)
    }

    private fun child(
        parent: Map<String, Any?>,
        sortOrder: Any?,
        newId: () -> String,
        payee: Any? = parent["payee"],
    ): MutableMap<String, Any?> =
        linkedMapOf(
            "amount" to 0L,
            "category" to parent["category"],
            "payee" to payee,
            "id" to newId(),
            "account" to parent["account"],
            "date" to parent["date"],
            "cleared" to parent["cleared"],
            "reconciled" to parent["reconciled"],
            "starting_balance_flag" to parent["starting_balance_flag"],
            "sort_order" to sortOrder,
            "is_child" to true,
            "parent_id" to parent["id"],
            "error" to null,
        )

    private fun remainder(list: List<Map<String, Any?>>): Long {
        val parent = (list[0]["amount"] as Number?)?.toLong() ?: 0L
        return parent - list.drop(1).sumOf { (it["amount"] as Number?)?.toLong() ?: 0L }
    }

    private fun split(
        actions: List<Action>,
        transaction: Map<String, Any?>,
        newId: () -> String,
    ): Map<String, Any?> {
        val amount = (transaction["amount"] as Number?)?.toLong() ?: 0L
        val parent = LinkedHashMap(transaction - "subtransactions")
        val first = child(parent, null, newId).also { if (truthy(it["sort_order"]).not()) it["sort_order"] = -1L }
        parent["is_parent"] = true
        parent["payee"] = null
        val list = mutableListOf<MutableMap<String, Any?>>(parent, first)
        for (action in actions) {
            val index = action.splitIndex + 1
            if (index >= list.size) {
                val previous = list.last()
                list +=
                    child(parent, ((previous["sort_order"] as Number?)?.toLong() ?: 0L) - 1, newId, previous["payee"] ?: parent["payee"])
            }
            exec(action, list[index])
        }
        val afterFixed = remainder(list)
        actions.filter { it.op == "set-split-amount" && it.method == "fixed-percent" }.forEach {
            list[it.splitIndex + 1]["amount"] = kotlin.math.round(afterFixed * ((it.value as Number).toDouble() / 100)).toLong()
        }
        val remainders = actions.filter { it.op == "set-split-amount" && it.method == "remainder" }
        val afterPercent = remainder(list)
        if (remainders.isNotEmpty()) {
            val each = kotlin.math.round(afterPercent.toDouble() / remainders.size).toLong()
            remainders.forEach { list[it.splitIndex + 1]["amount"] = each }
            val last = remainders.maxOf { it.splitIndex + 1 }
            list[last]["amount"] = (list[last]["amount"] as Long) + remainder(list)
        }
        list.removeAt(1)
        val subtransactions = list.drop(1)
        val total = subtransactions.sumOf { (it["amount"] as Number?)?.toLong() ?: 0L }
        return LinkedHashMap(list[0]).apply {
            this["subtransactions"] = subtransactions
            this["error"] =
                if (total == amount) null else mapOf("type" to "SplitTransactionError", "version" to 1L, "difference" to amount - total)
        }
    }

    /** Ids of the newest transactions a rule's conditions match, newest first, like Actual's rule preview. */
    fun preview(
        wire: JsonObject,
        limit: Int = 50,
    ): List<String> {
        val loaded = ActualRules.load(listOf(RuleWire.row(wire)), emptyMap()).firstOrNull() ?: return emptyList()
        val parsed = parseRule(JsonObject(loaded + ("actions" to JsonArray(emptyList()))))
        return db
            .query("SELECT * FROM v_transactions WHERE is_parent = 0 ORDER BY date DESC, sort_order DESC LIMIT 5000")
            .asSequence()
            .filter { row ->
                val fields =
                    mapOf(
                        "id" to row["id"],
                        "date" to ActualDates.fromInt((row["date"] as Number).toLong()),
                        "amount" to row["amount"],
                        "payee" to row["payee"],
                        "account" to row["account"],
                        "category" to row["category"],
                        "notes" to row["notes"],
                        "imported_payee" to row["imported_payee"],
                        "cleared" to row["cleared"],
                        "reconciled" to row["reconciled"],
                    )
                matches(parsed, prepare(fields))
            }.take(limit)
            .map { it["id"] as String }
            .toList()
    }

    /** `runRules`: applies every matching rule in rank order, as Actual does when posting or importing a transaction. */
    fun run(
        transaction: Map<String, Any?>,
        newId: () -> String,
    ): Map<String, Any?> {
        val missing = listOf("payee", "category").filter { !transaction.containsKey(it) }
        var current: Map<String, Any?> = LinkedHashMap(transaction).apply { missing.forEach { this[it] = null } }.let(::prepare)
        var lastCategory = current["category"] as String?
        val scheduleRule =
            (transaction["schedule"] as String?)?.let {
                db.first("SELECT rule FROM schedules WHERE id = ?", it)?.get("rule") as String?
            }
        val scheduleRules = db.query("SELECT rule FROM schedules").mapNotNull { it["rule"] as String? }.filter { it != scheduleRule }
        for (rule in rank(loadRules())) {
            val run =
                when {
                    scheduleRule != null && rule.id == scheduleRule -> true
                    scheduleRule != null && rule.id in scheduleRules -> continue
                    else -> matches(rule, current)
                }
            if (run) current = execActions(rule.actions, current, newId)
            current = resolvePayeeName(current)
            val category = current["category"] as String?
            if (category != lastCategory) {
                current =
                    LinkedHashMap(current).apply {
                        if (category !=
                            null
                        ) {
                            this["category_group"] =
                                db.first("SELECT cat_group FROM categories WHERE id = ?", category)?.get("cat_group")
                        } else {
                            remove("category_group")
                        }
                    }
                lastCategory = category
            }
        }
        val result = LinkedHashMap(resolvePayeeName(current))
        result.keys.removeAll(
            setOf(
                "payee_name",
                "balance",
                "category_group",
                "parent_amount",
                "parent_notes",
                "parent_imported_payee",
                "parent_payee",
                "parent_account",
            ),
        )
        result.keys.removeAll { it.startsWith("_") }
        missing.forEach { if (result[it] == null) result.remove(it) }
        return result
    }

    private fun prepare(transaction: Map<String, Any?>): Map<String, Any?> {
        val prepared = LinkedHashMap(transaction)
        (transaction["payee"] as String?)?.let { payee ->
            db.first("SELECT name FROM payees WHERE id = ?", payee)?.let {
                prepared["payee_name"] =
                    it["name"]
            }
        }
        prepared["balance"] = 0L
        (transaction["account"] as String?)?.let { account ->
            val row = db.first("SELECT * FROM accounts WHERE id = ?", account)
            prepared["_account"] = row
            prepared["_account_name"] = row?.get("name") ?: ""
        }
        (transaction["category"] as String?)?.let { category ->
            db.first("SELECT name, cat_group FROM categories WHERE id = ?", category)?.let {
                prepared["_category_name"] = it["name"]
                prepared["category_group"] = it["cat_group"]
            }
        }
        return prepared
    }

    private fun resolvePayeeName(transaction: Map<String, Any?>): Map<String, Any?> {
        if (!transaction.containsKey("payee_name") || transaction["payee"] != "new") return transaction
        val name = transaction["payee_name"] as String?
        val payee =
            if (name.isNullOrEmpty()) {
                null
            } else {
                db
                    .query("SELECT id, name FROM payees WHERE tombstone = 0")
                    .firstOrNull {
                        (it["name"] as String?)?.lowercase() ==
                            name.lowercase()
                    }?.get("id")
                    ?: insertPayee(name)
            }
        return LinkedHashMap(transaction).apply { this["payee"] = payee }
    }
}
