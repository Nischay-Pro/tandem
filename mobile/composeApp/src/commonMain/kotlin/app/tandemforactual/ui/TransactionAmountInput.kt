package app.tandemforactual.ui

import kotlin.math.abs
import kotlin.math.roundToLong

internal fun appendAmountDigit(
    currentMinorUnits: Long,
    digit: Int,
): Long {
    require(digit in 0..9)
    val current = if (currentMinorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(currentMinorUnits)
    val next = if (current > (Long.MAX_VALUE - digit) / 10) Long.MAX_VALUE else current * 10 + digit
    return if (currentMinorUnits < 0) -next else next
}

internal fun removeAmountDigit(currentMinorUnits: Long): Long {
    val current = if (currentMinorUnits == Long.MIN_VALUE) Long.MAX_VALUE else abs(currentMinorUnits)
    val next = current / 10
    return if (currentMinorUnits < 0) -next else next
}

internal fun calculateAmountMinorUnits(
    left: Long,
    right: Long,
    operator: String,
): Long? =
    when (operator) {
        "+" -> saturatingAdd(left, right)
        "−" -> saturatingSubtract(left, right)
        "×" -> (left.toDouble() * right.toDouble() / 100.0).roundToLong()
        "÷" -> if (right == 0L) null else (left.toDouble() * 100.0 / right.toDouble()).roundToLong()
        else -> null
    }

private fun saturatingAdd(
    left: Long,
    right: Long,
): Long =
    when {
        right > 0 && left > Long.MAX_VALUE - right -> Long.MAX_VALUE
        right < 0 && left < Long.MIN_VALUE - right -> Long.MIN_VALUE
        else -> left + right
    }

private fun saturatingSubtract(
    left: Long,
    right: Long,
): Long =
    when {
        right < 0 && left > Long.MAX_VALUE + right -> Long.MAX_VALUE
        right > 0 && left < Long.MIN_VALUE + right -> Long.MIN_VALUE
        else -> left - right
    }
