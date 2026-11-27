package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Actual 26.11's `schedule-template.ts`: what a category's `schedule` templates need this month, either paid in the
 * month they fall due or saved up for over the months before.
 */
internal class GoalScheduleTemplates(
    private val budget: GoalTemplateBudget,
    private val category: GoalCategory,
) {
    private class Target(
        val nextDate: String,
        val interval: Int,
        val frequency: String?,
        val months: Int,
        val full: Boolean,
        val target: Long,
    )

    /** `runSchedule`: the amount to add to this priority's budget. */
    fun run(
        templates: List<GoalTemplate>,
        balance: Long,
        lastMonthBalance: Long,
    ): Long {
        val targets = templates.filter { it.type == "schedule" }.mapNotNull(::target)
        val payMonthOf = targets.filter(::isPayMonthOf)
        val sinking = targets.filterNot(::isPayMonthOf).sortedBy { it.nextDate }
        val subMonthly = targets.count(::isSubMonthly)
        val totalPayMonthOf = payMonthOf.filter { it.months == 0 }.sumOf { it.target }
        val needed = sinking.sumOf { it.target } + totalPayMonthOf
        val lastMonthGoal = budget.goal(ActualMonths.add(budget.month, -1), category.id)
        if (balance >= needed || (lastMonthGoal < needed && lastMonthGoal != 0L && balance >= lastMonthGoal && subMonthly > 0)) {
            return jsMathRound(totalPayMonthOf + sinking.sumOf { monthlyBase(it) }).toLong()
        }
        val total = jsMathRound(totalPayMonthOf + sinkingContribution(sinking, lastMonthBalance)).toLong()
        return if (sinking.isEmpty()) total - lastMonthBalance else total
    }

    private fun isSubMonthly(target: Target) =
        (target.frequency == "weekly" && target.interval <= MAX_WEEKLY) || (target.frequency == "daily" && target.interval <= MAX_DAILY)

    private fun isPayMonthOf(target: Target) =
        target.full ||
            ((target.frequency == "monthly" || target.frequency == null) && target.interval == 1 && target.months == 0) ||
            isSubMonthly(target) ||
            budget.tracking

    private fun monthlyBase(target: Target): Double {
        fun over(previous: String) = target.target.toDouble() / (GoalMonths.months(target.nextDate, previous).takeIf { it != 0 } ?: 1)
        return when (target.frequency) {
            "yearly" -> target.target.toDouble() / target.interval / MONTHS_PER_YEAR
            "weekly" -> over(GoalMonths.addDays(target.nextDate, -DAYS_PER_WEEK * target.interval))
            "daily" -> over(GoalMonths.addDays(target.nextDate, -target.interval.toLong()))
            else -> target.target.toDouble() / target.interval
        }
    }

    private fun sinkingContribution(
        sinking: List<Target>,
        lastMonthBalance: Long,
    ): Double {
        var remainder = 0L
        var total = 0.0
        sinking.forEachIndexed { index, schedule ->
            remainder = schedule.target - (if (index == 0) lastMonthBalance else remainder)
            val contribution = if (remainder >= 0) remainder.also { remainder = 0 } else 0L.also { remainder = -remainder }
            total += contribution.toDouble() / (schedule.months + 1)
        }
        return total
    }

    /** `createScheduleList` for one template: null for a schedule that is completed or already past. */
    private fun target(template: GoalTemplate): Target? {
        val row =
            checkNotNull(
                if (template.scheduleId != null) {
                    budget.db.first("SELECT id, rule, completed FROM schedules WHERE id = ? AND tombstone = 0", template.scheduleId)
                } else {
                    budget.db.first("SELECT id, rule, completed FROM schedules WHERE TRIM(name) = ? AND tombstone = 0", template.name)
                },
            )
        val rule =
            ActualRules.load(budget.db.query("SELECT * FROM rules WHERE id = ?", row["rule"]), emptyMap()).firstOrNull()
                ?: throw GoalTemplateException("Schedule ${template.name} has no rule")
        val conditions = rule.getValue("conditions").jsonArray.map { it.jsonObject }
        val date = conditions.firstOrNull { it.text("field") == "date" && it.text("op") in setOf("is", "isapprox") }?.get("value")
        val amount = scheduleAmount(template, conditions)
        val nextDate =
            when (date) {
                is JsonObject -> ActualRecurrence.nextDate(RecurConfig.parse(date), GoalMonths.day(budget.month))?.toString()
                else -> (date as? JsonPrimitive)?.contentOrNull
            } ?: return null
        val months = GoalMonths.months(nextDate, budget.month)
        if (months < 0 || truthy(row["completed"])) return null
        val sign = if (category.isIncome) 1 else -1
        var target = sign * ruleAmount(rule.text("id").orEmpty(), amount, nextDate)
        if (date is JsonObject) target = monthlyTarget(RecurConfig.parse(date), months, target)
        val frequency = (date as? JsonObject)?.text("frequency")
        val interval = ((date as? JsonObject)?.get("interval") as? JsonPrimitive)?.intOrNull?.takeIf { it != 0 } ?: 1
        return Target(nextDate, interval, frequency, months, template.full, target)
    }

    private fun scheduleAmount(
        template: GoalTemplate,
        conditions: List<JsonObject>,
    ): Long {
        val condition =
            conditions.firstOrNull { it.text("field") == "amount" && it.text("op") in setOf("is", "isapprox", "isbetween") }
                ?: throw GoalTemplateException("Schedule ${template.name} has no amount")
        val value = condition["value"]
        var amount =
            if (value is JsonObject) {
                (value.number("num1") + value.number("num2")) / 2
            } else {
                (value as? JsonPrimitive)?.doubleOrNull ?: 0.0
            }
        val adjustment = template.adjustment
        if (adjustment != null && template.adjustmentType == "percent") amount *= 1 + adjustment / PERCENT
        if (adjustment != null && template.adjustmentType == "fixed") amount += (if (amount < 0) -1 else 1) * budget.integer(adjustment)
        return jsMathRound(amount).toLong()
    }

    /** The schedule's rule run on a stand-in transaction; a split for this category counts only its own lines. */
    @Suppress("UNCHECKED_CAST")
    private fun ruleAmount(
        ruleId: String,
        amount: Long,
        date: String,
    ): Long {
        val transaction =
            linkedMapOf<String, Any?>("amount" to amount, "category" to category.id, "subtransactions" to emptyList<Any>(), "date" to date)
        val result = ActualRuleEngine(budget.db) { error("A goal template never creates payees") }.execRule(ruleId, transaction)
        val lines = (result?.get("subtransactions") as? List<Map<String, Any?>>).orEmpty().filter { it["category"] == category.id }
        if (lines.isNotEmpty()) return lines.sumOf { (it["amount"] as Number).toLong() }
        return (result?.get("amount") as? Number)?.toLong() ?: amount
    }

    /** A repeating schedule's total for every occurrence that falls before the month after its next one. */
    private fun monthlyTarget(
        config: RecurConfig,
        months: Int,
        target: Long,
    ): Long {
        val nextMonth = ActualMonths.add(budget.month, months + 1)
        var base = ActualRecurrence.nextDate(config, GoalMonths.day(budget.month), noSkipWeekend = true)
        var monthly = 0L
        while (base != null && ActualRecurrence.nextDate(config, base).toString() < nextMonth) {
            monthly -= target
            val current = base
            base = ActualRecurrence.nextDate(config, current.plus(1), noSkipWeekend = true)?.takeIf { it != current }
        }
        return -monthly
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private companion object {
        const val MAX_WEEKLY = 4
        const val MAX_DAILY = 31
        const val MONTHS_PER_YEAR = 12
        const val DAYS_PER_WEEK = 7L
        const val PERCENT = 100.0
    }
}
