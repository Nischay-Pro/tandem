package app.tandemforactual.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.absoluteValue

internal enum class RuleConditionField(
    val wire: String,
    val label: String,
    val ops: List<String>,
) {
    PAYEE("payee", "Payee", ID_OPS),
    IMPORTED_PAYEE("imported_payee", "Imported payee", TEXT_OPS),
    NOTES("notes", "Notes", TEXT_OPS),
    ACCOUNT("account", "Account", ID_OPS),
    CATEGORY("category", "Category", ID_OPS),
    AMOUNT("amount", "Amount", AMOUNT_OPS),
    MONEY_OUT("amount", "Money out", AMOUNT_OPS),
    MONEY_IN("amount", "Money in", AMOUNT_OPS),
    ;

    val usesIds: Boolean get() = ops === ID_OPS
    val usesAmount: Boolean get() = ops === AMOUNT_OPS
}

private val ID_OPS = listOf("is", "isNot", "oneOf", "notOneOf")
private val TEXT_OPS = listOf("is", "isNot", "contains", "doesNotContain", "matches")
private val AMOUNT_OPS = listOf("is", "isapprox", "isbetween", "gt", "gte", "lt", "lte")

internal fun ruleOperatorLabel(op: String): String =
    when (op) {
        "is" -> "is"
        "isNot" -> "is not"
        "oneOf" -> "is one of"
        "notOneOf" -> "is not one of"
        "contains" -> "contains"
        "doesNotContain" -> "doesn’t contain"
        "matches" -> "matches pattern"
        "isapprox" -> "is about"
        "isbetween" -> "is between"
        "gt" -> "is more than"
        "gte" -> "is at least"
        "lt" -> "is less than"
        "lte" -> "is at most"
        else -> op
    }

internal enum class RuleActionKind(
    val label: String,
    val field: String?,
    val op: String,
) {
    SET_CATEGORY("Set category", "category", "set"),
    SET_PAYEE("Set payee", "payee", "set"),
    SET_ACCOUNT("Set account", "account", "set"),
    SET_NOTES("Set notes", "notes", "set"),
    PREPEND_NOTES("Add to start of notes", null, "prepend-notes"),
    APPEND_NOTES("Add to end of notes", null, "append-notes"),
    SET_CLEARED("Set cleared", "cleared", "set"),
    ;

    val usesId: Boolean get() = this.field in setOf("category", "payee", "account")
}

internal enum class RuleStage(
    val wire: String,
    val label: String,
    val detail: String,
) {
    PRE("pre", "First", "Runs before other rules, so later rules can build on what it sets."),
    DEFAULT("default", "Normal", "Runs with most rules, after any set to run first."),
    POST("post", "Last", "Runs after every other rule, so its changes win."),
}

internal data class RuleConditionDraft(
    val field: RuleConditionField = RuleConditionField.PAYEE,
    val op: String = field.ops.first(),
    val text: String = "",
    val ids: List<String> = emptyList(),
    val amountText: String = "",
    val amountMaxText: String = "",
)

internal data class RuleActionDraft(
    val kind: RuleActionKind = RuleActionKind.SET_CATEGORY,
    val id: String = "",
    val text: String = "",
    val cleared: Boolean = true,
)

internal data class RuleDraft(
    val stage: RuleStage = RuleStage.DEFAULT,
    val matchAll: Boolean = true,
    val conditions: List<RuleConditionDraft> = listOf(RuleConditionDraft()),
    val actions: List<RuleActionDraft> = listOf(RuleActionDraft()),
)

/** Returns null when the rule uses anything the editor can't show faithfully. */
internal fun JsonObject.toRuleDraftOrNull(formatAmount: (Long) -> String): RuleDraft? {
    val stage =
        when ((this["stage"] as? JsonPrimitive)?.contentOrNull) {
            "pre" -> RuleStage.PRE
            "post" -> RuleStage.POST
            null, "default" -> RuleStage.DEFAULT
            else -> return null
        }
    val matchAll =
        when ((this["conditionsOp"] as? JsonPrimitive)?.contentOrNull) {
            "or" -> false
            null, "and" -> true
            else -> return null
        }
    val conditions =
        (this["conditions"] as? JsonArray ?: return null).map {
            (it as? JsonObject)?.toConditionDraft(formatAmount)
                ?: return null
        }
    val actions = (this["actions"] as? JsonArray ?: return null).map { (it as? JsonObject)?.toActionDraft() ?: return null }
    if (conditions.isEmpty() || actions.isEmpty()) return null
    return RuleDraft(stage, matchAll, conditions, actions)
}

private fun JsonObject.toConditionDraft(formatAmount: (Long) -> String): RuleConditionDraft? {
    val wireField = (this["field"] as? JsonPrimitive)?.contentOrNull ?: return null
    val op = (this["op"] as? JsonPrimitive)?.contentOrNull ?: return null
    val options = this["options"] as? JsonObject
    val inflow = (options?.get("inflow") as? JsonPrimitive)?.booleanOrNull == true
    val outflow = (options?.get("outflow") as? JsonPrimitive)?.booleanOrNull == true
    if (options != null && options.keys.any { it !in setOf("inflow", "outflow") }) return null
    if (inflow && outflow) return null
    val field =
        when {
            wireField == "amount" && outflow -> RuleConditionField.MONEY_OUT
            wireField == "amount" && inflow -> RuleConditionField.MONEY_IN
            wireField == "amount" -> RuleConditionField.AMOUNT
            options != null && (inflow || outflow) -> return null
            else -> RuleConditionField.entries.firstOrNull { it.wire == wireField && !it.usesAmount } ?: return null
        }
    if (op !in field.ops) return null
    val value = this["value"] ?: return null
    return when {
        field.usesAmount && op == "isbetween" -> {
            val range = value as? JsonObject ?: return null
            val low = (range["num1"] as? JsonPrimitive)?.longOrNull ?: return null
            val high = (range["num2"] as? JsonPrimitive)?.longOrNull ?: return null
            RuleConditionDraft(field, op, amountText = formatAmount(low), amountMaxText = formatAmount(high))
        }
        field.usesAmount ->
            RuleConditionDraft(field, op, amountText = formatAmount((value as? JsonPrimitive)?.longOrNull ?: return null))
        field.usesIds && op in setOf("oneOf", "notOneOf") -> {
            val ids = (value as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull ?: return null } ?: return null
            RuleConditionDraft(field, op, ids = ids)
        }
        field.usesIds -> {
            val id = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
            RuleConditionDraft(field, op, ids = listOf(id))
        }
        else -> RuleConditionDraft(field, op, text = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null)
    }
}

private fun JsonObject.toActionDraft(): RuleActionDraft? {
    if (this["options"] != null) return null
    val op = (this["op"] as? JsonPrimitive)?.contentOrNull ?: return null
    val field = (this["field"] as? JsonPrimitive)?.contentOrNull
    val kind = RuleActionKind.entries.firstOrNull { it.op == op && it.field == (if (op == "set") field else null) } ?: return null
    val value = this["value"] as? JsonPrimitive ?: return null
    return when {
        kind == RuleActionKind.SET_CLEARED -> RuleActionDraft(kind, cleared = value.booleanOrNull ?: return null)
        kind.usesId -> RuleActionDraft(kind, id = value.takeIf { it.isString }?.contentOrNull ?: return null)
        else -> RuleActionDraft(kind, text = value.takeIf { it.isString }?.contentOrNull ?: return null)
    }
}

/** Returns the request body, or a message explaining what still needs filling in. */
internal fun RuleDraft.toRequestBody(parseAmount: (String) -> Long?): Result<JsonObject> {
    if (conditions.isEmpty()) return Result.failure(IllegalArgumentException("Add at least one condition."))
    if (actions.isEmpty()) return Result.failure(IllegalArgumentException("Add at least one action."))
    conditions.forEach { condition -> condition.problem(parseAmount)?.let { return Result.failure(IllegalArgumentException(it)) } }
    actions.forEach { action -> action.problem()?.let { return Result.failure(IllegalArgumentException(it)) } }
    val setFields = actions.mapNotNull { it.kind.field }
    if (setFields.size != setFields.toSet().size) {
        return Result.failure(IllegalArgumentException("Each field can only be set once in a rule."))
    }
    return Result.success(
        buildJsonObject {
            put("stage", stage.wire)
            put("conditionsOp", if (matchAll) "and" else "or")
            put("conditions", buildJsonArray { conditions.forEach { add(it.toWire(parseAmount)) } })
            put("actions", buildJsonArray { actions.forEach { add(it.toWire()) } })
        },
    )
}

/** Just the finished conditions, so a preview can run before the rule's actions are filled in. */
internal fun RuleDraft.toPreviewBody(parseAmount: (String) -> Long?): JsonObject? {
    val ready = conditions.filter { it.problem(parseAmount) == null }
    if (ready.isEmpty()) return null
    return buildJsonObject {
        put("conditionsOp", if (matchAll) "and" else "or")
        put("conditions", buildJsonArray { ready.forEach { add(it.toWire(parseAmount)) } })
    }
}

private fun RuleConditionDraft.problem(parseAmount: (String) -> Long?): String? {
    val low = signedAmount(amountText, parseAmount)
    val high = signedAmount(amountMaxText, parseAmount)
    return when {
        field.usesAmount && op == "isbetween" ->
            when {
                low == null || high == null -> "Enter both amounts for “${field.label}”."
                low > high -> "Enter the lower amount first for “${field.label}”."
                else -> null
            }
        field.usesAmount -> if (low == null) "Enter an amount for “${field.label}”." else null
        field.usesIds -> if (ids.isEmpty()) "Choose a ${field.label.lowercase()} for this condition." else null
        text.isBlank() -> "Enter text for “${field.label}”."
        op == "matches" && runCatching { Regex(text) }.isFailure -> "Enter a valid pattern for “${field.label}”."
        else -> null
    }
}

internal fun signedAmount(
    text: String,
    parseAmount: (String) -> Long?,
): Long? {
    val trimmed = text.trim()
    val negative = trimmed.startsWith("-") || trimmed.startsWith("−")
    val magnitude = parseAmount(trimmed.trimStart('-', '−').trim()) ?: return null
    return if (negative) -magnitude.absoluteValue else magnitude
}

private fun RuleActionDraft.problem(): String? =
    when {
        kind.usesId && id.isBlank() -> "Choose a value for “${kind.label}”."
        kind in setOf(RuleActionKind.PREPEND_NOTES, RuleActionKind.APPEND_NOTES) && text.isBlank() -> "Enter text for “${kind.label}”."
        else -> null
    }

private fun RuleConditionDraft.toWire(parseAmount: (String) -> Long?): JsonObject =
    buildJsonObject {
        put("field", field.wire)
        put("op", op)
        when {
            field.usesAmount && op == "isbetween" ->
                put(
                    "range",
                    buildJsonObject {
                        put("num1", signedAmount(amountText, parseAmount)!!.signedFor(field))
                        put("num2", signedAmount(amountMaxText, parseAmount)!!.signedFor(field))
                    },
                )
            field.usesAmount -> put("amountMinor", signedAmount(amountText, parseAmount)!!.signedFor(field))
            field.usesIds && op in setOf("oneOf", "notOneOf") -> put("ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
            field.usesIds -> put("text", ids.first())
            else -> put("text", text)
        }
        if (field == RuleConditionField.MONEY_IN) put("inflow", true)
        if (field == RuleConditionField.MONEY_OUT) put("outflow", true)
    }

private fun Long.signedFor(field: RuleConditionField): Long = if (field == RuleConditionField.AMOUNT) this else absoluteValue

private fun RuleActionDraft.toWire(): JsonObject =
    buildJsonObject {
        kind.field?.let { put("field", it) }
        put("op", kind.op)
        when {
            kind == RuleActionKind.SET_CLEARED -> put("cleared", cleared)
            kind.usesId -> put("text", id)
            else -> put("text", text)
        }
    }

/** Keeps the value that still makes sense when the field or operator changes. */
internal fun RuleConditionDraft.withField(next: RuleConditionField): RuleConditionDraft =
    when {
        next == field -> this
        next.usesAmount && field.usesAmount -> copy(field = next)
        else -> RuleConditionDraft(field = next)
    }

internal fun RuleConditionDraft.withOp(next: String): RuleConditionDraft =
    when {
        next == op -> this
        field.usesIds && next in setOf("is", "isNot") -> copy(op = next, ids = ids.take(1))
        else -> copy(op = next)
    }
