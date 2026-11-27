package app.tandemforactual.ui.feature.schedules.components

import app.tandemforactual.ui.core.state.formatLiveDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

private val WEEKDAY_NAMES =
    mapOf(
        "MO" to "Monday",
        "TU" to "Tuesday",
        "WE" to "Wednesday",
        "TH" to "Thursday",
        "FR" to "Friday",
        "SA" to "Saturday",
        "SU" to "Sunday",
    )

internal fun ordinal(n: Int): String =
    n.toString() +
        when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }

private fun nth(value: Int) = if (value == -1) "last" else ordinal(value)

/** A schedule's date rule in words, such as "Every 2 weeks" or "Monthly on the 1st and 3rd Friday". */
internal fun scheduleRepeatLabel(dateJson: String): String {
    val value = runCatching { Json.parseToJsonElement(dateJson) }.getOrNull()
    if (value is JsonPrimitive) return "Once on ${formatLiveDate(value.contentOrNull)}"
    val config = value as? JsonObject ?: return "Repeats"
    val interval = config["interval"]?.jsonPrimitive?.intOrNull ?: 1
    val frequency = config["frequency"]?.jsonPrimitive?.contentOrNull
    val unit =
        when (frequency) {
            "daily" -> "day"
            "weekly" -> "week"
            "yearly" -> "year"
            else -> "month"
        }
    val every = if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
    if (frequency != "monthly") return every
    val patterns = (config["patterns"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val days = patterns.filter { it["type"]?.jsonPrimitive?.contentOrNull == "day" }.mapNotNull { it["value"]?.jsonPrimitive?.intOrNull }
    val weekdays = patterns.filter { it["type"]?.jsonPrimitive?.contentOrNull != "day" }
    val on =
        when {
            weekdays.isNotEmpty() ->
                weekdays.joinToString(" and ") {
                    val name = WEEKDAY_NAMES[it["type"]?.jsonPrimitive?.contentOrNull].orEmpty()
                    "${nth(it["value"]?.jsonPrimitive?.intOrNull ?: 1)} $name"
                }
            days.isNotEmpty() -> days.joinToString(" and ") { if (it == -1) "last day" else ordinal(it) }
            else -> config["start"]?.jsonPrimitive?.contentOrNull?.takeLast(2)?.toIntOrNull()?.let(::ordinal) ?: return every
        }
    return "$every on the $on"
}
