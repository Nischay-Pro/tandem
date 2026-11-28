package app.tandemforactual.data.actual

/** A rule condition's parsed value, as Actual's `rules/condition.ts` reads it. */
internal sealed class RuleValue {
    data class Literal(
        val value: Any?,
    ) : RuleValue()

    data class Number(
        val value: Double,
    ) : RuleValue()

    data class Between(
        val low: Double,
        val high: Double,
    ) : RuleValue()

    data class DateBetween(
        val low: String,
        val high: String,
    ) : RuleValue()

    data class Date(
        val kind: String,
        val date: String,
    ) : RuleValue()

    data class Recur(
        val config: RecurConfig,
    ) : RuleValue()
}

private fun num(any: Any?) = (any as? Number)?.toDouble()

internal fun RuleValue.isBetween(field: Any?): Boolean {
    if (this is RuleValue.DateBetween) {
        val day = field as? String ?: return false
        return day >= minOf(low, high) && day <= maxOf(low, high)
    }
    val between = this as RuleValue.Between
    val n = num(field) ?: return false
    return n >= minOf(between.low, between.high) && n <= maxOf(between.low, between.high)
}

internal fun RuleValue.isMatch(
    op: String,
    field: Any?,
): Boolean =
    when (this) {
        is RuleValue.Recur -> {
            val day = (field as? String)?.let(Day::parse) ?: return false
            if (op == "isapprox") {
                ActualRecurrence.occursBetween(config, day.plus(-2), day.plus(2))
            } else {
                ActualRecurrence.occursBetween(config, day, day)
            }
        }

        is RuleValue.Date -> {
            val day = field as? String ?: return false
            if (op == "isapprox") {
                val center = Day.parse(date)
                day >= center.plus(-2).toString() && day <= center.plus(2).toString()
            } else {
                when (kind) {
                    "date" -> day == date
                    "month" -> day.take(7) == date
                    else -> day.take(4) == date
                }
            }
        }

        is RuleValue.Number -> {
            val n = num(field) ?: return false
            if (op == "isapprox") {
                val threshold = jsMathRound(kotlin.math.abs(value) * 0.075)
                n >= value - threshold && n <= value + threshold
            } else {
                n == value
            }
        }

        else -> {
            field == (this as RuleValue.Literal).value
        }
    }

internal fun RuleValue.compareWith(
    op: String,
    field: Any?,
): Boolean {
    if (field == null) return false
    val comparison =
        when (this) {
            is RuleValue.Date -> (field as String).compareTo(date)
            else -> num(field)?.compareTo((this as RuleValue.Number).value) ?: return false
        }
    return when (op) {
        "gt" -> comparison > 0
        "gte" -> comparison >= 0
        "lt" -> comparison < 0
        else -> comparison <= 0
    }
}
