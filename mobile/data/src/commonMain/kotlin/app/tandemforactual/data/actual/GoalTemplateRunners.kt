package app.tandemforactual.data.actual

import kotlin.math.max

/** The per-line processors of Actual's `CategoryTemplateContext` (`runSimple`, `runPeriodic`, `runBy`, …). */
internal class GoalTemplateRunners(
    private val budget: GoalTemplateBudget,
    private val category: GoalCategory,
    private val fromLastMonth: Long,
    private val limitAmount: Long,
) {
    fun run(
        template: GoalTemplate,
        availStart: Long,
    ): Long =
        when (template.type) {
            "simple" -> template.monthly?.let(budget::integer) ?: (limitAmount - fromLastMonth)
            "refill" -> limitAmount - fromLastMonth
            "copy" -> budget.value(ActualMonths.add(budget.month, -template.lookBack), "budget-${category.id}")
            "periodic" -> periodic(template)
            "spend" -> spend(template)
            "percentage" -> percentage(template, availStart)
            "average" -> average(template)
            else -> 0L
        }

    private fun periodic(template: GoalTemplate): Long {
        val amount = budget.integer(template.amount)
        val count = template.periodCount.toLong()
        val shift: (String) -> String =
            when (template.period) {
                "day" -> { date -> GoalMonths.addDays(date, count) }
                "week" -> { date -> GoalMonths.addDays(date, count * DAYS_PER_WEEK) }
                "month" -> { date -> ActualMonths.add(date, count.toInt()) }
                "year" -> { date -> ActualMonths.add(date, count.toInt() * MONTHS_PER_YEAR) }
                else -> throw GoalTemplateException("Unrecognized periodic period: ${template.period}")
            }
        var date = template.starting.ifEmpty { "${budget.month}-01" }
        while (budget.month > date) date = shift(date)
        if (GoalMonths.months(budget.month, date) < 0) return 0
        val nextMonth = ActualMonths.add(budget.month, 1)
        var toBudget = 0L
        while (date < nextMonth) {
            toBudget += amount
            date = shift(date)
        }
        return toBudget
    }

    private fun repeatMonths(template: GoalTemplate): Int? =
        (if (template.annual == true) (template.repeat ?: 1) * MONTHS_PER_YEAR else template.repeat)?.takeIf { it != 0 }

    private fun spend(template: GoalTemplate): Long {
        var fromMonth = template.from.orEmpty()
        var toMonth = template.month
        val repeat = repeatMonths(template)
        while (repeat != null && GoalMonths.months(toMonth, budget.month) < 0) {
            toMonth = ActualMonths.add(toMonth, repeat)
            fromMonth = ActualMonths.add(fromMonth, repeat)
        }
        var alreadyBudgeted = fromLastMonth
        var cursor = fromMonth
        var first = true
        while (GoalMonths.months(budget.month, cursor) > 0) {
            if (first) {
                alreadyBudgeted = budget.value(cursor, "leftover-${category.id}") - budget.value(cursor, "sum-amount-${category.id}")
                first = false
            } else {
                alreadyBudgeted += budget.value(cursor, "budget-${category.id}")
            }
            cursor = ActualMonths.add(cursor, 1)
        }
        val numMonths = GoalMonths.months(toMonth, budget.month)
        if (numMonths < 0) return 0
        return jsMathRound((budget.integer(template.amount) - alreadyBudgeted).toDouble() / (numMonths + 1)).toLong()
    }

    private fun percentage(
        template: GoalTemplate,
        availStart: Long,
    ): Long {
        val month = if (template.previous) ActualMonths.add(budget.month, -1) else budget.month
        val name = template.category.lowercase()
        val income =
            when (name) {
                "all income" -> budget.value(month, "total-income")
                "available funds" -> availStart
                else -> {
                    val source = incomeCategories(budget.db).first { it.id == template.category || it.name.lowercase() == name }
                    budget.value(month, "sum-amount-${source.id}")
                }
            }
        return max(0L, jsMathRound(income * (template.percent / PERCENT)).toLong())
    }

    private fun average(template: GoalTemplate): Long {
        var average = kotlin.math.abs(categoryAverage(template.numMonths).toDouble())
        val adjustment = template.adjustment
        if (adjustment != null && template.adjustmentType == "percent") average *= 1 + adjustment / PERCENT
        if (adjustment != null && template.adjustmentType == "fixed") average += budget.integer(adjustment)
        return jsMathRound(average).toLong()
    }

    /** `getCategoryAverage`: up to [maxMonths] months back from last month, not before the category's first activity. */
    private fun categoryAverage(maxMonths: Int): Long {
        val previous = ActualMonths.add(budget.month, -1)
        val first = if (previous >= budget.currentMonth) ActualMonths.add(budget.currentMonth, -1) else previous
        val end = ActualMonths.dbMonth(first)
        val activity =
            budget.db
                .first(
                    "SELECT MIN(month) AS month FROM (SELECT month FROM ${budget.table} WHERE category = ? AND month <= ? " +
                        "UNION ALL SELECT CAST(t.date / 100 AS INTEGER) AS month FROM v_transactions_internal_alive t " +
                        "LEFT JOIN accounts a ON a.id = t.account WHERE t.category = ? AND CAST(t.date / 100 AS INTEGER) <= ? " +
                        "AND a.offbudget = 0)",
                    category.id,
                    end,
                    category.id,
                    end,
                )?.get("month")
                ?.let { ActualMonths.fromDateInt((it as Number).toLong() * 100) }
        val months = mutableListOf<String>()
        var month = first
        while (months.size < maxMonths && (activity == null || month >= activity)) {
            months += month
            month = ActualMonths.add(month, -1)
        }
        if (months.isEmpty()) return 0
        return jsMathRound(months.sumOf { budget.value(it, "sum-amount-${category.id}") }.toDouble() / months.size).toLong()
    }

    /** `runBy`: every `by` template at once, spread over the nearest target month. */
    fun by(templates: List<GoalTemplate>): Long {
        val windows =
            templates.map { template ->
                var target = template.month
                val period = repeatMonths(template)
                while (period != null && GoalMonths.months(target, budget.month) < 0) target = ActualMonths.add(target, period)
                GoalMonths.months(target, budget.month) to period
            }
        val shortest = windows.minOf { it.first }
        val needed =
            templates.zip(windows).sumOf { (template, window) ->
                val (months, period) = window
                val amount = budget.integer(template.amount).toDouble()
                when {
                    months > shortest && period != null -> jsMathRound(amount / period * (period - months + shortest)).toLong()
                    months > shortest -> jsMathRound(amount / (months + 1) * (shortest + 1)).toLong()
                    else -> amount.toLong()
                }
            }
        return jsMathRound((needed - fromLastMonth).toDouble() / (shortest + 1)).toLong()
    }

    private companion object {
        const val DAYS_PER_WEEK = 7L
        const val MONTHS_PER_YEAR = 12
        const val PERCENT = 100.0
    }
}

internal fun incomeCategories(db: ActualDatabase): List<GoalCategory> =
    db
        .query("SELECT * FROM categories WHERE tombstone = 0 ORDER BY sort_order, id")
        .filter { truthy(it["is_income"]) }
        .map { GoalCategory(it["id"] as String, it["name"] as String, true) }
