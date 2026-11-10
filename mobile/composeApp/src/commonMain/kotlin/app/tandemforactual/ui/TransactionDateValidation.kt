package app.tandemforactual.ui

internal const val MIN_TRANSACTION_DATE = "1995-01-01"
internal const val TRANSACTION_DATE_CONFIRMATION_YEARS = 1
internal const val MAX_TRANSACTION_DATE_YEARS_AHEAD = 5

internal enum class TransactionDateValidationStatus {
    VALID,
    INVALID,
    BEFORE_MINIMUM,
    CONFIRMATION_REQUIRED,
    AFTER_MAXIMUM,
}

internal data class TransactionDateValidation(
    val status: TransactionDateValidationStatus,
    val date: String? = null,
    val maximumDate: String? = null,
)

/** Validates the shared create and edit transaction date policy against the device's current date. */
internal fun validateTransactionDate(
    value: String,
    today: String,
    confirmedFutureDate: String? = null,
): TransactionDateValidation {
    val date = normalizeTransactionDate(value) ?: return TransactionDateValidation(TransactionDateValidationStatus.INVALID)
    val currentDate = normalizeTransactionDate(today) ?: return TransactionDateValidation(TransactionDateValidationStatus.INVALID)
    val confirmationBoundary = addCalendarYears(currentDate, TRANSACTION_DATE_CONFIRMATION_YEARS)
    val maximumDate = addCalendarYears(currentDate, MAX_TRANSACTION_DATE_YEARS_AHEAD)

    return when {
        date < MIN_TRANSACTION_DATE -> TransactionDateValidation(TransactionDateValidationStatus.BEFORE_MINIMUM, date, maximumDate)
        date > maximumDate -> TransactionDateValidation(TransactionDateValidationStatus.AFTER_MAXIMUM, date, maximumDate)
        date > confirmationBoundary && confirmedFutureDate != date ->
            TransactionDateValidation(TransactionDateValidationStatus.CONFIRMATION_REQUIRED, date, maximumDate)
        else -> TransactionDateValidation(TransactionDateValidationStatus.VALID, date, maximumDate)
    }
}

internal fun transactionDateValidationMessage(validation: TransactionDateValidation): String? =
    when (validation.status) {
        TransactionDateValidationStatus.VALID -> null
        TransactionDateValidationStatus.INVALID -> "Choose a valid transaction date."
        TransactionDateValidationStatus.BEFORE_MINIMUM ->
            "Choose a date on or after ${displayTransactionDate(MIN_TRANSACTION_DATE)}."
        TransactionDateValidationStatus.CONFIRMATION_REQUIRED -> "Confirm this future date before saving."
        TransactionDateValidationStatus.AFTER_MAXIMUM ->
            "Choose a date within five years of today. Later dates are blocked to catch year typos."
    }

internal fun normalizeTransactionDate(value: String): String? {
    val text = value.trim()
    if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(text)) return null
    val year = text.substring(0, 4).toIntOrNull() ?: return null
    val month = text.substring(5, 7).toIntOrNull() ?: return null
    val day = text.substring(8, 10).toIntOrNull() ?: return null
    if (year == 0 || month !in 1..12) return null
    return text.takeIf { day in 1..daysInMonth(year, month) }
}

private fun addCalendarYears(
    isoDate: String,
    years: Int,
): String {
    val (year, month, day) =
        checkNotNull(normalizeTransactionDate(isoDate)).let { normalizedDate ->
            Triple(
                normalizedDate.substring(0, 4).toInt(),
                normalizedDate.substring(5, 7).toInt(),
                normalizedDate.substring(8, 10).toInt(),
            )
        }
    val targetYear = year + years
    if (targetYear > 9999) return "9999-12-31"
    val targetDay = minOf(day, daysInMonth(targetYear, month))
    return transactionIsoDate(targetYear, month, targetDay)
}
