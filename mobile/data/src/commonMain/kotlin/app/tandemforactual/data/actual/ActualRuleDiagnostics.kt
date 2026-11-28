package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

enum class RuleSeverity { PROBLEM, WARNING, SUGGESTION }

enum class RuleFindingKind {
    MISSING_REFERENCE,
    CONTRADICTORY,
    NO_CONDITIONS,
    NO_ACTIONS,
    NO_OP_ACTIONS,
    SHADOWED,
    DUPLICATE,
    NEAR_DUPLICATE,
    BROAD,
}

/**
 * Something about [ruleId] that likely doesn't do what was meant. [related] are the other rules involved, [fields] the
 * fields at fault, [matched] of [checked] recent transactions what a broad rule caught, and [merged] the rule that
 * replaces a group of near duplicates.
 */
data class RuleFinding(
    val kind: RuleFindingKind,
    val severity: RuleSeverity,
    val ruleId: String,
    val related: List<String> = emptyList(),
    val fields: List<String> = emptyList(),
    val matched: Int = 0,
    val checked: Int = 0,
    val merged: JsonObject? = null,
) {
    val key get() = "RULE_${kind.name}:" + (listOf(ruleId) + related).joinToString(",")
}

/** Read-only checks of a budget's rules with Actual's stage, ranking and condition semantics. Same rules, same findings. */
class ActualRuleDiagnostics(
    private val db: ActualDatabase,
) {
    private class Condition(
        val op: String,
        val field: String,
        val type: String,
        val value: JsonElement,
        val options: String,
    ) {
        val text get() = (value as? JsonPrimitive)?.contentOrNull?.let { if (type == "string") it.lowercase() else it }
        val list get() = (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercaseIf(type == "string") }?.toSet()
        val canonical get() = canonicalJson(JsonObject(mapOf("op" to JsonPrimitive(op), "field" to JsonPrimitive(field), "v" to norm())))

        private fun norm(): JsonElement {
            val normalized =
                when {
                    value is JsonArray -> JsonArray(list.orEmpty().sorted().map(::JsonPrimitive))
                    type == "string" -> JsonPrimitive(text)
                    else -> value
                }
            return JsonObject(mapOf("value" to normalized, "options" to JsonPrimitive(options)))
        }
    }

    private class Rule(
        val id: String,
        val stage: String,
        val or: Boolean,
        val conditions: List<Condition>,
        val actions: List<JsonObject>,
        val scheduled: Boolean,
        val json: JsonObject,
    ) {
        val sets get() = actions.filter(::isPlainSet).map { it.value("field") as String }
        val conditionKeys get() = conditions.map { it.canonical }.sorted()
        val actionKeys get() = actions.map(::canonicalJson).sorted()
        val signature get() = listOf(stage, or.toString(), conditionKeys.toString(), actionKeys.toString()).joinToString("|")
    }

    fun check(): List<RuleFinding> {
        val engine = ActualRuleEngine(db) { error("Diagnostics never create payees") }
        val order = engine.rankedIds()
        val loaded = engine.loadedRules().associateBy { it.value("id") as String }
        val scheduled = db.query("SELECT rule FROM schedules WHERE tombstone = 0").mapNotNull { it["rule"] as String? }.toSet()
        val rules = order.map { parse(loaded.getValue(it), it in scheduled) }
        val known =
            mapOf(
                "payee" to ids("payees"),
                "category" to ids("categories"),
                "account" to ids("accounts"),
                "category_group" to ids("category_groups"),
            )
        val (checked, counts) = engine.matchCounts(SAMPLE)
        val findings =
            rules.flatMap { missing(it, known) + contradictions(it) + empties(it) } + shadows(rules) + duplicates(rules) +
                broad(rules, checked, counts)
        val rank = order.withIndex().associate { it.value to it.index }
        return findings.sortedWith(compareBy({ it.severity }, { it.kind }, { rank.getValue(it.ruleId) }, { it.key }))
    }

    private fun ids(table: String) = db.query("SELECT id FROM $table WHERE tombstone = 0").map { it["id"] as String }.toSet()

    private fun parse(
        json: JsonObject,
        scheduled: Boolean,
    ): Rule {
        val conditions =
            json.getValue("conditions").jsonArray.map {
                val c = it.jsonObject
                Condition(
                    c.value("op") as String,
                    c.value("field") as String,
                    c.value("type") as String,
                    c["value"] ?: JsonNull,
                    canonicalJson(c["options"] ?: JsonNull),
                )
            }
        return Rule(
            json.value("id") as String,
            json.value("stage") as String? ?: "default",
            json.value("conditionsOp") == "or" && conditions.size > 1,
            conditions,
            json.getValue("actions").jsonArray.map { it.jsonObject },
            scheduled,
            json,
        )
    }

    private fun missing(
        rule: Rule,
        known: Map<String, Set<String>>,
    ): List<RuleFinding> {
        fun broken(
            field: String?,
            op: String?,
            value: JsonElement?,
        ): Boolean {
            val ids =
                when (op) {
                    "is", "isNot", "set" -> listOf(value)
                    "oneOf", "notOneOf" -> (value as? JsonArray).orEmpty()
                    else -> emptyList()
                }.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            return known[field]?.let { set -> ids.any { it !in set } } == true
        }
        val inConditions = rule.conditions.filter { broken(it.field, it.op, it.value) }.map { it.field }
        val inActions = rule.actions.filter { broken(it.value("field") as String?, it.value("op") as String?, it["value"]) }
        val fields = (inConditions + inActions.map { it.value("field") as String }).distinct().sorted()
        if (fields.isEmpty()) return emptyList()
        val severity = if (inActions.isEmpty()) RuleSeverity.WARNING else RuleSeverity.PROBLEM
        return listOf(RuleFinding(RuleFindingKind.MISSING_REFERENCE, severity, rule.id, fields = fields))
    }

    private fun neverMatches(rule: Rule) =
        !rule.or &&
            rule.conditions.indices.any { i ->
                (i + 1 until rule.conditions.size).any { j -> contradict(rule.conditions[i], rule.conditions[j]) }
            }

    private fun contradictions(rule: Rule): List<RuleFinding> {
        if (!neverMatches(rule)) return emptyList()
        val fields =
            rule.conditions
                .map { it.field }
                .distinct()
                .sorted()
        return listOf(RuleFinding(RuleFindingKind.CONTRADICTORY, RuleSeverity.PROBLEM, rule.id, fields = fields))
    }

    private fun empties(rule: Rule): List<RuleFinding> {
        if (rule.conditions.isEmpty()) return listOf(RuleFinding(RuleFindingKind.NO_CONDITIONS, RuleSeverity.WARNING, rule.id))
        if (rule.actions.isEmpty()) return listOf(RuleFinding(RuleFindingKind.NO_ACTIONS, RuleSeverity.WARNING, rule.id))
        val noOps = rule.actions.filterIndexed { index, action -> noOp(rule, index, action) }
        if (noOps.isEmpty()) return emptyList()
        val severity = if (noOps.size == rule.actions.size) RuleSeverity.WARNING else RuleSeverity.SUGGESTION
        val fields = noOps.map { it.value("field") as String }.distinct().sorted()
        return listOf(RuleFinding(RuleFindingKind.NO_OP_ACTIONS, severity, rule.id, fields = fields))
    }

    private fun noOp(
        rule: Rule,
        index: Int,
        action: JsonObject,
    ): Boolean {
        val op = action.value("op")
        if (op == "prepend-notes" || op == "append-notes") return (action.value("value") as? String).isNullOrEmpty()
        if (!isPlainSet(action)) return false
        val field = action.value("field")
        val replaced = rule.actions.drop(index + 1).any { isPlainSet(it) && it.value("field") == field }
        val matched = !rule.or && rule.conditions.any { it.op == "is" && it.field == field && it.value == action["value"] }
        return replaced || matched
    }

    private fun shadows(rules: List<Rule>): List<RuleFinding> =
        rules.withIndex().mapNotNull { (i, rule) ->
            val candidate =
                !rule.scheduled &&
                    rule.conditions.isNotEmpty() &&
                    rule.actions.isNotEmpty() &&
                    rule.actions.all(::isPlainSet) &&
                    !neverMatches(rule)
            if (!candidate) return@mapNotNull null
            var changed = rule.sets.toSet()
            val later =
                rules.drop(i + 1).takeWhile { it.stage == rule.stage }.firstOrNull { other ->
                    val shadows =
                        !other.scheduled &&
                            other.signature != rule.signature &&
                            other.sets.containsAll(rule.sets) &&
                            other.conditions.none { it.field in changed } &&
                            implies(rule, other)
                    changed = changed + other.sets
                    shadows
                }
            later?.let {
                RuleFinding(
                    RuleFindingKind.SHADOWED,
                    RuleSeverity.WARNING,
                    rule.id,
                    listOf(it.id),
                    rule.sets.distinct().sorted(),
                )
            }
        }

    private fun duplicates(rules: List<Rule>): List<RuleFinding> {
        val live = rules.filter { !it.scheduled && it.conditions.isNotEmpty() }
        val exact =
            live.groupBy { it.signature }.values.filter { it.size > 1 }.map { group ->
                RuleFinding(RuleFindingKind.DUPLICATE, RuleSeverity.WARNING, group.first().id, group.drop(1).map { it.id })
            }
        val seen = mutableSetOf<Set<String>>()
        val near =
            live
                .flatMap { rule ->
                    rule.conditions
                        .withIndex()
                        .filter { (_, c) ->
                            c.op in MERGEABLE &&
                                c.type in setOf("id", "string") &&
                                c.field != "notes" &&
                                c.value !is JsonNull
                        }.map { (k, c) ->
                            val rest =
                                rule.conditions
                                    .filterIndexed { index, _ -> index != k }
                                    .map { it.canonical }
                                    .sorted()
                            listOf(rule.stage, rule.or, rule.actionKeys, rest, c.field, c.options).toString() to (rule to k)
                        }
                }.groupBy({ it.first }, { it.second })
                .values
                .filter { group -> group.map { it.first.signature }.distinct().size > 1 }
                .mapNotNull { group ->
                    val members = group.distinctBy { it.first.id }
                    if (!seen.add(members.map { it.first.id }.toSet())) return@mapNotNull null
                    val (keep, k) = members.first()
                    RuleFinding(
                        RuleFindingKind.NEAR_DUPLICATE,
                        RuleSeverity.SUGGESTION,
                        keep.id,
                        members.drop(1).map { it.first.id },
                        listOf(keep.conditions[k].field),
                        merged = merge(keep, k, members.flatMap { (rule, index) -> values(rule.conditions[index]) }),
                    )
                }
        return exact + near
    }

    private fun values(condition: Condition): List<JsonElement> = (condition.value as? JsonArray) ?: listOf(condition.value)

    private fun merge(
        keep: Rule,
        index: Int,
        values: List<JsonElement>,
    ): JsonObject {
        val target = keep.conditions[index]
        val distinct = values.distinctBy { (it as JsonPrimitive).content.lowercaseIf(target.type == "string") }
        val conditions =
            keep.json
                .getValue("conditions")
                .jsonArray
                .toMutableList()
        conditions[index] = JsonObject(conditions[index].jsonObject + mapOf("op" to JsonPrimitive("oneOf"), "value" to JsonArray(distinct)))
        return JsonObject(keep.json + ("conditions" to JsonArray(conditions)))
    }

    private fun broad(
        rules: List<Rule>,
        checked: Int,
        counts: Map<String, Int>,
    ): List<RuleFinding> {
        if (checked < MIN_SAMPLE) return emptyList()
        return rules
            .filter { !it.scheduled && it.actions.isNotEmpty() && counts.getValue(it.id) * 100 >= checked * BROAD_PERCENT }
            .map { RuleFinding(RuleFindingKind.BROAD, RuleSeverity.SUGGESTION, it.id, matched = counts.getValue(it.id), checked = checked) }
    }

    /** Whether every transaction [rule] matches is also matched by [other]. */
    private fun implies(
        rule: Rule,
        other: Rule,
    ): Boolean {
        fun covers(c: Condition) = if (other.or) other.conditions.any { implies(c, it) } else other.conditions.all { implies(c, it) }
        return if (rule.or) {
            rule.conditions.all(::covers)
        } else if (other.or) {
            other.conditions.any { o -> rule.conditions.any { implies(it, o) } }
        } else {
            other.conditions.all { o -> rule.conditions.any { implies(it, o) } }
        }
    }

    private fun implies(
        a: Condition,
        b: Condition,
    ): Boolean {
        if (a.field != b.field || a.options != b.options || a.type != b.type) return false
        if (a.canonical == b.canonical) return true
        val (low, high) = range(a) to range(b)
        if (low != null && high != null) return high.contains(low)
        if (a.type in RANGED) return false
        val text = a.type == "string"
        return when (a.op to b.op) {
            "is" to "isNot" -> a.text != b.text
            "is" to "oneOf" -> a.text in b.list.orEmpty()
            "is" to "notOneOf" -> a.text !in b.list.orEmpty()
            "is" to "contains" -> text && b.text.orEmpty() in a.text.orEmpty()
            "is" to "doesNotContain" -> text && b.text.orEmpty() !in a.text.orEmpty()
            "oneOf" to "oneOf" -> b.list.orEmpty().containsAll(a.list.orEmpty())
            "oneOf" to "isNot" -> b.text !in a.list.orEmpty()
            "oneOf" to "notOneOf" -> a.list.orEmpty().none { it in b.list.orEmpty() }
            "contains" to "contains" -> text && b.text.orEmpty() in a.text.orEmpty()
            "doesNotContain" to "doesNotContain" -> text && a.text.orEmpty() in b.text.orEmpty()
            "notOneOf" to "notOneOf" -> a.list.orEmpty().containsAll(b.list.orEmpty())
            "notOneOf" to "isNot" -> b.text in a.list.orEmpty()
            else -> false
        }
    }

    private fun contradict(
        a: Condition,
        b: Condition,
    ): Boolean {
        if (a.field != b.field || a.options != b.options || a.type != b.type) return false
        val (first, second) = range(a) to range(b)
        if (first != null && second != null) return !first.overlaps(second)
        return a.type !in RANGED && (excludes(a, b) || excludes(b, a))
    }

    private fun excludes(
        a: Condition,
        b: Condition,
    ): Boolean {
        val text = a.type == "string"
        return when (a.op to b.op) {
            "is" to "is" -> a.text != b.text
            "is" to "isNot" -> a.text == b.text
            "is" to "oneOf" -> a.text !in b.list.orEmpty()
            "is" to "notOneOf" -> a.text in b.list.orEmpty()
            "is" to "contains" -> text && b.text.orEmpty() !in a.text.orEmpty()
            "is" to "doesNotContain" -> text && b.text.orEmpty() in a.text.orEmpty()
            "oneOf" to "oneOf" -> a.list.orEmpty().none { it in b.list.orEmpty() }
            "oneOf" to "notOneOf" -> b.list.orEmpty().containsAll(a.list.orEmpty())
            "contains" to "doesNotContain" -> text && b.text.orEmpty() in a.text.orEmpty()
            else -> false
        }
    }

    private class Range(
        val low: Double,
        val lowOpen: Boolean,
        val high: Double,
        val highOpen: Boolean,
    ) {
        fun overlaps(other: Range): Boolean {
            val start = maxOf(low, other.low)
            val end = minOf(high, other.high)
            val startOpen = (low == start && lowOpen) || (other.low == start && other.lowOpen)
            val endOpen = (high == end && highOpen) || (other.high == end && other.highOpen)
            return start < end || (start == end && !startOpen && !endOpen)
        }

        fun contains(inner: Range): Boolean {
            val lowOk = low < inner.low || (low == inner.low && (!lowOpen || inner.lowOpen))
            val highOk = high > inner.high || (high == inner.high && (!highOpen || inner.highOpen))
            return lowOk && highOk
        }
    }

    /** The values a number or calendar-date condition allows, or null when it isn't a plain range. */
    private fun range(c: Condition): Range? {
        val inf = Double.POSITIVE_INFINITY
        val at: (JsonElement?) -> Double? = { point(c.type, it) }
        return when (c.op) {
            "isbetween" -> {
                val bounds = c.value as? JsonObject ?: return null
                val (x, y) = (at(bounds["num1"]) ?: return null) to (at(bounds["num2"]) ?: return null)
                Range(minOf(x, y), false, maxOf(x, y), false)
            }

            "is" -> {
                if (c.type == "date") dateRange(c.text) else at(c.value)?.let { Range(it, false, it, false) }
            }

            "isapprox" -> {
                if (c.type == "number") {
                    at(c.value)?.let { v -> jsMathRound(kotlin.math.abs(v) * 0.075).let { t -> Range(v - t, false, v + t, false) } }
                } else {
                    null
                }
            }

            "gt" -> {
                at(c.value)?.let { Range(it, true, inf, true) }
            }

            "gte" -> {
                at(c.value)?.let { Range(it, false, inf, true) }
            }

            "lt" -> {
                at(c.value)?.let { Range(-inf, true, it, true) }
            }

            "lte" -> {
                at(c.value)?.let { Range(-inf, true, it, false) }
            }

            else -> {
                null
            }
        }
    }

    private fun point(
        type: String,
        value: JsonElement?,
    ): Double? {
        val primitive = value as? JsonPrimitive ?: return null
        return when (type) {
            "number" -> primitive.contentOrNull?.toDoubleOrNull()
            "date" -> primitive.contentOrNull?.takeIf { it.length == 10 }?.let { ActualDates.toInt(it).toDouble() }
            else -> null
        }
    }

    private fun dateRange(text: String?): Range? =
        when (text?.length) {
            10 -> ActualDates.toInt(text).toDouble().let { Range(it, false, it, false) }
            7 -> (text.replace("-", "").toDouble() * 100).let { Range(it + 1, false, it + 31, false) }
            4 -> (text.toDouble() * 10_000).let { Range(it + 101, false, it + 1231, false) }
            else -> null
        }
}

private const val SAMPLE = 2000
private const val MIN_SAMPLE = 20
private const val BROAD_PERCENT = 40
private val MERGEABLE = setOf("is", "oneOf")
private val RANGED = setOf("number", "date")

private fun isPlainSet(action: JsonObject): Boolean {
    val options = action["options"] as? JsonObject
    val split = (options?.get("splitIndex") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    return action.value("op") == "set" && split == 0 && options?.get("formula") == null && options?.get("template") == null
}

private fun String.lowercaseIf(condition: Boolean) = if (condition) lowercase() else this
