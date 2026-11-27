package app.tandemforactual.data.actual

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal fun ActualBudgetEdits.createdMonths(): List<String> = actualCreatedMonths(db, ActualMonths.fromIsoDate(today()), tracking())

private fun ActualBudgetEdits.requireMonth(month: String) {
    if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(month)) fail("Choose a valid budget month")
}

private fun ActualBudgetEdits.monthData(month: String): JsonObject =
    ActualBudgetReader(db).budgetMonth(today(), month) ?: fail("No budget exists for month: $month")

private fun ActualBudgetEdits.monthCategories(month: String): List<JsonObject> =
    monthData(month).getValue("categoryGroups").jsonArray.flatMap { group ->
        (group.jsonObject["categories"] as? JsonArray).orEmpty().map { it.jsonObject }
    }

internal fun ActualBudgetEdits.getBudget(
    month: String,
    category: String,
): Long =
    (
        db
            .first(
                "SELECT amount FROM ${budgetTable()} WHERE month = ? AND category = ?",
                ActualMonths.dbMonth(month),
                category,
            )?.get("amount") as Number?
    )?.toLong() ?: 0L

internal fun ActualBudgetEdits.setBudget(
    month: String,
    category: String,
    amount: Long,
) {
    val table = budgetTable()
    val existing = db.first("SELECT id FROM $table WHERE month = ? AND category = ?", ActualMonths.dbMonth(month), category)
    if (existing != null) {
        writer.update(table, existing["id"] as String, mapOf("amount" to amount))
    } else {
        writer.insert(
            table,
            linkedMapOf(
                "id" to "${ActualMonths.dbMonth(month)}-$category",
                "month" to ActualMonths.dbMonth(month),
                "category" to category,
                "amount" to amount,
            ),
        )
    }
}

private fun ActualBudgetEdits.requireAmount(
    amount: Long,
    message: String,
    allowZero: Boolean = true,
) {
    if (amount < 0 || (!allowZero && amount == 0L) || amount > MAX_BUDGET_AMOUNT_MINOR) fail(message)
}

fun ActualBudgetEdits.setBudgetAmount(
    categoryId: String,
    month: String,
    amount: Long,
) = writer.batch {
    requireAmount(amount, "Enter a budget amount up to 1,000,000,000 major units")
    if (categories().none { it["id"] == categoryId }) fail("Category does not exist in this budget")
    if (month !in createdMonths().dropLast(1)) fail("No budget exists for month: $month")
    setBudget(month, categoryId, amount)
}

fun ActualBudgetEdits.setBudgetCarryover(
    categoryId: String,
    month: String,
    enabled: Boolean,
) = writer.batch {
    if (monthCategories(month).none { it.value("id") == categoryId }) fail("Choose a category in this budget month")
    val category = categories().firstOrNull { it["id"] == categoryId } ?: fail("Choose a category in this budget month")
    val table = budgetTable()
    if (enabled && truthy(category["is_income"]) && table == "zero_budgets") setBuffer(month, 0L)
    for (each in createdMonths().filter { it >= month }) {
        val dbMonth = ActualMonths.dbMonth(each).toString()
        val existing = db.first("SELECT id FROM $table WHERE month = ? AND category = ?", dbMonth, categoryId)
        if (existing != null) {
            writer.update(table, existing["id"] as String, mapOf("carryover" to (if (enabled) 1L else 0L)))
        } else {
            writer.insert(
                table,
                linkedMapOf(
                    "id" to "$dbMonth-$categoryId",
                    "month" to dbMonth,
                    "category" to categoryId,
                    "carryover" to (if (enabled) 1L else 0L),
                ),
            )
        }
    }
}

fun ActualBudgetEdits.copyBudgetMonth(month: String) =
    writer.batch {
        requireMonth(month)
        if (month == "0001-01") fail("Choose a valid budget month")
        val tracking = tracking()
        db
            .query(
                "SELECT b.category, b.amount, c.is_income, c.hidden, g.hidden AS group_hidden FROM ${budgetTable()} b " +
                    "LEFT JOIN categories c ON b.category = c.id LEFT JOIN category_groups g ON c.cat_group = g.id " +
                    "WHERE c.tombstone = 0 AND b.month = ?",
                ActualMonths.dbMonth(ActualMonths.add(month, -1)),
            ).forEach {
                if (truthy(it["is_income"]) && !tracking) return@forEach
                if (truthy(it["hidden"]) || truthy(it["group_hidden"])) return@forEach
                setBudget(month, it["category"] as String, (it["amount"] as Number?)?.toLong() ?: 0L)
            }
    }

fun ActualBudgetEdits.applyBudgetTemplate(
    month: String,
    assignments: List<Pair<String, Long>>,
) = writer.batch {
    requireMonth(month)
    if (assignments.isEmpty() || assignments.size > 500) fail("Choose a template with 1 to 500 categories")
    if (assignments.map { it.first }.toSet().size != assignments.size ||
        assignments.any { it.first.isBlank() || it.second < 0 || it.second > MAX_BUDGET_AMOUNT_MINOR }
    ) {
        fail("The template contains an invalid category amount")
    }
    val monthIds = monthCategories(month).map { it.value("id") as String }.toSet()
    val active = categories().filter { !truthy(it["hidden"]) }.map { it["id"] as String }.toSet()
    if (assignments.any { it.first !in active || it.first !in monthIds }) fail("A template category is no longer active in this month")
    assignments.forEach { (category, amount) -> setBudget(month, category, amount) }
}

private fun ActualBudgetEdits.setBuffer(
    month: String,
    amount: Long,
) {
    if (db.first("SELECT id FROM zero_budget_months WHERE id = ?", month) != null) {
        writer.update("zero_budget_months", month, mapOf("buffered" to amount))
    } else {
        writer.insert("zero_budget_months", linkedMapOf("id" to month, "buffered" to amount))
    }
}

fun ActualBudgetEdits.holdBudgetForNextMonth(
    month: String,
    amount: Long,
) = writer.batch {
    requireMonth(month)
    if (amount <= 0 || amount > MAX_BUDGET_AMOUNT_MINOR) fail("Enter a positive amount to hold for next month")
    if (tracking()) fail("Hold for next month is only available in an envelope budget")
    val data = monthData(month)
    val ready = data.value("toBudget") as Long? ?: 0L
    if (ready <= 0) fail("There is no money ready to assign to hold")
    val held = (db.first("SELECT buffered FROM zero_budget_months WHERE id = ?", month)?.get("buffered") as Number?)?.toLong() ?: 0L
    setBuffer(month, held + minOf(maxOf(amount, -held), ready))
}

fun ActualBudgetEdits.resetBudgetHold(month: String) =
    writer.batch {
        requireMonth(month)
        setBuffer(month, 0L)
    }

fun ActualBudgetEdits.moveMoney(
    month: String,
    fromCategoryId: String,
    toCategoryId: String,
    amount: Long,
) = writer.batch {
    if (amount <= 0 || amount > MAX_BUDGET_AMOUNT_MINOR) fail("Enter a positive move amount up to 1,000,000,000 major units")
    if (fromCategoryId == toCategoryId) fail("Choose two different categories")
    if (tracking()) fail("Money can only be moved in an envelope budget")
    val cats = monthCategories(month)
    val source = cats.firstOrNull { it.value("id") == fromCategoryId }
    val destination = cats.firstOrNull { it.value("id") == toCategoryId }
    if (source == null || destination == null) fail("Choose categories in this budget month")
    if (source.value("is_income") != destination.value("is_income")) fail("Move money between categories of the same type")
    if ((source.value("balance") as Long? ?: 0L) < amount) fail("The source category does not have enough available money")
    setBudget(month, fromCategoryId, (source.value("budgeted") as Long? ?: 0L) - amount)
    setBudget(month, toCategoryId, (destination.value("budgeted") as Long? ?: 0L) + amount)
    addMovementNote(month, amount, fromCategoryId, toCategoryId)
}

/** Actual's synced `budgetType`; each type keeps its own amounts, so switching back finds them as they were. */
fun ActualBudgetEdits.setBudgetType(type: String) =
    writer.batch {
        if (type != "envelope" && type != "tracking") fail("Choose an envelope or tracking budget")
        writer.update("preferences", "budgetType", mapOf("value" to type))
    }
