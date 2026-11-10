package app.tandemforactual.ui

import app.tandemforactual.data.store.BudgetDisplaySettings

internal data class DecimalSeparators(
    val decimal: Char,
    val grouping: Char,
    val primaryGroupingSize: Int = 3,
    val secondaryGroupingSize: Int = primaryGroupingSize,
)

internal fun parseStrictMinorUnits(
    value: String,
    fractionDigits: Int,
    separators: DecimalSeparators,
): Long? {
    if (fractionDigits !in 0..10 || separators.primaryGroupingSize <= 0 || separators.secondaryGroupingSize <= 0) {
        return null
    }

    val input = value.trim { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }
    if (input.isEmpty()) return null

    val normalized = normalizeGroupingSeparators(input, separators.grouping)
    val negative = normalized.first() == '-' || normalized.first() == '\u2212'
    val signedInput =
        if (
            normalized.first() == '-' ||
            normalized.first() == '+' ||
            normalized.first() == '\u2212'
        ) {
            normalized.drop(1)
        } else {
            normalized
        }
    if (signedInput.isEmpty()) return null

    val decimalIndex = signedInput.indexOf(separators.decimal)
    if (decimalIndex >= 0 && (fractionDigits == 0 || signedInput.indexOf(separators.decimal, decimalIndex + 1) >= 0)) {
        return null
    }
    val integerPart = if (decimalIndex >= 0) signedInput.substring(0, decimalIndex) else signedInput
    val fractionPart = if (decimalIndex >= 0) signedInput.substring(decimalIndex + 1) else ""
    if (integerPart.isEmpty() || (decimalIndex >= 0 && fractionPart.isEmpty()) || fractionPart.length > fractionDigits) return null

    val integerGroups = integerPart.split(separators.grouping)
    if (integerGroups.size > 1 && !hasValidGrouping(integerGroups, separators)) return null
    val integerDigits = integerGroups.joinToString(separator = "")
    val normalizedInteger = digitsOnly(integerDigits) ?: return null
    val normalizedFraction = digitsOnly(fractionPart) ?: return null
    val fractionalMinorUnits = normalizedFraction.padEnd(fractionDigits, '0')
    val minorUnitDigits = (normalizedInteger + fractionalMinorUnits).trimStart('0').ifEmpty { "0" }
    val signedMinorUnits =
        when {
            negative && minorUnitDigits != "0" -> "-$minorUnitDigits"
            else -> minorUnitDigits
        }
    return signedMinorUnits.toLongOrNull()
}

internal fun transactionAmountInputError(
    value: String,
    displaySettings: BudgetDisplaySettings?,
): String {
    val fractionDigits = displaySettings?.currencyFractionDigits ?: 2
    val input = value.trim()
    if (fractionDigits == 0 && ('.' in input || ',' in input)) {
        return "This budget doesn’t use decimals. Enter a whole amount, such as 12."
    }

    val decimalSeparator =
        when (displaySettings?.numberFormat) {
            "dot-comma", "space-comma" -> ','
            "comma-dot", "apostrophe-dot", "comma-dot-in" -> '.'
            else -> null
        }
    val wrongDecimalSeparator = decimalSeparator?.let { if (it == '.') ',' else '.' }
    if (wrongDecimalSeparator != null && fractionDigits > 0 && decimalSeparator !in input) {
        val possibleFraction = input.substringAfterLast(wrongDecimalSeparator, "")
        if (possibleFraction.length in 1..fractionDigits && possibleFraction.all(Char::isDigit)) {
            val expectedMark = if (decimalSeparator == ',') "a comma" else "a period"
            val example = "12$decimalSeparator${"0".repeat(fractionDigits)}"
            return "Use $expectedMark for decimals in this budget, such as $example."
        }
    }

    return "Enter an amount using this budget’s number format."
}

internal fun transactionAmountNeedsFormatConfirmation(
    value: String,
    amountMinorUnits: Long,
    displaySettings: BudgetDisplaySettings?,
): Boolean =
    normalizeAmountForDisplayComparison(value) !=
        normalizeAmountForDisplayComparison(formatAmount(amountMinorUnits, displaySettings))

private fun normalizeAmountForDisplayComparison(value: String): String =
    buildString(value.length) {
        value.trim().forEach { character ->
            append(
                when {
                    character == '\u00a0' || character == '\u202f' || character == '\u2009' -> ' '
                    character == '\u2018' || character == '\u2019' || character == '\u02bc' -> '\''
                    else -> character
                },
            )
        }
    }

private fun normalizeGroupingSeparators(
    value: String,
    groupingSeparator: Char,
): String =
    buildString(value.length) {
        value.forEach { character ->
            append(if (isEquivalentGroupingSeparator(character, groupingSeparator)) groupingSeparator else character)
        }
    }

private fun isEquivalentGroupingSeparator(
    character: Char,
    groupingSeparator: Char,
): Boolean {
    if (character == groupingSeparator) return true
    val spaceSeparators = setOf(' ', '\u00A0', '\u202F', '\u2009')
    if (groupingSeparator in spaceSeparators) return character in spaceSeparators
    val apostropheSeparators = setOf('\'', '\u2018', '\u2019', '\u02BC')
    return groupingSeparator in apostropheSeparators && character in apostropheSeparators
}

private fun hasValidGrouping(
    groups: List<String>,
    separators: DecimalSeparators,
): Boolean {
    if (groups.any(String::isEmpty)) return false
    if (groups.any { digitsOnly(it) == null }) return false

    val localeGrouping =
        groups.last().length == separators.primaryGroupingSize &&
            groups.drop(1).dropLast(1).all { it.length == separators.secondaryGroupingSize } &&
            groups.first().length in 1..separators.secondaryGroupingSize
    if (localeGrouping) return true

    // Some platform formatters do not support secondary grouping (for example, en-IN).
    // Accept the conventional three-digit form too, so the displayed value can be re-entered.
    return separators.secondaryGroupingSize != separators.primaryGroupingSize &&
        groups.first().length in 1..separators.primaryGroupingSize &&
        groups.drop(1).all { it.length == separators.primaryGroupingSize }
}

private fun digitsOnly(value: String): String? {
    if (value.isEmpty()) return ""
    val result = StringBuilder(value.length)
    value.forEach { character ->
        val digit = character.digitToIntOrNull() ?: return null
        result.append(digit)
    }
    return result.toString()
}
