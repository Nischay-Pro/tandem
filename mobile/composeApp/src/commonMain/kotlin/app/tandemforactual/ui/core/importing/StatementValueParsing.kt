package app.tandemforactual.ui.core.importing

import app.tandemforactual.ui.core.format.STORED_AMOUNT_DIGITS
import app.tandemforactual.ui.core.format.storedHundredths

internal fun normalizeColumn(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)

internal fun List<String>.cell(index: Int?): String = index?.let { getOrNull(it)?.trim() }.orEmpty()

internal fun parseOptionalMoney(
    value: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
): Long? = value.takeIf(String::isNotBlank)?.let { parseMoney(it, currencyFractionDigits, numberFormat) }

internal fun parseMoney(
    value: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
): Long {
    val trimmed = value.trim()
    if (trimmed.isBlank()) throw StatementImportParseException("An amount is missing. Enter an amount for this row.")
    val currencyFree = stripCurrencyMarkers(trimmed)
    val parenthesized = currencyFree.startsWith('(') && currencyFree.endsWith(')')
    var unsigned = currencyFree.removeSurrounding("(", ")").trim()
    val trailingMarker =
        Regex("(?i)\\s*(DR|CR)\\.?$")
            .find(unsigned)
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
    if (trailingMarker != null) unsigned = unsigned.dropLast(Regex("(?i)\\s*(DR|CR)\\.?$").find(unsigned)?.value?.length ?: 0).trim()
    val trailingMinus = unsigned.endsWith('-') || unsigned.endsWith('−')
    if (trailingMinus) unsigned = unsigned.dropLast(1).trim()
    val leadingMinus = unsigned.startsWith('-') || unsigned.startsWith('−')
    if (leadingMinus) unsigned = unsigned.drop(1).trim()
    val leadingPlus = unsigned.startsWith('+')
    if (leadingPlus) unsigned = unsigned.drop(1).trim()
    if ((trailingMarker == "CR" || leadingPlus) && (parenthesized || trailingMinus || leadingMinus)) {
        throw StatementImportParseException("This amount has conflicting signs. Use a single sign.")
    }
    val negative = parenthesized || trailingMinus || leadingMinus || trailingMarker == "DR"
    if (unsigned.any { !it.isDigit() && it != '.' && it != ',' && !it.isWhitespace() }) throw invalidAmount(trimmed)
    val numeric = unsigned.filter { it.isDigit() || it == '.' || it == ',' }
    if (numeric.isBlank()) throw invalidAmount(value)
    val lastSeparator = maxOf(numeric.lastIndexOf('.'), numeric.lastIndexOf(','))
    val separators = numeric.count { it == '.' || it == ',' }
    val fractionLength = if (lastSeparator >= 0) numeric.length - lastSeparator - 1 else 0
    val decimalSeparator =
        when {
            lastSeparator < 0 -> null
            numeric.contains('.') && numeric.contains(',') -> {
                if (fractionLength !in 1..currencyFractionDigits) throw invalidAmount(trimmed, "Check its decimal places.")
                numeric[lastSeparator]
            }
            separators > 1 -> {
                if (!isGroupedInteger(numeric)) throw invalidAmount(trimmed, "Check its separators.")
                null
            }
            fractionLength in 1..currencyFractionDigits -> {
                if (fractionLength == 3 && separators == 1) {
                    when {
                        numeric[lastSeparator] == groupingSeparatorFor(numberFormat) && isGroupedInteger(numeric) ->
                            throw invalidAmount(trimmed, "This amount is ambiguous. Remove the grouping mark or include the decimal mark.")
                        numeric[lastSeparator] == decimalSeparatorFor(numberFormat) -> numeric[lastSeparator]
                        else -> throw invalidAmount(trimmed, "Check its separators.")
                    }
                } else {
                    numeric[lastSeparator]
                }
            }
            fractionLength == 3 && currencyFractionDigits == 0 -> {
                when {
                    numeric[lastSeparator] == groupingSeparatorFor(numberFormat) && isGroupedInteger(numeric) -> null
                    numeric[lastSeparator] == decimalSeparatorFor(numberFormat) ->
                        throw invalidAmount(trimmed, "Check its decimal places.")
                    else -> throw invalidAmount(trimmed, "Check its separators.")
                }
            }
            fractionLength == 3 && currencyFractionDigits != 3 -> throw invalidAmount(trimmed, "Check its decimal places.")
            else -> throw invalidAmount(trimmed, "Check its decimal places.")
        }
    val split = decimalSeparator?.let(numeric::lastIndexOf) ?: -1
    val wholeText = if (split >= 0) numeric.substring(0, split) else numeric
    val wholeDigits = wholeText.filter(Char::isDigit)
    val fractionDigits = if (split >= 0) numeric.substring(split + 1).filter(Char::isDigit) else ""
    if (fractionDigits.length > currencyFractionDigits) throw invalidAmount(trimmed, "Check its decimal places.")
    val scale = decimalScale(currencyFractionDigits)
    val normalizedFraction = fractionDigits.padEnd(currencyFractionDigits, '0')
    val whole = wholeDigits.toLongOrNull() ?: throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val fraction = normalizedFraction.toLongOrNull() ?: 0L
    if (whole > (Long.MAX_VALUE - fraction) / scale) throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val hundredths = toStoredHundredths((whole * scale) + fraction, currencyFractionDigits)
    return if (negative) -hundredths else hundredths
}

/**
 * A statement amount in the currency's own minor units, as the hundredths Actual stores. A third decimal, which Actual
 * can't keep, rounds to the nearest hundredth.
 */
private fun toStoredHundredths(
    minorUnits: Long,
    currencyFractionDigits: Int,
): Long {
    if (currencyFractionDigits <= STORED_AMOUNT_DIGITS) {
        return storedHundredths(minorUnits, currencyFractionDigits)
            ?: throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    }
    val extra = decimalScale(currencyFractionDigits - STORED_AMOUNT_DIGITS)
    return (minorUnits + extra / 2) / extra
}

private fun decimalScale(fractionDigits: Int): Long {
    var scale = 1L
    repeat(fractionDigits) { scale *= 10L }
    return scale
}

private fun decimalSeparatorFor(numberFormat: String?): Char? =
    when (numberFormat) {
        "comma-dot", "comma-dot-in", "apostrophe-dot" -> '.'
        "dot-comma", "space-comma" -> ','
        else -> null
    }

private fun groupingSeparatorFor(numberFormat: String?): Char? =
    when (numberFormat) {
        "comma-dot", "comma-dot-in" -> ','
        "dot-comma" -> '.'
        else -> null
    }

private fun stripCurrencyMarkers(value: String): String =
    value
        .replace(Regex("(?i)\\b(?:USD|CAD|EUR|GBP|INR|AUD|NZD|JPY|CHF|SGD|HKD)\\b"), "")
        .filterNot { it in setOf('$', '€', '£', '₹', '¥', '₩') }
        .trim()

private fun isGroupedInteger(value: String): Boolean {
    val separator = value.firstOrNull { it == '.' || it == ',' } ?: return true
    val groups = value.split(separator)
    return groups.size > 1 && groups.first().length in 1..3 && groups.drop(1).all { it.length == 3 && it.all(Char::isDigit) }
}

internal fun parseStatementDate(
    value: String,
    order: StatementDateOrder,
): String {
    val dateText = value.trim().substringBefore('T').trim()
    val numericText = dateText.substringBefore(' ')
    val yearFirst = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$").matchEntire(numericText)
    if (yearFirst != null) {
        val result = formatIsoDate(yearFirst.groupValues[1].toInt(), yearFirst.groupValues[2].toInt(), yearFirst.groupValues[3].toInt())
        return result.also { validateDate(it, value) }
    }
    if (numericText.length >= 8 && numericText.take(8).all(Char::isDigit)) {
        return "${numericText.take(4)}-${numericText.substring(4, 6)}-${numericText.substring(6, 8)}".also { validateDate(it, value) }
    }
    val monthNamed = parseMonthNamedDate(dateText)
    if (monthNamed != null) return monthNamed.also { validateDate(it, value) }
    val numeric =
        Regex("^(\\d{1,2})[./\\\\-](\\d{1,2})[./\\\\-](\\d{2,4})$").matchEntire(numericText)
            ?: throw invalidDate(value)
    val first = numeric.groupValues[1].toIntOrNull() ?: throw invalidDate(value)
    val second = numeric.groupValues[2].toIntOrNull() ?: throw invalidDate(value)
    var year = numeric.groupValues[3].trimEnd('\'').toIntOrNull() ?: throw invalidDate(value)
    if (year < 100) year += if (year < 50) 2000 else 1900
    val month =
        if (first > 12) {
            second
        } else if (order == StatementDateOrder.MONTH_DAY_YEAR) {
            first
        } else {
            second
        }
    val day =
        if (first > 12) {
            first
        } else if (order == StatementDateOrder.MONTH_DAY_YEAR) {
            second
        } else {
            first
        }
    return formatIsoDate(year, month, day).also { validateDate(it, value) }
}

private fun parseMonthNamedDate(value: String): String? {
    val parts =
        value
            .replace(',', ' ')
            .trim()
            .split(Regex("\\s+"))
            .take(3)
    if (parts.size != 3) return null
    val monthByName =
        mapOf(
            "jan" to 1,
            "january" to 1,
            "feb" to 2,
            "february" to 2,
            "mar" to 3,
            "march" to 3,
            "apr" to 4,
            "april" to 4,
            "may" to 5,
            "jun" to 6,
            "june" to 6,
            "jul" to 7,
            "july" to 7,
            "aug" to 8,
            "august" to 8,
            "sep" to 9,
            "sept" to 9,
            "september" to 9,
            "oct" to 10,
            "october" to 10,
            "nov" to 11,
            "november" to 11,
            "dec" to 12,
            "december" to 12,
        )
    val monthIndex = parts.indexOfFirst { token -> monthByName.containsKey(token.trimEnd('.').lowercase()) }
    if (monthIndex < 0) return null
    val month = monthByName.getValue(parts[monthIndex].trimEnd('.').lowercase())
    val numberParts = parts.filterIndexed { index, _ -> index != monthIndex }
    if (numberParts.size != 2) return null
    val yearToken = numberParts.last()
    var year = yearToken.trimEnd('\'').toIntOrNull() ?: return null
    val day = numberParts.first().trimEnd('\'').toIntOrNull() ?: return null
    if (year < 100) year += if (year < 50) 2000 else 1900
    return formatIsoDate(year, month, day)
}

private fun invalidDate(value: String) =
    StatementImportParseException("Couldn’t read the date “${value.take(48)}”. Check the selected date format.")

private fun invalidAmount(
    value: String,
    guidance: String = "Use a number such as 12.34.",
) = StatementImportParseException("Couldn’t read the amount “${value.take(48)}”. $guidance")

private fun validateDate(
    isoDate: String,
    input: String,
) {
    try {
        validateIsoDate(isoDate)
    } catch (_: StatementImportParseException) {
        throw invalidDate(input)
    }
}

private fun formatIsoDate(
    year: Int,
    month: Int,
    day: Int,
): String = year.toString().padStart(4, '0') + "-" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')

private fun validateIsoDate(value: String) {
    val parts = value.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val days =
        when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            in 1..12 -> 31
            else -> 0
        }
    if (year !in 1..9999 || day !in 1..days) throw StatementImportParseException("Couldn’t read this date. Check the selected date format.")
}

internal fun String.containsAny(vararg values: String): Boolean = values.any(::contains)
