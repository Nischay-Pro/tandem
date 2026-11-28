package app.tandemforactual.ui.feature.budget

import app.tandemforactual.data.actual.GoalTemplate
import app.tandemforactual.data.actual.GoalTemplateLimit
import app.tandemforactual.data.actual.goalTemplateLine
import app.tandemforactual.data.actual.goalTemplatesFromNote
import kotlin.math.abs
import kotlin.math.roundToLong

/** The kinds of template the editor builds, each written in Actual's note syntax. */
internal enum class GoalTemplateKind(
    val label: String,
) {
    FIXED("Fixed amount"),
    BY_DATE("Save by a date"),
    PERCENT("Percent of income"),
    AVERAGE("Average spending"),
    SCHEDULE("Schedule"),
    CAP("Up to a cap"),
    REMAINDER("Remainder"),
    GOAL("Balance goal"),
}

/** What the editor's fields hold; [template] is null until the fields describe a whole template. */
internal data class GoalTemplateDraft(
    val kind: GoalTemplateKind = GoalTemplateKind.FIXED,
    val amount: String = "",
    val cap: String = "",
    val month: String = "",
    val repeatYearly: Boolean = false,
    val percent: String = "",
    val source: String = "all income",
    val previous: Boolean = false,
    val months: Int = 3,
    val adjustPercent: String = "",
    val schedule: String = "",
    val full: Boolean = false,
    val period: String = "monthly",
    val weekStart: String = "",
    val hold: Boolean = false,
    val weight: String = "1",
    val priority: String = "",
) {
    fun template(): GoalTemplate? {
        val priority =
            priority
                .trim()
                .ifEmpty { "0" }
                .toIntOrNull()
                ?.takeIf { it >= 0 } ?: return null
        return when (kind) {
            GoalTemplateKind.FIXED -> {
                GoalTemplate(
                    "simple",
                    priority = priority,
                    monthly = number(amount) ?: return null,
                    limit =
                        cap.ifBlank { null }?.let {
                            limit(it)
                                ?: return null
                        },
                )
            }

            GoalTemplateKind.BY_DATE -> {
                GoalTemplate(
                    "by",
                    priority = priority,
                    amount = number(amount) ?: return null,
                    month = month.trim(),
                    annual = if (repeatYearly) true else null,
                )
            }

            GoalTemplateKind.PERCENT -> {
                GoalTemplate(
                    "percentage",
                    priority = priority,
                    percent = number(percent) ?: return null,
                    previous = previous,
                    category = source,
                )
            }

            GoalTemplateKind.AVERAGE -> {
                val adjustment = adjustPercent.ifBlank { null }?.let { signed(it) ?: return null }
                GoalTemplate(
                    "average",
                    priority = priority,
                    numMonths = months,
                    adjustment = adjustment,
                    adjustmentType = adjustment?.let { "percent" },
                )
            }

            GoalTemplateKind.SCHEDULE -> {
                GoalTemplate("schedule", priority = priority, name = schedule.ifBlank { return null }, full = full)
            }

            GoalTemplateKind.CAP -> {
                GoalTemplate("simple", priority = priority, limit = limit(cap) ?: return null)
            }

            GoalTemplateKind.REMAINDER -> {
                GoalTemplate(
                    "remainder",
                    priority = null,
                    weight =
                        weight.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null,
                )
            }

            GoalTemplateKind.GOAL -> {
                GoalTemplate("goal", directive = "goal", priority = null, amount = number(amount) ?: return null)
            }
        }
    }

    private fun limit(text: String): GoalTemplateLimit? =
        GoalTemplateLimit(number(text) ?: return null, hold, period, weekStart.trim().takeIf { period == "weekly" })

    /** The note line for this draft, only when Actual's grammar reads it back as the same kind of template. */
    fun line(): String? {
        val line = template()?.let(::goalTemplateLine) ?: return null
        return line.takeIf { goalTemplatesFromNote(it).singleOrNull()?.type?.let { type -> type != "error" } == true }
    }
}

private fun number(text: String): Double? =
    text
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it >= 0 && it.isFinite() }

private fun signed(text: String): Double? =
    text
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it.isFinite() }

/** A template in plain words; [money] formats an amount written in the note, in the budget's major units. */
internal fun describeGoalTemplate(
    template: GoalTemplate,
    money: (Double) -> String,
): String {
    val text =
        when (template.type) {
            "simple" -> simpleWords(template, money)
            "periodic" -> "${money(template.amount)} every ${countWords(template.periodCount, template.period)} from ${template.starting}"
            "by" -> "Save ${money(template.amount)} by ${template.month}${repeatWords(template)}"
            "spend" -> "Save ${money(template.amount)} by ${template.month}, spending from ${template.from}${repeatWords(template)}"
            "percentage" -> "${plain(template.percent)}% of ${if (template.previous) "last month’s " else ""}${template.category}"
            "schedule" -> "Cover “${template.name}”${if (template.full) " in full" else ""}${adjustmentWords(template, money)}"
            "average" -> "Average of the last ${template.numMonths} months${adjustmentWords(template, money)}"
            "copy" -> "Same as ${countWords(template.lookBack, "month")} ago"
            "remainder" -> "Share of what’s left${if (template.weight != 1) " ×${template.weight}" else ""}"
            "goal" -> "Keep a balance of ${money(template.amount)}"
            "error" -> "Can’t read “${template.line.trim()}”"
            else -> "Set in Actual"
        }
    val priority = template.priority?.takeIf { it > 0 && template.type != "error" }
    return if (priority != null) "$text · priority $priority" else text
}

private fun simpleWords(
    template: GoalTemplate,
    money: (Double) -> String,
): String {
    val limit = template.limit
    val cap =
        limit?.let {
            val per =
                when (it.period) {
                    "weekly" -> " a week"
                    "daily" -> " a day"
                    else -> ""
                }
            "${money(it.amount)}$per${if (it.hold) ", keeping any extra" else ""}"
        }
    val monthly = template.monthly
    return when {
        monthly != null && cap != null -> "${money(monthly)} a month, up to $cap"
        monthly != null -> "${money(monthly)} a month"
        else -> "Top up to $cap"
    }
}

private fun countWords(
    count: Int,
    unit: String,
): String = if (count == 1) unit else "$count ${unit}s"

private fun repeatWords(template: GoalTemplate): String {
    val annual = template.annual ?: return ""
    val repeat = template.repeat ?: 1
    return " · repeats every ${countWords(repeat, if (annual) "year" else "month")}"
}

private fun adjustmentWords(
    template: GoalTemplate,
    money: (Double) -> String,
): String {
    val adjustment = template.adjustment ?: return ""
    val change = if (template.adjustmentType == "percent") "${plain(abs(adjustment))}%" else money(abs(adjustment))
    return if (adjustment >= 0) " plus $change" else " minus $change"
}

private fun plain(value: Double): String = if (value == value.roundToLong().toDouble()) value.roundToLong().toString() else value.toString()
