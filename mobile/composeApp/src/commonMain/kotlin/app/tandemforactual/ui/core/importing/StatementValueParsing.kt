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
    val (negative, unsigned) = signedAmountText(trimmed)
    if (unsigned.any { !it.isAmountMark() && !it.isWhitespace() }) throw invalidAmount(trimmed)
    val numeric = withoutApostropheGrouping(unsigned.filter { it.isAmountMark() }, trimmed)
    if (numeric.isBlank()) throw invalidAmount(value)
    val decimalSeparator = decimalSeparatorOf(numeric, currencyFractionDigits, numberFormat, trimmed)
    val split = decimalSeparator?.let(numeric::lastIndexOf) ?: -1
    val wholeText = if (split >= 0) numeric.substring(0, split) else numeric
    val wholeDigits = wholeText.filter(Char::isDigit)
    val fractionDigits = if (split >= 0) numeric.substring(split + 1).filter(Char::isDigit).take(currencyFractionDigits) else ""
    val scale = decimalScale(currencyFractionDigits)
    val normalizedFraction = fractionDigits.padEnd(currencyFractionDigits, '0')
    val whole = wholeDigits.toLongOrNull() ?: throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val fraction = normalizedFraction.toLongOrNull() ?: 0L
    if (whole > (Long.MAX_VALUE - fraction) / scale) throw StatementImportParseException("This amount is too large. Use a smaller amount.")
    val hundredths = toStoredHundredths((whole * scale) + fraction, currencyFractionDigits)
    return if (negative) -hundredths else hundredths
}

private fun Char.isAmountMark(): Boolean = isDigit() || this == '.' || this == ',' || this in apostropheMarks

/** Whether the amount is negative, and its text without currency, sign or debit/credit markers. */
private fun signedAmountText(trimmed: String): Pair<Boolean, String> {
    val currencyFree = stripCurrencyMarkers(trimmed)
    val parenthesized = currencyFree.startsWith('(') && currencyFree.endsWith(')')
    var unsigned = currencyFree.removeSurrounding("(", ")").trim()
    val markerMatch = debitCreditMarker.find(unsigned)
    val trailingMarker = markerMatch?.groupValues?.getOrNull(1)?.uppercase()
    if (markerMatch != null) unsigned = unsigned.dropLast(markerMatch.value.length).trim()
    val trailingMinus = unsigned.endsWith('-') || unsigned.endsWith('−')
    if (trailingMinus) unsigned = unsigned.dropLast(1).trim()
    val leadingMinus = unsigned.startsWith('-') || unsigned.startsWith('−')
    if (leadingMinus) unsigned = unsigned.drop(1).trim()
    val leadingPlus = unsigned.startsWith('+')
    if (leadingPlus) unsigned = unsigned.drop(1).trim()
    if ((trailingMarker == "CR" || leadingPlus) && (parenthesized || trailingMinus || leadingMinus)) {
        throw StatementImportParseException("This amount has conflicting signs. Use a single sign.")
    }
    return (parenthesized || trailingMinus || leadingMinus || trailingMarker == "DR") to unsigned
}

/** Swiss-style “1’234.50”: apostrophes may only group thousands, and are dropped once checked. */
private fun withoutApostropheGrouping(
    numeric: String,
    original: String,
): String {
    if (numeric.none { it in apostropheMarks }) return numeric
    if (!apostropheGrouped.matches(numeric)) throw invalidAmount(original, "Check its separators.")
    return numeric.filterNot { it in apostropheMarks }
}

/** The decimal mark in [numeric], or null when every mark groups thousands. */
private fun decimalSeparatorOf(
    numeric: String,
    currencyFractionDigits: Int,
    numberFormat: String?,
    original: String,
): Char? {
    val lastSeparator = maxOf(numeric.lastIndexOf('.'), numeric.lastIndexOf(','))
    if (lastSeparator < 0) return null
    val mark = numeric[lastSeparator]
    val fraction = numeric.substring(lastSeparator + 1)
    val separators = numeric.count { it == '.' || it == ',' }
    return when {
        numeric.contains('.') && numeric.contains(',') -> {
            if (!fitsFraction(fraction, currencyFractionDigits)) throw invalidAmount(original, "Check its decimal places.")
            mark
        }

        separators > 1 -> {
            if (!isGroupedInteger(numeric)) throw invalidAmount(original, "Check its separators.")
            null
        }

        fraction.length == 3 -> {
            threeDigitGroupOrDecimal(numeric, mark, currencyFractionDigits, numberFormat, original)
        }

        fitsFraction(fraction, currencyFractionDigits) -> {
            mark
        }

        else -> {
            throw invalidAmount(original, "Check its decimal places.")
        }
    }
}

/** A fraction the currency can hold, allowing zeros past its own decimals such as “1500.00” for yen. */
private fun fitsFraction(
    fraction: String,
    currencyFractionDigits: Int,
): Boolean =
    fraction.length in 1..currencyFractionDigits ||
        (fraction.length in 1..STORED_AMOUNT_DIGITS && fraction.drop(currencyFractionDigits).all { it == '0' })

/**
 * “1,234” or “1.234”: three decimals only for a currency that has them, otherwise a thousands group unless it uses the
 * budget's own decimal mark.
 */
private fun threeDigitGroupOrDecimal(
    numeric: String,
    mark: Char,
    currencyFractionDigits: Int,
    numberFormat: String?,
    original: String,
): Char? {
    val budgetDecimal = decimalSeparatorFor(numberFormat)
    if (currencyFractionDigits >= 3) {
        return when {
            mark == groupingSeparatorFor(numberFormat) && isGroupedInteger(numeric) -> {
                throw invalidAmount(original, "This amount is ambiguous. Remove the grouping mark or include the decimal mark.")
            }

            mark == budgetDecimal -> {
                mark
            }

            else -> {
                throw invalidAmount(original, "Check its separators.")
            }
        }
    }
    return when {
        budgetDecimal == null || mark == budgetDecimal -> throw invalidAmount(original, "Check its decimal places.")
        isGroupedInteger(numeric) -> null
        else -> throw invalidAmount(original, "Check its separators.")
    }
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

private val apostropheMarks = setOf('\'', '’')
private val apostropheGrouped = Regex("^\\d{1,3}(?:['’]\\d{3})+(?:[.,]\\d+)?$")
private val debitCreditMarker = Regex("(?i)\\s*(DR|CR)\\.?$")
private val currencyCodes =
    Regex(
        "(?i)\\b(?:USD|CAD|EUR|GBP|INR|AUD|NZD|JPY|CHF|SGD|HKD|BRL|MXN|SEK|NOK|DKK|PLN|CZK|HUF|ZAR|CNY|KRW|TRY|ILS|THB|PHP|" +
            "IDR|MYR|ARS|CLP|COP)\\b",
    )
private val currencyWords = Regex("(?i)R\\$|kr\\.?|zł|Kč|Fr\\.")
private val currencySymbols = setOf('$', '€', '£', '₹', '¥', '₩', '₺', '₽', '₱', '₪', '₫', '฿', '₦', '₴')

private fun stripCurrencyMarkers(value: String): String =
    currencyWords
        .replace(currencyCodes.replace(value, ""), "")
        .filterNot { it in currencySymbols }
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
    val numeric = numericDate(dateText) ?: throw invalidDate(value)
    val month = if (order == StatementDateOrder.MONTH_DAY_YEAR) numeric.first else numeric.second
    val day = if (order == StatementDateOrder.MONTH_DAY_YEAR) numeric.second else numeric.first
    return formatIsoDate(numeric.year, month, day).also { validateDate(it, value) }
}

/**
 * The day/month order for a whole statement: the order its unambiguous dates show, or [selected] when every date reads
 * either way. A statement that shows both orders can't be read reliably.
 */
internal fun statementDateOrderFor(
    dates: List<String>,
    selected: StatementDateOrder,
): StatementDateOrder {
    val numericDates = dates.mapNotNull { numericDate(it.trim().substringBefore('T').trim()) }
    val dayFirst = numericDates.any { it.first in 13..31 && it.second in 1..12 }
    val monthFirst = numericDates.any { it.second in 13..31 && it.first in 1..12 }
    return when {
        dayFirst && monthFirst -> {
            throw StatementImportParseException("This statement mixes day-first and month-first dates. Check the dates in the file.")
        }

        dayFirst -> {
            StatementDateOrder.DAY_MONTH_YEAR
        }

        monthFirst -> {
            StatementDateOrder.MONTH_DAY_YEAR
        }

        dates.any { dottedDate.matches(it.trim()) } -> {
            StatementDateOrder.DAY_MONTH_YEAR
        }

        else -> {
            selected
        }
    }
}

private val dottedDate = Regex("^\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}$")

private data class NumericDate(
    val first: Int,
    val second: Int,
    val year: Int,
)

private val spaceAfterDateSeparator = Regex("([./\\\\'-])\\s+")
private val numericDatePattern = Regex("^(\\d{1,2})[./\\\\-](\\d{1,2})([./\\\\'-])(\\d{2,4})$")

/** “31/10/2026”, “10-31-26” or Quicken's “10/31'26” and “1/ 5'26”, before deciding which number is the month. */
private fun numericDate(dateText: String): NumericDate? {
    val compact = spaceAfterDateSeparator.replace(dateText, "\$1").substringBefore(' ')
    val match = numericDatePattern.matchEntire(compact) ?: return null
    var year = match.groupValues[4].toIntOrNull() ?: return null
    if (year < 100) year += if (match.groupValues[3] == "'" || year < 50) 2000 else 1900
    return NumericDate(match.groupValues[1].toInt(), match.groupValues[2].toInt(), year)
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
