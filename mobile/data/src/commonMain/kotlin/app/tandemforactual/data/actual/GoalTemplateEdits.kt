package app.tandemforactual.data.actual

/**
 * Actual's "Apply budget templates" (`budget/apply-goal-template`), which fills only categories with nothing budgeted,
 * or with [force] "Overwrite with budget templates". With [categoryIds] it applies just those categories, as
 * `budget/apply-multiple-templates` does. Nothing changes when any template has a problem.
 */
fun ActualBudgetEdits.applyGoalTemplates(
    month: String,
    force: Boolean,
    categoryIds: List<String>? = null,
): GoalTemplateOutcome =
    writer.batch {
        if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(month)) fail("Choose a valid budget month")
        val engine = GoalTemplateEngine(db, today())
        val computed = engine.compute(month, force || categoryIds != null, categoryIds)
        val outcome = with(engine) { computed.outcome() }
        if (outcome.errors.isNotEmpty()) throw ActualEditException(outcome.errors.joinToString("\n\n"), "template_errors")
        outcome.values.forEach { if (getBudget(month, it.categoryId) != it.budgeted) setBudget(month, it.categoryId, it.budgeted) }
        computed.orphans.forEach { setGoal(month, it, null, null) }
        outcome.values.forEach { setGoal(month, it.categoryId, it.goal, if (it.longGoal) 1L else null) }
        outcome
    }

/** Actual's `setGoal`, leaving a month's row alone when its goal is already the same. */
private fun ActualBudgetEdits.setGoal(
    month: String,
    category: String,
    goal: Long?,
    longGoal: Long?,
) {
    val table = budgetTable()
    val dbMonth = ActualMonths.dbMonth(month)
    val row = db.first("SELECT id, goal, long_goal FROM $table WHERE month = ? AND category = ?", dbMonth, category)
    val fields = linkedMapOf<String, Any?>("goal" to goal, "long_goal" to longGoal)
    if (row == null) {
        if (goal != null ||
            longGoal != null
        ) {
            writer.insert(
                table,
                linkedMapOf<String, Any?>("id" to "$dbMonth-$category", "month" to dbMonth, "category" to category) + fields,
            )
        }
    } else if ((row["goal"] as Number?)?.toLong() != goal || (row["long_goal"] as Number?)?.toLong() != longGoal) {
        writer.update(table, row["id"] as String, fields)
    }
}
