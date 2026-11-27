package app.tandemforactual.data.actual

/**
 * `templateToLine` in Actual 26.11's `template-notes.ts`: the note line that parses back into [template], so a template
 * built in Tandem's editor reads the same on the desktop. Errors and templates only the automations editor makes have
 * no line.
 */
fun goalTemplateLine(template: GoalTemplate): String? {
    if (template.type == "goal") return "$GOAL_PREFIX ${jsText(template.amount)}"
    val prefix = if (template.priority == null || template.priority == 0) TEMPLATE_PREFIX else "$TEMPLATE_PREFIX-${template.priority}"
    val limit = template.limit?.let { " ${limitText(it)}" }.orEmpty()
    return when (template.type) {
        "simple" -> (prefix + template.monthly?.let { " ${jsText(it)}" }.orEmpty() + limit).trim()
        "schedule" -> "$prefix schedule${if (template.full) " full" else ""} ${template.name}${adjustmentText(template)}"
        "percentage" -> "$prefix ${jsText(template.percent)}% of ${if (template.previous) "previous " else ""}${template.category}".trim()
        "periodic" ->
            "$prefix ${jsText(
                template.amount,
            )} repeat every ${template.periodCount} ${template.period}s starting ${template.starting}$limit"
        "by", "spend" -> {
            val from = if (template.type == "spend" && template.from != null) " spend from ${template.from}" else ""
            "$prefix ${jsText(template.amount)} by ${template.month}$from${repeatText(template)}"
        }
        "remainder" -> "$prefix remainder${if (template.weight != 1) " ${template.weight}" else ""}$limit"
        "average" -> "$prefix average ${template.numMonths} months${adjustmentText(template)}"
        "copy" -> "$prefix copy from ${template.lookBack} months ago"
        else -> null
    }
}

private fun limitText(limit: GoalTemplateLimit): String {
    val base =
        when (limit.period) {
            "weekly" -> "up to ${jsText(limit.amount)} per week starting ${limit.start}"
            "daily" -> "up to ${jsText(limit.amount)} per day"
            else -> "up to ${jsText(limit.amount)}"
        }
    return if (limit.hold) "$base hold" else base
}

private fun adjustmentText(template: GoalTemplate): String {
    val adjustment = template.adjustment ?: return ""
    val op = if (adjustment >= 0) "increase" else "decrease"
    return " [$op ${jsText(kotlin.math.abs(adjustment))}${if (template.adjustmentType == "percent") "%" else ""}]"
}

private fun repeatText(template: GoalTemplate): String {
    val annual = template.annual ?: return ""
    val repeat = template.repeat?.takeIf { it != 1 }
    val every =
        when {
            annual && repeat != null -> "$repeat years"
            annual -> "year"
            repeat != null -> "$repeat months"
            else -> "month"
        }
    return " repeat every $every"
}

/** [note] with [line] added as its last line. */
fun noteWithTemplateLine(
    note: String,
    line: String,
): String = if (note.isBlank()) line else "${note.trimEnd()}\n$line"

/** [note] without its [index]th template line, counting lines that start (from their first `#`) with a template. */
fun noteWithoutTemplateLine(
    note: String,
    index: Int,
): String {
    var seen = -1
    return note
        .split("\n")
        .filterNot { line ->
            val trimmed = line.substring(maxOf(line.indexOf('#'), 0)).trim()
            (trimmed.startsWith(TEMPLATE_PREFIX) || trimmed.startsWith(GOAL_PREFIX)) && ++seen == index
        }.joinToString("\n")
        .trim()
}

/** How a category stands against its templates this month, as Actual colours it in the budget. */
data class GoalTemplateStatus(
    val templates: List<GoalTemplate>,
    val needed: Long?,
    val goal: Long?,
    val longGoal: Boolean,
    val problems: List<String>,
) {
    /** What is still missing: the budgeted amount below the goal, or for a `#goal` the balance below it. */
    fun shortfall(
        budgeted: Long,
        balance: Long,
    ): Long? = goal?.let { maxOf(0L, it - if (longGoal) balance else budgeted) }
}
