package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal data class BudgetCategory(
    val id: String,
    val name: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val groupId: String,
) {
    fun external(): Map<String, JsonElement> =
        linkedMapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "is_income" to JsonPrimitive(isIncome),
            "hidden" to JsonPrimitive(hidden),
            "group_id" to JsonPrimitive(groupId),
        )
}

internal data class BudgetGroup(
    val id: String,
    val name: String,
    val isIncome: Boolean,
    val hidden: Boolean,
    val categories: List<BudgetCategory>,
) {
    fun external(): Map<String, JsonElement> =
        linkedMapOf(
            "id" to JsonPrimitive(id),
            "name" to JsonPrimitive(name),
            "is_income" to JsonPrimitive(isIncome),
            "hidden" to JsonPrimitive(hidden),
        )
}

/**
 * Actual's `getBudgetRange`: three months before the first transaction through twelve after the current month,
 * extended to three months past the latest non-zero amount but never more than sixty months ahead.
 */
internal fun actualCreatedMonths(
    db: ActualDatabase,
    currentMonth: String,
    tracking: Boolean,
): List<String> {
    val earliest =
        db.first("SELECT date FROM transactions WHERE isChild=0 AND date IS NOT NULL ORDER BY date ASC LIMIT 1")?.get("date")?.let {
            ActualMonths.fromDateInt((it as Number).toLong())
        } ?: currentMonth
    val table = if (tracking) "reflect_budgets" else "zero_budgets"
    val latest =
        db
            .first(
                "SELECT MAX(b.month) AS month FROM $table b JOIN categories c ON c.id = b.category WHERE c.tombstone = 0 AND b.amount != 0",
            )?.get("month")
            ?.let { (it as? Number)?.toLong() ?: it.toString().toLongOrNull() }
            ?.takeIf { it > 0 }
            ?.let { ActualMonths.of((it / 100).toInt(), (it % 100).toInt()) }
    var end = ActualMonths.add(currentMonth, MIN_MONTHS_AHEAD)
    if (latest != null) {
        val planned = ActualMonths.add(latest, MONTHS_AFTER_LATEST_ALLOCATION)
        if (planned > end) end = minOf(planned, ActualMonths.add(currentMonth, MAX_MONTHS_AHEAD))
    }
    return ActualMonths.rangeInclusive(ActualMonths.add(minOf(earliest, currentMonth), -3), end)
}

private const val MIN_MONTHS_AHEAD = 12
private const val MONTHS_AFTER_LATEST_ALLOCATION = 3
private const val MAX_MONTHS_AHEAD = 60

/**
 * Reproduces the cells of Actual's budget spreadsheet (`envelope.ts` and `tracking.ts`) for every month Actual creates,
 * and returns them in the shape of the API's `getBudgetMonth`.
 */
internal class ActualBudgetMonths(
    private val db: ActualDatabase,
    private val groups: List<BudgetGroup>,
    private val tracking: Boolean,
) {
    private data class Cell(
        val amount: Any?,
        val carryover: Boolean,
    )

    private val categories = groups.flatMap { it.categories }
    private val budgets = HashMap<String, Cell>()
    private val buffered = HashMap<String, Any?>()
    private val sums = HashMap<String, Long>()

    fun createdMonths(currentMonth: String): List<String> = actualCreatedMonths(db, currentMonth, tracking)

    fun compute(
        months: List<String>,
        returned: List<String>,
    ): List<Pair<String, JsonObject>> {
        val values = cells(months)
        return returned.map { it to output(it, values.getValue(it)) }
    }

    /** Every sheet cell of each month, keyed by Actual's cell names (`budget-<id>`, `leftover-<id>`, `to-budget`, …). */
    fun cells(months: List<String>): Map<String, Map<String, Any?>> {
        load(months)
        val values = HashMap<String, Map<String, Any?>>()
        var previous: Map<String, Any?> = if (tracking) emptyMap() else blankMonth()
        for (month in months) {
            val current = if (tracking) trackingMonth(month, previous) else envelopeMonth(month, previous)
            values[month] = current
            previous = current
        }
        return values
    }

    private fun load(months: List<String>) {
        val table = if (tracking) "reflect_budgets" else "zero_budgets"
        db.query("SELECT b.* FROM $table b LEFT JOIN categories c ON c.id = b.category WHERE c.tombstone = 0").forEach {
            val month = it["month"]
            val category = it["category"] as String?
            if (truthy(month) && category != null) {
                budgets["${(month as Number).toLong()}-$category"] = Cell(it["amount"], (it["carryover"] as Number?)?.toLong() == 1L)
            }
        }
        if (!tracking) db.query("SELECT * FROM zero_budget_months").forEach { buffered[it["id"] as String] = it["buffered"] }
        if (months.isEmpty()) return
        db
            .query(
                """
                SELECT t.category AS category, t.date / 100 AS month, SUM(t.amount) AS amount
                FROM v_transactions_internal_alive t LEFT JOIN accounts a ON a.id = t.account
                WHERE t.date >= ? AND t.date <= ? AND t.category IS NOT NULL AND a.offbudget = 0
                GROUP BY t.category, t.date / 100
                """.trimIndent(),
                ActualMonths.firstDay(months.first()),
                ActualMonths.lastDay(months.last()),
            ).forEach { sums["${(it["month"] as Number).toLong()}-${it["category"]}"] = (it["amount"] as Number?)?.toLong() ?: 0 }
    }

    private fun budget(
        month: String,
        category: String,
    ): Cell? = budgets["${ActualMonths.dbMonth(month)}-$category"]

    private fun sum(
        month: String,
        category: String,
    ): Long = sums["${ActualMonths.dbMonth(month)}-$category"] ?: 0

    private fun blankMonth(): Map<String, Any?> {
        val cells = HashMap<String, Any?>()
        cells["to-budget"] = 0L
        cells["buffered"] = 0L
        categories.forEach {
            cells["carryover-${it.id}"] = false
            cells["leftover-${it.id}"] = 0L
            cells["leftover-pos-${it.id}"] = 0L
        }
        return cells
    }

    private fun envelopeMonth(
        month: String,
        prev: Map<String, Any?>,
    ): Map<String, Any?> {
        val cells = HashMap<String, Any?>()
        categories.forEach { cat ->
            cells["sum-amount-${cat.id}"] = sum(month, cat.id)
            val stored = budget(month, cat.id)
            if (stored != null) {
                cells["budget-${cat.id}"] = stored.amount
                cells["carryover-${cat.id}"] = stored.carryover
            }
            if (!cat.isIncome) {
                if (cells["budget-${cat.id}"] == null) cells["budget-${cat.id}"] = 0L
                if (!cells.containsKey("carryover-${cat.id}")) cells["carryover-${cat.id}"] = false
                val carried =
                    if (truthy(
                            prev["carryover-${cat.id}"],
                        )
                    ) {
                        number(prev["leftover-${cat.id}"])
                    } else {
                        number(prev["leftover-pos-${cat.id}"])
                    }
                val leftover = number(cells["budget-${cat.id}"]) + number(cells["sum-amount-${cat.id}"]) + carried
                cells["leftover-${cat.id}"] = leftover
                cells["leftover-pos-${cat.id}"] = if (leftover < 0) 0L else leftover
            }
        }
        groups.forEach { group ->
            cells["group-sum-amount-${group.id}"] = group.categories.sumOf { number(cells["sum-amount-${it.id}"]) }
            if (!group.isIncome) {
                cells["group-budget-${group.id}"] = group.categories.sumOf { number(cells["budget-${it.id}"]) }
                cells["group-leftover-${group.id}"] = group.categories.sumOf { number(cells["leftover-${it.id}"]) }
            }
        }
        val incomeGroup = groups.firstOrNull { it.isIncome }
        val expenseGroups = groups.filter { !it.isIncome }
        cells["buffered"] = if (buffered.containsKey(month)) buffered[month] else 0L
        cells["from-last-month"] = number(prev["to-budget"]) + number(prev["buffered-selected"])
        cells["total-income"] = incomeGroup?.let { cells["group-sum-amount-${it.id}"] } ?: 0L
        cells["available-funds"] = number(cells["total-income"]) + number(cells["from-last-month"])
        cells["last-month-overspent"] =
            categories.filter { !it.isIncome }.sumOf { cat ->
                if (truthy(prev["carryover-${cat.id}"])) 0L else minOf(0L, number(prev["leftover-${cat.id}"]))
            }
        cells["total-budgeted"] = -expenseGroups.sumOf { number(cells["group-budget-${it.id}"]) }
        cells["buffered-auto"] =
            categories.filter { it.isIncome }.sumOf { cat ->
                if (truthy(cells["carryover-${cat.id}"])) number(cells["sum-amount-${cat.id}"]) else 0L
            }
        cells["buffered-selected"] = if (number(cells["buffered"]) != 0L) cells["buffered"] else cells["buffered-auto"]
        cells["to-budget"] =
            number(cells["available-funds"]) + number(cells["last-month-overspent"]) + number(cells["total-budgeted"]) -
            number(cells["buffered-selected"])
        cells["total-spent"] = expenseGroups.sumOf { number(cells["group-sum-amount-${it.id}"]) }
        cells["total-leftover"] = expenseGroups.sumOf { number(cells["group-leftover-${it.id}"]) }
        return cells
    }

    private fun trackingMonth(
        month: String,
        prev: Map<String, Any?>,
    ): Map<String, Any?> {
        val cells = HashMap<String, Any?>()
        categories.forEach { cat ->
            cells["sum-amount-${cat.id}"] = sum(month, cat.id)
            val stored = budget(month, cat.id)
            cells["budget-${cat.id}"] = stored?.amount ?: 0L
            cells["carryover-${cat.id}"] = stored?.carryover ?: false
            val carried = if (truthy(prev["carryover-${cat.id}"])) number(prev["leftover-${cat.id}"]) else 0L
            val sum = number(cells["sum-amount-${cat.id}"])
            cells["leftover-${cat.id}"] = number(cells["budget-${cat.id}"]) + (if (cat.isIncome) -sum else sum) + carried
        }
        groups.forEach { group ->
            val visible = group.categories.filter { !it.hidden }
            cells["group-sum-amount-${group.id}"] = visible.sumOf { number(cells["sum-amount-${it.id}"]) }
            cells["group-budget-${group.id}"] = visible.sumOf { number(cells["budget-${it.id}"]) }
            cells["group-leftover-${group.id}"] = visible.sumOf { number(cells["leftover-${it.id}"]) }
        }
        val incomeGroup = groups.firstOrNull { it.isIncome }
        val expenseGroups = groups.filter { !it.isIncome && !it.hidden }
        cells["total-budgeted"] = expenseGroups.sumOf { number(cells["group-budget-${it.id}"]) }
        cells["total-spent"] = expenseGroups.sumOf { number(cells["group-sum-amount-${it.id}"]) }
        cells["total-income"] = incomeGroup?.let { cells["group-sum-amount-${it.id}"] } ?: 0L
        cells["total-leftover"] = expenseGroups.sumOf { number(cells["group-leftover-${it.id}"]) }
        cells["total-saved"] = (incomeGroup?.let { number(cells["group-budget-${it.id}"]) } ?: 0L) - number(cells["total-budgeted"])
        return cells
    }

    private fun output(
        month: String,
        cells: Map<String, Any?>,
    ): JsonObject {
        fun value(name: String): JsonElement = cellJson(cells[name])
        return buildJsonObject {
            put("month", JsonPrimitive(month))
            put("incomeAvailable", value("available-funds"))
            put("lastMonthOverspent", value("last-month-overspent"))
            put("forNextMonth", value("buffered"))
            put("totalBudgeted", value("total-budgeted"))
            put("toBudget", value("to-budget"))
            put("fromLastMonth", value("from-last-month"))
            put("totalIncome", value("total-income"))
            put("totalSpent", value("total-spent"))
            put("totalBalance", value("total-leftover"))
            put(
                "categoryGroups",
                JsonArray(
                    groups.map { group ->
                        val fields = LinkedHashMap(group.external())
                        if (group.isIncome && !tracking) {
                            fields["received"] = value("total-income")
                            fields["categories"] =
                                JsonArray(
                                    group.categories.map { cat ->
                                        JsonObject(
                                            cat.external() + ("received" to value("sum-amount-${cat.id}")),
                                        )
                                    },
                                )
                        } else {
                            val spentKey = if (group.isIncome) "received" else "spent"
                            fields["budgeted"] = value("group-budget-${group.id}")
                            fields[spentKey] = value("group-sum-amount-${group.id}")
                            fields["balance"] = value("group-leftover-${group.id}")
                            fields["categories"] =
                                JsonArray(
                                    group.categories.map { cat ->
                                        JsonObject(
                                            cat.external() +
                                                linkedMapOf(
                                                    "budgeted" to value("budget-${cat.id}"),
                                                    spentKey to value("sum-amount-${cat.id}"),
                                                    "balance" to value("leftover-${cat.id}"),
                                                    "carryover" to value("carryover-${cat.id}"),
                                                ),
                                        )
                                    },
                                )
                        }
                        JsonObject(fields)
                    },
                ),
            )
        }
    }

    private fun cellJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            else -> jsonNumber(value)
        }

    private fun number(value: Any?): Long = (value as? Number)?.toLong() ?: 0
}
