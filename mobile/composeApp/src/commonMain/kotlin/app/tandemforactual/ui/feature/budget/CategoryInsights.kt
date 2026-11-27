package app.tandemforactual.ui.feature.budget

import app.tandemforactual.ui.core.format.daysInMonth
import app.tandemforactual.ui.core.state.LiveCategoryGroupSummary
import app.tandemforactual.ui.core.state.LiveScheduleSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

internal enum class CategoryPaceStatus(
    val title: String,
) {
    ON_TRACK("On track"),
    AHEAD("Spending faster than planned"),
    OVER("Over budget"),
}

internal data class CategoryPace(
    val status: CategoryPaceStatus,
    val spentMinor: Long,
    val remainingMinor: Long,
    val daysLeft: Int,
)

private const val AHEAD_TOLERANCE = 1.1

/** Money a category had for the month: the assigned amount plus anything carried in from last month. */
internal fun categoryFundsMinor(
    balanceMinor: Long,
    activityMinor: Long,
): Long = balanceMinor - activityMinor

/** Net spending in a category; refunds offset it and never count as spending. */
internal fun categorySpentMinor(activityMinor: Long): Long = (-activityMinor).coerceAtLeast(0)

/** Share of a category's money already spent, for its progress bar. */
internal fun categoryProgress(
    balanceMinor: Long,
    activityMinor: Long,
): Float {
    val funds = categoryFundsMinor(balanceMinor, activityMinor)
    val spent = categorySpentMinor(activityMinor)
    return when {
        funds > 0L -> (spent.toDouble() / funds.toDouble()).toFloat().coerceIn(0f, 1f)
        spent > 0L -> 1f
        else -> 0f
    }
}

/** How spending compares with an even spread of the category's money through the month. */
internal fun categoryPace(
    fundsMinor: Long,
    activityMinor: Long,
    dayOfMonth: Int,
    daysInMonth: Int,
): CategoryPace? {
    if (fundsMinor <= 0 || daysInMonth <= 0 || dayOfMonth !in 1..daysInMonth) return null
    val spent = categorySpentMinor(activityMinor)
    val expected = fundsMinor.toDouble() * dayOfMonth / daysInMonth
    val status =
        when {
            spent > fundsMinor -> CategoryPaceStatus.OVER
            spent > expected * AHEAD_TOLERANCE -> CategoryPaceStatus.AHEAD
            else -> CategoryPaceStatus.ON_TRACK
        }
    return CategoryPace(status, spent, fundsMinor - spent, daysInMonth - dayOfMonth)
}

/** Money spent in one category in an Actual budget month record, or null when the category isn't there. */
internal fun categorySpendingInMonth(
    budgetMonthJson: String?,
    categoryId: String,
): Long? {
    val month = budgetMonthJson?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    val category =
        (month["categoryGroups"] as? JsonArray)
            .orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { (it["categories"] as? JsonArray).orEmpty().mapNotNull { category -> category as? JsonObject } }
            .firstOrNull { (it["id"] as? JsonPrimitive)?.contentOrNull == categoryId } ?: return null
    val activity = (category["spent"] as? JsonPrimitive)?.longOrNull ?: (category["activity"] as? JsonPrimitive)?.longOrNull ?: return null
    return (-activity).coerceAtLeast(0)
}

internal fun previousMonthId(monthId: String): String? {
    val year = monthId.substringBefore('-').toIntOrNull() ?: return null
    val month = monthId.substringAfter('-', "").toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return if (month == 1) "${(year - 1).toString().padStart(4, '0')}-12" else "$year-${(month - 1).toString().padStart(2, '0')}"
}

internal fun categoryPaceDetail(
    pace: CategoryPace,
    format: (Long) -> String,
): String =
    when {
        pace.status == CategoryPaceStatus.OVER -> "${format(-pace.remainingMinor)} more than assigned"
        pace.daysLeft == 0 -> "${format(pace.remainingMinor)} left today"
        else -> "${format(pace.remainingMinor)} left · about ${format(pace.remainingMinor / (pace.daysLeft + 1))} a day"
    }

/** Last month's spending, compared with this month once this month is over. */
internal fun lastMonthSpendingDetail(
    lastMonthMinor: Long,
    thisMonthMinor: Long,
    thisMonthComplete: Boolean,
    format: (Long) -> String,
): String {
    val spent = "${format(lastMonthMinor)} spent"
    if (!thisMonthComplete) return spent
    val difference = thisMonthMinor - lastMonthMinor
    return when {
        difference > 0 -> "$spent · ${format(difference)} more this month"
        difference < 0 -> "$spent · ${format(-difference)} less this month"
        else -> "$spent · the same this month"
    }
}

internal data class StillToSpend(
    val totalMinor: Long,
    val perDayMinor: Long,
)

/** What's left in expense categories this month, ignoring overspent ones, spread over the remaining days including today. */
internal fun stillToSpend(
    groups: List<LiveCategoryGroupSummary>,
    dayOfMonth: Int,
    daysInMonth: Int,
): StillToSpend? {
    if (daysInMonth <= 0 || dayOfMonth !in 1..daysInMonth) return null
    val total = groups.filterNot { it.isIncome }.flatMap { it.categories }.sumOf { it.balanceMinor.coerceAtLeast(0) }
    return StillToSpend(total, total / (daysInMonth - dayOfMonth + 1))
}

/** Scheduled payments still due from [today] to the end of its month, which what's left to spend has to cover. */
internal fun billsStillDue(
    schedules: List<LiveScheduleSummary>,
    today: String,
): Long =
    schedules
        .filter { it.nextDate >= today && it.nextDate.take(7) == today.take(7) }
        .sumOf { -(it.amountMinor ?: 0).coerceAtMost(0) }

/** Offer to assign last month's spending to a category that has nothing assigned yet this month or later. */
internal fun canQuickAssign(
    isTrackingBudget: Boolean,
    readOnly: Boolean,
    assignedMinor: Long,
    lastMonthSpentMinor: Long,
    monthIsCurrentOrLater: Boolean,
): Boolean = !isTrackingBudget && !readOnly && assignedMinor == 0L && lastMonthSpentMinor > 0 && monthIsCurrentOrLater
