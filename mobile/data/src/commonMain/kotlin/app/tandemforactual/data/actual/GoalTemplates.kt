package app.tandemforactual.data.actual

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/** An `up to` cap: [period] is `monthly`, `weekly` or `daily`; a weekly cap counts weeks from [start]. */
data class GoalTemplateLimit(
    val amount: Double,
    val hold: Boolean = false,
    val period: String = "monthly",
    val start: String? = null,
)

/**
 * One parsed `#template` or `#goal` line, shaped like Actual 26.11's `Template` union (`types/models/templates.ts`).
 * Amounts are in major units, as written in the note.
 */
data class GoalTemplate(
    val type: String,
    val directive: String = "template",
    val priority: Int? = 0,
    val amount: Double = 0.0,
    val monthly: Double? = null,
    val limit: GoalTemplateLimit? = null,
    val percent: Double = 0.0,
    val previous: Boolean = false,
    val category: String = "",
    val period: String = "month",
    val periodCount: Int = 0,
    val starting: String = "",
    val month: String = "",
    val annual: Boolean? = null,
    val repeat: Int? = null,
    val from: String? = null,
    val name: String? = null,
    val scheduleId: String? = null,
    val full: Boolean = false,
    val adjustment: Double? = null,
    val adjustmentType: String? = null,
    val numMonths: Int = 0,
    val lookBack: Int = 0,
    val weight: Int = 1,
    val line: String = "",
    val error: String = "",
    val description: String? = null,
) {
    companion object {
        /** A template saved by Actual's automations editor in `categories.goal_def`. */
        fun fromJson(json: JsonObject): GoalTemplate {
            fun text(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull

            fun number(key: String) = (json[key] as? JsonPrimitive)?.doubleOrNull

            fun limit(source: JsonObject?) =
                source?.let {
                    GoalTemplateLimit(
                        (it["amount"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                        (it["hold"] as? JsonPrimitive)?.booleanOrNull == true,
                        (it["period"] as? JsonPrimitive)?.contentOrNull ?: "monthly",
                        (it["start"] as? JsonPrimitive)?.contentOrNull,
                    )
                }
            val type = text("type") ?: "error"
            val period = json["period"] as? JsonObject
            return GoalTemplate(
                type = type,
                directive = text("directive") ?: "template",
                priority = number("priority")?.toInt(),
                amount = number("amount") ?: 0.0,
                monthly = number("monthly"),
                limit = if (type == "limit") limit(json) else limit(json["limit"] as? JsonObject),
                percent = number("percent") ?: 0.0,
                previous = (json["previous"] as? JsonPrimitive)?.booleanOrNull == true,
                category = text("category").orEmpty(),
                period = (period?.get("period") as? JsonPrimitive)?.contentOrNull ?: "month",
                periodCount = (period?.get("amount") as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0,
                starting = text("starting").orEmpty(),
                month = text("month").orEmpty(),
                annual = (json["annual"] as? JsonPrimitive)?.booleanOrNull,
                repeat = number("repeat")?.toInt(),
                from = text("from"),
                name = text("name"),
                scheduleId = text("scheduleId"),
                full = (json["full"] as? JsonPrimitive)?.booleanOrNull == true,
                adjustment = number("adjustment"),
                adjustmentType = text("adjustmentType"),
                numMonths = number("numMonths")?.toInt() ?: 0,
                lookBack = number("lookBack")?.toInt() ?: 0,
                weight = number("weight")?.toInt() ?: 1,
            )
        }

        fun fromGoalDef(goalDef: String?): List<GoalTemplate>? =
            goalDef
                ?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
                ?.map { fromJson(it.jsonObject) }
    }
}

/** A template line Actual's grammar rejects. */
internal class GoalTemplateSyntaxException(
    message: String,
) : IllegalArgumentException(message)

internal const val TEMPLATE_PREFIX = "#template"
internal const val GOAL_PREFIX = "#goal"
private const val CLEANUP_PREFIX = "#cleanup"

/**
 * `getCategoriesWithTemplates` in Actual's `template-notes.ts`: every line whose text from its first `#` starts with
 * `#template` or `#goal` becomes a template, and lines that don't parse become `error` templates. Plain lines just
 * above a template are kept as its description.
 */
fun goalTemplatesFromNote(note: String): List<GoalTemplate> {
    val parsed = mutableListOf<GoalTemplate>()
    var description = mutableListOf<String>()
    note.split("\n").forEach { line ->
        val trimmed = line.substring(maxOf(line.indexOf('#'), 0)).trim()
        if (!trimmed.startsWith(TEMPLATE_PREFIX) && !trimmed.startsWith(GOAL_PREFIX)) {
            if (line.trim().isEmpty() ||
                trimmed.startsWith(CLEANUP_PREFIX)
            ) {
                description = mutableListOf()
            } else {
                description.add(line.trimEnd())
            }
            return@forEach
        }
        val text = description.takeIf { it.isNotEmpty() }?.joinToString("\n")
        description = mutableListOf()
        val template =
            try {
                GoalTemplateParser.parse(trimmed).also(::checkAdjustment)
            } catch (error: GoalTemplateSyntaxException) {
                GoalTemplate("error", directive = "error", priority = null, line = line, error = error.message.orEmpty())
            }
        parsed += template.copy(description = text)
    }
    return parsed
}

private fun checkAdjustment(template: GoalTemplate) {
    val adjustment = template.adjustment
    if (template.adjustmentType == "percent" && adjustment != null && (adjustment <= -100 || adjustment > 1000)) {
        throw GoalTemplateSyntaxException(
            "Invalid adjustment percentage (${jsText(adjustment)}%). Must be between -100% and 1000%",
        )
    }
}

/** A number as JavaScript's `String(n)` writes it, for the amounts that appear in messages and notes. */
internal fun jsText(value: Double): String =
    if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
