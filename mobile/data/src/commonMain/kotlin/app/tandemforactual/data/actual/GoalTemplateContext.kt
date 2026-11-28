package app.tandemforactual.data.actual

import kotlin.math.max
import kotlin.math.pow

/** A template a category can't run; Actual reports it as `<category>: <message>`. */
internal class GoalTemplateException(
    message: String,
) : IllegalStateException(message)

internal data class GoalCategory(
    val id: String,
    val name: String,
    val isIncome: Boolean,
)

/** What a template run reads: the budget's sheet cells, its preferences, and the month being filled. */
internal class GoalTemplateBudget(
    val db: ActualDatabase,
    val month: String,
    val currentMonth: String,
    val tracking: Boolean,
    private val cells: Map<String, Map<String, Any?>>,
) {
    private val preferences = db.query("SELECT id, value FROM preferences").associate { it["id"] as String to it["value"] as String? }
    val decimals = ACTUAL_CURRENCIES[preferences["defaultCurrencyCode"]]?.decimalPlaces ?: 2
    val hideDecimal = preferences["hideFraction"] == "true"
    val table = if (tracking) "reflect_budgets" else "zero_budgets"

    fun value(
        month: String,
        cell: String,
    ): Long = (cells[month]?.get(cell) as? Number)?.toLong() ?: 0L

    fun flag(
        month: String,
        cell: String,
    ): Boolean = cells[month]?.get(cell) == true

    fun goal(
        month: String,
        category: String,
    ): Long =
        (
            db.first("SELECT goal FROM $table WHERE month = ? AND category = ?", ActualMonths.dbMonth(month), category)?.get("goal")
                as? Number
        )?.toLong() ?: 0L

    /** `amountToInteger` in the budget's currency. */
    fun integer(amount: Double): Long = jsMathRound(amount * 10.0.pow(decimals)).toLong()

    /** `removeFraction`: whole currency units. */
    fun whole(amount: Long): Long = integer(jsMathRound(amount / 10.0.pow(decimals)))
}

/**
 * Actual 26.11's `CategoryTemplateContext`: one category's templates, run priority by priority against the money left
 * to budget, then its remainder share, an `up to` cap and its goal.
 */
internal class GoalTemplateContext(
    val category: GoalCategory,
    private val budget: GoalTemplateBudget,
    allTemplates: List<GoalTemplate>,
    private val fromLastMonth: Long,
    private val previouslyBudgeted: Long,
) {
    private val templates = allTemplates.filter { it.directive == "template" && it.type != "remainder" && it.type != "limit" }
    private val remainders = allTemplates.filter { it.directive == "template" && it.type == "remainder" }
    private val goals = allTemplates.filter { it.directive == "goal" && it.type == "goal" }
    val priorities = templates.mapNotNull { it.priority }.toSet()
    private val remainderWeight = remainders.sumOf { it.weight }
    private var toBudgetAmount = 0L
    private var fullAmount: Long? = null
    private var limitMet = false
    var limitExcess = 0L
        private set
    private var limitAmount = 0L
    private var limitCheck = false

    init {
        checkLimit(allTemplates)
        if (templates.count { it.type == "spend" } > 1) throw GoalTemplateException("Only one spend template is allowed per category")
        if (goals.size > 1) throw GoalTemplateException("Only one #goal is allowed per category")
    }

    val isGoalOnly get() = templates.isEmpty() && remainders.isEmpty() && goals.isNotEmpty()

    fun hasRemainder() = remainderWeight > 0 && !limitMet

    fun remainderWeight() = remainderWeight

    private fun checkLimit(all: List<GoalTemplate>) {
        all.filter { it.type in setOf("simple", "periodic", "limit", "remainder") }.forEach { template ->
            val limit = template.limit ?: return@forEach
            if (limitCheck) throw GoalTemplateException("Only one `up to` allowed per category")
            limitAmount =
                when (limit.period) {
                    "daily" -> budget.integer(limit.amount) * GoalMonths.days(budget.month)
                    "weekly" -> weeklyLimit(limit)
                    "monthly" -> budget.integer(limit.amount)
                    else -> throw GoalTemplateException("Invalid limit period. Check template syntax")
                }
            limitCheck = true
            if (fromLastMonth >= limitAmount) {
                limitMet = true
                if (limit.hold) {
                    toBudgetAmount = 0
                    fullAmount = 0
                } else {
                    limitExcess = fromLastMonth - limitAmount
                    toBudgetAmount = -limitExcess
                    fullAmount = -limitExcess
                }
            }
        }
    }

    private fun weeklyLimit(limit: GoalTemplateLimit): Long {
        var week = limit.start?.takeIf { it.isNotEmpty() } ?: throw GoalTemplateException("Weekly limit requires a start date (YYYY-MM-DD)")
        val nextMonth = ActualMonths.add(budget.month, 1)
        var total = 0L
        while (week < nextMonth) {
            if (week >= budget.month) total += budget.integer(limit.amount)
            week = GoalMonths.addDays(week, WEEK)
        }
        return total
    }

    /** `runTemplatesForPriority`: the amount budgeted at [priority], negative for an income category. */
    fun runPriority(
        priority: Int,
        budgetAvail: Long,
        availStart: Long,
    ): Long {
        if (priority !in priorities || limitMet) return 0
        val atPriority = templates.filter { it.priority == priority }
        var available = budgetAvail
        var toBudget = 0L
        var byDone = false
        var scheduleDone = false
        val runner = GoalTemplateRunners(budget, category, fromLastMonth, limitAmount)
        for (template in atPriority) {
            val amount =
                when (template.type) {
                    "by" -> {
                        if (byDone) 0L else runner.by(templates.filter { it.type == "by" })
                    }

                    "schedule" -> {
                        if (scheduleDone) {
                            0L
                        } else {
                            GoalScheduleTemplates(budget, category).run(atPriority, fromLastMonth + toBudget, fromLastMonth)
                        }
                    }

                    else -> {
                        runner.run(template, availStart)
                    }
                }
            if (template.type == "by") byDone = true
            if (template.type == "schedule") scheduleDone = true
            available -= amount
            toBudget += amount
        }
        if (limitCheck && toBudget + toBudgetAmount + fromLastMonth >= limitAmount) {
            val original = toBudget
            toBudget = limitAmount - toBudgetAmount - fromLastMonth
            limitMet = true
            available += original - toBudget
        }
        if (budget.hideDecimal) toBudget = budget.whole(toBudget)
        fullAmount = (fullAmount ?: 0L) + toBudget
        if (priority > 0 && available < 0 && !category.isIncome) toBudget = max(0L, toBudget + available)
        toBudgetAmount += toBudget
        return if (category.isIncome) -toBudget else toBudget
    }

    fun runRemainder(
        budgetAvail: Long,
        perWeight: Double,
    ): Long {
        if (remainders.isEmpty()) return 0
        var toBudget = jsMathRound(remainderWeight * perWeight).toLong()
        var smallest = 1L
        if (budget.hideDecimal) {
            toBudget = budget.whole(toBudget)
            smallest = 100
        }
        if (toBudget > budgetAvail || budgetAvail - toBudget <= smallest) toBudget = budgetAvail
        if (limitCheck && toBudget + toBudgetAmount + fromLastMonth >= limitAmount) {
            toBudget = limitAmount - toBudgetAmount - fromLastMonth
            limitMet = true
        }
        toBudgetAmount += toBudget
        return toBudget
    }

    /** `getValues`: the amount to budget, the goal, and whether it is a long-term `#goal`. */
    fun values(): GoalTemplateValues {
        val goal = goals.firstOrNull() ?: return GoalTemplateValues(category.id, toBudgetAmount, fullAmount, false)
        return GoalTemplateValues(category.id, if (isGoalOnly) previouslyBudgeted else toBudgetAmount, budget.integer(goal.amount), true)
    }

    private companion object {
        const val WEEK = 7L
    }
}

data class GoalTemplateValues(
    val categoryId: String,
    val budgeted: Long,
    val goal: Long?,
    val longGoal: Boolean,
)

/** Actual's `months.ts` date arithmetic on `YYYY-MM` and `YYYY-MM-DD` strings, keeping each result's format. */
internal object GoalMonths {
    fun day(value: String): Day = if (value.length == 7) Day.parse("$value-01") else Day.parse(value)

    fun addDays(
        value: String,
        days: Long,
    ): String = day(value).plus(days).toString()

    fun months(
        later: String,
        earlier: String,
    ): Int =
        (later.substring(0, 4).toInt() - earlier.substring(0, 4).toInt()) * 12 + later.substring(5, 7).toInt() -
            earlier.substring(5, 7).toInt()

    fun daysBetween(
        later: String,
        earlier: String,
    ): Long = day(later).epoch - day(earlier).epoch

    fun days(month: String): Long = daysBetween(ActualMonths.add(month, 1), month)
}
