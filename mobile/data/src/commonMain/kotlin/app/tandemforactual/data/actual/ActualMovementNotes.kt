package app.tandemforactual.data.actual

import app.tandemforactual.data.store.BudgetDisplaySettings

private val MONTH_NAMES =
    listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    )

/** Actual's `addMovementNotes`: appends a "Reassigned" line to the month's budget note. */
internal fun ActualBudgetEdits.addMovementNote(
    month: String,
    amount: Long,
    fromCategoryId: String,
    toCategoryId: String,
) {
    val preferences = db.query("SELECT id, value FROM preferences").associate { it["id"] as String to it["value"] as String? }
    val displayAmount = formatActualAmount(amount, budgetDisplaySettings(preferences))
    val names = categories().associate { it["id"] as String to it["name"] as String? }
    val day = today()
    val displayDay = "${MONTH_NAMES[day.substring(5, 7).toInt() - 1]} ${day.substring(8, 10)}"
    val id = "budget-$month"
    val existing = (db.first("SELECT note FROM notes WHERE id = ?", id)?.get("note") as String?)?.takeIf { it.isNotEmpty() }
    val note = "Reassigned $displayAmount from ${names[fromCategoryId]} → ${names[toCategoryId]} on $displayDay"
    writer.update("notes", id, mapOf("note" to "${existing?.let { "$it\n" }.orEmpty()}- $note"))
}

/** Actual's `integerToCurrencyWithDecimal` with a currency code: the budget's number format at the currency's decimal places. */
internal fun formatActualAmount(
    amount: Long,
    settings: BudgetDisplaySettings,
): String {
    val places = settings.currencyFractionDigits.coerceIn(0, 6)
    val (group, decimal) =
        when (settings.numberFormat) {
            "dot-comma" -> "." to ","
            "space-comma" -> " " to ","
            "apostrophe-dot" -> "’" to "."
            else -> "," to "."
        }
    val magnitude = if (amount == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amount)
    val scaled =
        if (places >= 2) {
            magnitude * pow10(places - 2)
        } else {
            val divisor = pow10(2 - places)
            (magnitude + divisor / 2) / divisor
        }
    val unit = pow10(places)
    val digits = (scaled / unit).toString()
    val whole =
        if (settings.numberFormat == "comma-dot-in" && digits.length > 3) {
            groupDigits(digits.dropLast(3), 2, group) + group + digits.takeLast(3)
        } else {
            groupDigits(digits, 3, group)
        }
    val fraction = if (places > 0) decimal + (scaled % unit).toString().padStart(places, '0') else ""
    return (if (amount < 0 && scaled != 0L) "-" else "") + whole + fraction
}

private fun groupDigits(
    digits: String,
    size: Int,
    separator: String,
): String =
    digits
        .reversed()
        .chunked(size)
        .joinToString(separator)
        .reversed()

private fun pow10(exponent: Int): Long = (1..exponent).fold(1L) { acc, _ -> acc * 10 }
