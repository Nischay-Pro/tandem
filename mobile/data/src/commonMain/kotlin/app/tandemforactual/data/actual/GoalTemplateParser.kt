package app.tandemforactual.data.actual

/**
 * Actual 26.11's `goal-template.pegjs`, by hand. Like the generated PEG parser, a choice keeps its first match and
 * never retries another branch when what follows fails, and a line is valid only when the match uses all of it.
 */
internal class GoalTemplateParser private constructor(
    private val input: String,
) {
    private var pos = 0

    companion object {
        fun parse(line: String): GoalTemplate {
            val parser = GoalTemplateParser(line)
            val result = parser.expr()
            if (result == null || parser.pos != line.length) throw GoalTemplateSyntaxException("Invalid template: $line")
            return result
        }
    }

    private fun expr(): GoalTemplate? =
        sequenceOf(::percentage, ::periodic, ::byOrSpend, ::simple, ::limitOnly, ::schedule, ::remainder, ::average, ::copy, ::goal)
            .firstNotNullOfOrNull { rule ->
                pos = 0
                rule()
            }

    private fun <T> optional(rule: () -> T?): T? {
        val start = pos
        return rule() ?: null.also { pos = start }
    }

    private fun literal(
        text: String,
        ignoreCase: Boolean = true,
    ): Boolean {
        if (!input.regionMatches(pos, text, 0, text.length, ignoreCase)) return false
        pos += text.length
        return true
    }

    private fun spaces(): String {
        val start = pos
        while (pos < input.length && (input[pos] == ' ' || input[pos] == '\t')) pos++
        return input.substring(start, pos)
    }

    private fun digits(min: Int = 1): String? {
        val start = pos
        while (pos < input.length && input[pos].isAsciiDigit()) pos++
        return input.substring(start, pos).takeIf { it.length >= min } ?: null.also { pos = start }
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun positive(): Int? {
        if (pos >= input.length || input[pos] !in '1'..'9') return null
        return digits()!!.toIntOrNull() ?: Int.MAX_VALUE
    }

    /** `d+ ('.' d*)?` */
    private fun decimal(): String? {
        val start = pos
        digits() ?: return null
        if (literal(".")) digits(0)
        return input.substring(start, pos)
    }

    private fun template(): Int? {
        if (!literal(TEMPLATE_PREFIX, ignoreCase = false)) return null
        return optional { if (literal("-")) digits() else null }?.let { it.toIntOrNull() ?: Int.MAX_VALUE } ?: 0
    }

    private fun amount(): Double? =
        optional {
            if (pos < input.length && input[pos].category == CharCategory.CURRENCY_SYMBOL) pos++
            spaces()
            val start = pos
            literal("-")
            if (digits() == null) return@optional null
            if (literal(".") && digits() != null) pos = minOf(pos, input.indexOf('.', start) + 3)
            input.substring(start, pos).toDouble()
        }

    private fun percent(): String? =
        optional {
            val value = decimal() ?: return@optional null
            spaces()
            if (literal("%")) value else null
        }

    private fun fixedDigits(count: Int): Boolean {
        if (pos + count > input.length || (0 until count).any { !input[pos + it].isAsciiDigit() }) return false
        pos += count
        return true
    }

    private fun month(): String? =
        optional {
            val start = pos
            if (fixedDigits(4) && literal("-") && fixedDigits(2)) input.substring(start, pos) else null
        }

    private fun date(): String? =
        optional {
            val start = pos
            if (month() != null && literal("-") && fixedDigits(2)) input.substring(start, pos) else null
        }

    private fun limit(): GoalTemplateLimit? = optional { weeklyLimit() } ?: optional { dailyLimit() } ?: optional { monthlyLimit() }

    private fun upToAmount(): Double? {
        spaces()
        if (!literal("up")) return null
        spaces()
        if (!literal("to")) return null
        spaces()
        return amount()
    }

    private fun hold(): Boolean = optional { if (literal("hold")) true else null } ?: false

    private fun weeklyLimit(): GoalTemplateLimit? {
        val amount = upToAmount() ?: return null
        spaces()
        if (!literal("per week starting")) return null
        spaces()
        val start = date() ?: return null
        spaces()
        return GoalTemplateLimit(amount, hold(), "weekly", start)
    }

    private fun dailyLimit(): GoalTemplateLimit? {
        val amount = upToAmount() ?: return null
        spaces()
        if (!literal("per day")) return null
        spaces()
        return GoalTemplateLimit(amount, hold(), "daily")
    }

    private fun monthlyLimit(): GoalTemplateLimit? {
        val amount = upToAmount() ?: return null
        spaces()
        return GoalTemplateLimit(amount, hold())
    }

    private fun percentage(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        val percent = percent() ?: return null
        spaces()
        if (!literal("of")) return null
        spaces()
        val previous = optional { if (literal("previous")) spaces() else null } != null
        val start = pos
        while (pos < input.length && input[pos] !in "\r\n\t") pos++
        if (pos == start) return null
        return GoalTemplate(
            "percentage",
            priority = priority,
            percent = percent.toDouble(),
            previous = previous,
            category = input.substring(start, pos),
        )
    }

    private fun periodic(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        val amount = amount() ?: return null
        spaces()
        if (!repeatEvery()) return null
        spaces()
        val (period, count) = periodCount() ?: return null
        spaces()
        if (!literal("starting")) return null
        spaces()
        val starting = date() ?: return null
        val limit = optional { limit() }
        return GoalTemplate(
            "periodic",
            priority = priority,
            amount = amount,
            period = period,
            periodCount = count,
            starting = starting,
            limit = limit,
        )
    }

    private fun repeatEvery(): Boolean {
        if (!literal("repeat")) return false
        spaces()
        return literal("every")
    }

    private fun periodCount(): Pair<String, Int>? =
        optional { if (literal("day")) "day" to 1 else null }
            ?: optional { counted("days", "day", trailing = true) }
            ?: optional { if (literal("week")) ("week" to 1).also { spaces() } else null }
            ?: optional { counted("weeks", "week", trailing = false) }
            ?: optional { counted("months", "month", trailing = true) }
            ?: optional { if (literal("year")) ("year" to 1).also { spaces() } else null }
            ?: optional { counted("years", "year", trailing = true) }

    private fun counted(
        word: String,
        period: String,
        trailing: Boolean,
    ): Pair<String, Int>? {
        val count = positive() ?: return null
        spaces()
        if (!literal(word)) return null
        if (trailing) spaces()
        return period to count
    }

    private fun byOrSpend(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        val amount = amount() ?: return null
        spaces()
        if (!literal("by")) return null
        spaces()
        val month = month() ?: return null
        val from = optional { spendFrom() }
        val repeat = optional { repeatInterval() }
        return GoalTemplate(
            if (from != null) "spend" else "by",
            priority = priority,
            amount = amount,
            month = month,
            annual = repeat?.first,
            repeat = repeat?.second,
            from = from,
        )
    }

    private fun spendFrom(): String? {
        spaces()
        if (!literal("spend")) return null
        spaces()
        if (!literal("from")) return null
        spaces()
        return month()
    }

    private fun repeatInterval(): Pair<Boolean, Int?>? {
        spaces()
        if (!repeatEvery()) return null
        spaces()
        return optional { if (literal("month")) false to null else null }
            ?: optional { positive()?.let { n -> spaces().let { if (literal("months")) false to n else null } } }
            ?: optional { if (literal("year")) true to null else null }
            ?: optional { positive()?.let { n -> spaces().let { if (literal("years")) true to n else null } } }
    }

    private fun simple(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        val monthly = amount() ?: return null
        return GoalTemplate("simple", priority = priority, monthly = monthly, limit = optional { limit() })
    }

    private fun limitOnly(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        return GoalTemplate("simple", priority = priority, limit = limit() ?: return null)
    }

    private fun schedule(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        if (!literal("schedule")) return null
        spaces()
        val full = optional { if (literal("full")) spaces() else null } != null
        val name = scheduleName() ?: return null
        val adjustment = optional { modifiers() }
        return GoalTemplate(
            "schedule",
            priority = priority,
            name = name,
            full = full,
            adjustment = adjustment?.first,
            adjustmentType = adjustment?.second,
        )
    }

    private fun startsModifier(at: Int): Boolean {
        var index = at
        while (index < input.length && (input[index] == ' ' || input[index] == '\t')) index++
        return input.startsWith("[increase", index, ignoreCase = true) || input.startsWith("[decrease", index, ignoreCase = true)
    }

    private fun scheduleName(): String? {
        val start = pos
        if (pos >= input.length || input[pos] in " \t\r\n" || startsModifier(pos)) return null
        pos++
        while (pos < input.length && !startsModifier(pos) && input[pos] !in "\r\n") pos++
        return input.substring(start, pos).trim()
    }

    private fun modifiers(): Pair<Double, String>? {
        spaces()
        if (!literal("[")) return null
        val sign =
            if (literal("increase")) {
                1
            } else if (literal("decrease")) {
                -1
            } else {
                return null
            }
        spaces()
        val value = decimal() ?: return null
        val save = pos
        spaces()
        val type = if (literal("%")) "percent" else "fixed".also { pos = save }
        if (!literal("]")) return null
        return sign * value.toDouble() to type
    }

    private fun remainder(): GoalTemplate? {
        template() ?: return null
        spaces()
        if (!literal("remainder")) return null
        spaces()
        val weight = optional { positive() } ?: 1
        return GoalTemplate("remainder", priority = null, weight = weight, limit = optional { limit() })
    }

    private fun average(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        if (!literal("average")) return null
        spaces()
        val months = positive() ?: return null
        spaces()
        literal("months")
        val adjustment = optional { modifiers() }
        return GoalTemplate(
            "average",
            priority = priority,
            numMonths = months,
            adjustment = adjustment?.first,
            adjustmentType = adjustment?.second,
        )
    }

    private fun copy(): GoalTemplate? {
        val priority = template() ?: return null
        spaces()
        if (!literal("copy from")) return null
        spaces()
        val lookBack = positive() ?: return null
        spaces()
        if (!literal("months ago")) return null
        return GoalTemplate("copy", priority = priority, lookBack = lookBack, limit = optional { limit() })
    }

    private fun goal(): GoalTemplate? {
        if (!literal(GOAL_PREFIX)) return null
        return GoalTemplate("goal", directive = "goal", priority = null, amount = amount() ?: return null)
    }
}
