package app.tandemforactual.ui

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

/** How spending compares with an even spread of the assigned amount through the month. */
internal fun categoryPace(
    assignedMinor: Long,
    activityMinor: Long,
    dayOfMonth: Int,
    daysInMonth: Int,
): CategoryPace? {
    if (assignedMinor <= 0 || daysInMonth <= 0 || dayOfMonth !in 1..daysInMonth) return null
    val spent = (-activityMinor).coerceAtLeast(0)
    val expected = assignedMinor.toDouble() * dayOfMonth / daysInMonth
    val status =
        when {
            spent > assignedMinor -> CategoryPaceStatus.OVER
            spent > expected * AHEAD_TOLERANCE -> CategoryPaceStatus.AHEAD
            else -> CategoryPaceStatus.ON_TRACK
        }
    return CategoryPace(status, spent, assignedMinor - spent, daysInMonth - dayOfMonth)
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

internal fun daysInMonth(monthId: String): Int? {
    val year = monthId.substringBefore('-').toIntOrNull() ?: return null
    return when (monthId.substringAfter('-', "").toIntOrNull()) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> null
    }
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
