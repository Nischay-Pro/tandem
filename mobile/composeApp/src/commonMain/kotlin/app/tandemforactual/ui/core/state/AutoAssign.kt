package app.tandemforactual.ui.core.state

/** The ways auto-assign can share out money left to budget. */
internal enum class AutoAssignRule(
    val label: String,
    val detail: String,
) {
    COVER_OVERSPENDING("Cover overspending", "Bring categories that are below zero back to zero"),
    SPENT_LAST_MONTH("Spent last month", "Give each category what it spent last month"),
    AVERAGE_SPENT("3-month average spent", "Give each category its average monthly spending"),
    ASSIGNED_LAST_MONTH("Assigned last month", "Match what each category was given last month"),
}

/** One category's assignment before and after auto-assign. */
internal data class AutoAssignChange(
    val categoryId: String,
    val name: String,
    val fromMinor: Long,
    val toMinor: Long,
) {
    val addedMinor: Long get() = toMinor - fromMinor
}

/**
 * What [rule] would add to this month's spending categories from [availableMinor] left to budget. [history] holds the
 * months before [month], most recent first. The largest needs are filled first, and never more than is available.
 */
internal fun planAutoAssign(
    rule: AutoAssignRule,
    month: LiveBudgetMonthSummary,
    history: List<LiveBudgetMonthSummary>,
    availableMinor: Long,
): List<AutoAssignChange> {
    if (availableMinor <= 0) return emptyList()
    val categories = month.groups.filterNot { it.isIncome }.flatMap { it.categories }

    fun past(months: List<LiveBudgetMonthSummary>) =
        months.map { summary ->
            summary.groups
                .filterNot { it.isIncome }
                .flatMap { it.categories }
                .associateBy { it.id }
        }
    val last = past(history.take(1)).firstOrNull().orEmpty()
    val recent = past(history.take(3))
    val needs =
        categories.mapNotNull { category ->
            val target =
                when (rule) {
                    AutoAssignRule.COVER_OVERSPENDING -> {
                        return@mapNotNull (-category.balanceMinor)
                            .takeIf {
                                it > 0
                            }?.let { category to it }
                    }

                    AutoAssignRule.SPENT_LAST_MONTH -> {
                        -(last[category.id]?.activityMinor ?: 0)
                    }

                    AutoAssignRule.ASSIGNED_LAST_MONTH -> {
                        last[category.id]?.assignedMinor ?: 0
                    }

                    AutoAssignRule.AVERAGE_SPENT -> {
                        if (recent.isEmpty()) 0 else recent.sumOf { -(it[category.id]?.activityMinor ?: 0) } / recent.size
                    }
                }
            (target - category.assignedMinor).takeIf { it > 0 }?.let { category to it }
        }
    var left = availableMinor
    return needs.sortedByDescending { it.second }.mapNotNull { (category, need) ->
        val added = minOf(need, left).takeIf { it > 0 } ?: return@mapNotNull null
        left -= added
        AutoAssignChange(category.id, category.name, category.assignedMinor, category.assignedMinor + added)
    }
}

/** The month before [monthId], as "yyyy-MM". */
internal fun monthBefore(monthId: String): String {
    val year = monthId.take(4).toInt()
    val month = monthId.drop(5).take(2).toInt()
    return if (month == 1) "${year - 1}-12" else "$year-${(month - 1).toString().padStart(2, '0')}"
}
