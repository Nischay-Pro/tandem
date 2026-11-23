package app.tandemforactual.ui.feature.planning

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Step sizes for the what-if rows, scaled to the household's own month so they mean the same thing in any currency:
 * about 5%, 10% and 20% of the larger of income and spending, rounded to amounts people would type.
 */
internal object WhatIfSteps {
    const val CUSTOM_LESS = Long.MIN_VALUE
    const val CUSTOM_MORE = Long.MAX_VALUE

    private val shares = listOf(0.05, 0.10, 0.20)
    private val niceMantissas = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

    /** A typical monthly income in major units, adjusted for purchasing power, for months with nothing recorded. */
    private val typicalMonth =
        mapOf(
            "INR" to 50_000.0,
            "JPY" to 300_000.0,
            "KRW" to 3_000_000.0,
            "IDR" to 8_000_000.0,
            "VND" to 12_000_000.0,
            "PHP" to 30_000.0,
            "BRL" to 4_000.0,
            "MXN" to 15_000.0,
            "ZAR" to 15_000.0,
            "TRY" to 30_000.0,
            "PKR" to 80_000.0,
            "NGN" to 300_000.0,
            "CNY" to 10_000.0,
            "CHF" to 6_000.0,
            "SEK" to 30_000.0,
            "NOK" to 35_000.0,
            "DKK" to 25_000.0,
            "PLN" to 7_000.0,
        )
    private const val DEFAULT_TYPICAL_MONTH = 3_000.0

    fun steps(
        monthlyIncomeMinor: Long,
        monthlySpendingMinor: Long,
        currencyCode: String?,
        fractionDigits: Int,
    ): List<Long> {
        val unit = 10.0.pow(fractionDigits)
        val baseMajor =
            maxOf(monthlyIncomeMinor, monthlySpendingMinor).takeIf { it > 0 }?.let { it / unit }
                ?: typicalMonth[currencyCode?.uppercase()] ?: DEFAULT_TYPICAL_MONTH
        val up = shares.map { (nice(baseMajor * it) * unit).toLong().coerceAtLeast(1) }.distinct()
        return up.reversed().map { -it } + 0L + up
    }

    /** The nearest of 1, 2, 2.5, 5 times a power of ten. */
    internal fun nice(value: Double): Double {
        if (value <= 0) return 1.0
        val magnitude = 10.0.pow(floor(log10(value)))
        return niceMantissas.minBy { kotlin.math.abs(it * magnitude - value) } * magnitude
    }
}
