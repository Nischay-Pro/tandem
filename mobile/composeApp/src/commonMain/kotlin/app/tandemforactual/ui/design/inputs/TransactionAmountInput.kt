package app.tandemforactual.ui.design.inputs

import kotlin.math.abs

// Kept in sync with MAX_TRANSACTION_AMOUNT in the API reconciliation layer.
internal const val MAX_TRANSACTION_AMOUNT_MINOR_UNITS = 1_000_000_000L

internal fun isValidTransferDestination(
    sourceAccountId: String,
    destinationAccountId: String?,
): Boolean =
    sourceAccountId.isNotBlank() &&
        !destinationAccountId.isNullOrBlank() &&
        sourceAccountId != destinationAccountId

internal fun transactionAmountInputMagnitude(minorUnits: Long?): Long {
    val magnitude =
        when (minorUnits) {
            null -> 0L
            Long.MIN_VALUE -> Long.MAX_VALUE
            else -> abs(minorUnits)
        }
    return magnitude.coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
}

internal fun amountDigitExceedsLimit(
    currentMinorUnits: Long,
    digit: Int,
): Boolean {
    require(digit in 0..9)
    val current =
        (if (currentMinorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(currentMinorUnits))
            .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
    return current > (MAX_TRANSACTION_AMOUNT_MINOR_UNITS - digit) / 10
}

internal fun appendAmountDigit(
    currentMinorUnits: Long,
    digit: Int,
): Long {
    require(digit in 0..9)
    val current =
        (if (currentMinorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(currentMinorUnits))
            .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
    val next =
        if (amountDigitExceedsLimit(current, digit)) {
            MAX_TRANSACTION_AMOUNT_MINOR_UNITS
        } else {
            current * 10 + digit
        }
    return if (currentMinorUnits < 0) -next else next
}

internal fun removeAmountDigit(currentMinorUnits: Long): Long {
    val current =
        (if (currentMinorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(currentMinorUnits))
            .coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
    val next = current / 10
    return if (currentMinorUnits < 0) -next else next
}

internal fun calculateAmountMinorUnits(
    left: Long,
    right: Long,
    operator: String,
    fractionDigits: Int = 2,
): Long? {
    if (fractionDigits !in 0..9) return null
    if (left !in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS ||
        right !in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS
    ) {
        return null
    }
    val scale = decimalScale(fractionDigits)
    val result =
        when (operator) {
            "+" -> left + right
            "−" -> left - right
            "×" -> divideRounded(left * right, scale)
            "÷" -> if (right == 0L) null else divideRounded(left * scale, right)
            else -> null
        }
    return result?.takeIf { it in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS }
}

/** The amount once the decimal separator is typed: what was typed becomes whole units, or null past the largest amount. */
internal fun startAmountFraction(
    currentMinorUnits: Long,
    fractionDigits: Int,
): Long? {
    val whole = transactionAmountInputMagnitude(currentMinorUnits)
    val scale = decimalScale(fractionDigits)
    return (whole * scale).takeIf { whole <= MAX_TRANSACTION_AMOUNT_MINOR_UNITS / scale }
}

/** Fills the next fraction place after the decimal separator, from the left. */
internal fun appendAmountFractionDigit(
    currentMinorUnits: Long,
    digit: Int,
    fractionDigitsTyped: Int,
    fractionDigits: Int,
): Long {
    require(digit in 0..9)
    val current = transactionAmountInputMagnitude(currentMinorUnits)
    if (fractionDigitsTyped >= fractionDigits) return current
    return (current + digit * decimalScale(fractionDigits - 1 - fractionDigitsTyped)).coerceAtMost(MAX_TRANSACTION_AMOUNT_MINOR_UNITS)
}

/** Clears the last typed fraction place; with none typed, undoes the decimal separator. */
internal fun removeAmountFractionDigit(
    currentMinorUnits: Long,
    fractionDigitsTyped: Int,
    fractionDigits: Int,
): Long {
    val current = transactionAmountInputMagnitude(currentMinorUnits)
    if (fractionDigitsTyped <= 0) return current / decimalScale(fractionDigits)
    val place = decimalScale(fractionDigits - fractionDigitsTyped)
    return current - (current / place % 10) * place
}

private fun decimalScale(fractionDigits: Int): Long {
    var scale = 1L
    repeat(fractionDigits) { scale *= 10L }
    return scale
}

private fun divideRounded(
    numerator: Long,
    denominator: Long,
): Long {
    val quotient = numerator / denominator
    val remainder = numerator % denominator
    if (remainder == 0L || kotlin.math.abs(remainder) * 2 < kotlin.math.abs(denominator)) return quotient
    return quotient + if ((numerator < 0) xor (denominator < 0)) -1 else 1
}
