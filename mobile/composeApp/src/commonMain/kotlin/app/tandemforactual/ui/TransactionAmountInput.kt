package app.tandemforactual.ui

import kotlin.math.abs
import kotlin.math.roundToLong

// Kept in sync with MAX_TRANSACTION_AMOUNT in the API reconciliation layer.
internal const val MAX_TRANSACTION_AMOUNT_MINOR_UNITS = 1_000_000_000L

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
): Long? {
    if (left !in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS ||
        right !in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS
    ) {
        return null
    }
    val result =
        when (operator) {
            "+" -> left + right
            "−" -> left - right
            "×" -> (left.toDouble() * right.toDouble() / 100.0).roundToLong()
            "÷" -> if (right == 0L) null else (left.toDouble() * 100.0 / right.toDouble()).roundToLong()
            else -> null
        }
    return result?.takeIf { it in -MAX_TRANSACTION_AMOUNT_MINOR_UNITS..MAX_TRANSACTION_AMOUNT_MINOR_UNITS }
}
