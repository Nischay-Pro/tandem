package app.tandemforactual.data.planning

/** A payoff estimate from supplied terms only: whole months and total interest, rounded to the minor unit. */
data class PayoffEstimate(
    val months: Int,
    val totalInterestMinor: Long,
)

object PlanningMath {
    private const val MAX_MONTHS = 600

    /**
     * Months to repay [balanceMinor] at [aprBasisPoints] with a fixed [paymentMinor], compounding monthly. Null when the
     * payment never covers the interest or the debt would outlast fifty years, so no false payoff date is shown.
     */
    fun payoff(
        balanceMinor: Long,
        aprBasisPoints: Int,
        paymentMinor: Long,
    ): PayoffEstimate? {
        if (balanceMinor <= 0) return PayoffEstimate(0, 0)
        if (paymentMinor <= 0) return null
        var balance = balanceMinor
        var interest = 0L
        var months = 0
        while (balance > 0) {
            val charge = roundDiv(balance * aprBasisPoints, 120_000)
            if (charge >= paymentMinor) return null
            interest += charge
            balance = balance + charge - paymentMinor
            months++
            if (months > MAX_MONTHS) return null
        }
        return PayoffEstimate(months, interest)
    }

    /** How much to set aside each month to reach [targetMinor] from [savedMinor] in [monthsLeft] months, rounded up. */
    fun monthlyToTarget(
        targetMinor: Long,
        savedMinor: Long,
        monthsLeft: Int,
    ): Long {
        val remaining = (targetMinor - savedMinor).coerceAtLeast(0)
        if (remaining == 0L) return 0
        val months = monthsLeft.coerceAtLeast(1)
        return (remaining + months - 1) / months
    }

    /** Whole months from [fromIsoDate] to [toIsoDate] (yyyy-MM-dd), counting a partial month as one. */
    fun monthsBetween(
        fromIsoDate: String,
        toIsoDate: String,
    ): Int {
        val (fy, fm, fd) = fromIsoDate.split('-').map(String::toInt)
        val (ty, tm, td) = toIsoDate.split('-').map(String::toInt)
        val months = (ty - fy) * 12 + (tm - fm) + if (td > fd) 1 else 0
        return months.coerceAtLeast(0)
    }

    private fun roundDiv(
        value: Long,
        divisor: Long,
    ): Long = (value + divisor / 2) / divisor
}
