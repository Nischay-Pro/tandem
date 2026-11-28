package app.tandemforactual.data.planning

/** A payoff estimate from supplied terms only: whole months and total interest, rounded to the minor unit. */
data class PayoffEstimate(
    val months: Int,
    val totalInterestMinor: Long,
)

data class DebtInput(
    val id: String,
    val balanceMinor: Long,
    val aprBasisPoints: Int,
    val minimumMinor: Long,
)

/** One month of a loan's repayment: what was paid, how it split into interest and principal, and what was left. */
data class AmortisationRow(
    val month: Int,
    val paymentMinor: Long,
    val interestMinor: Long,
    val principalMinor: Long,
    val balanceMinor: Long,
)

enum class PayoffStrategy { AVALANCHE, SNOWBALL }

/** A month-by-month payoff: when each debt is cleared, the total interest, and the remaining balance after each month. */
data class DebtPlan(
    val months: Int,
    val totalInterestMinor: Long,
    val payoffMonth: Map<String, Int>,
    val order: List<String>,
    val balances: List<Long>,
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

    /**
     * The month-by-month repayment of [balanceMinor] at [aprBasisPoints] with a fixed [paymentMinor], the last month
     * paying only what is left. Null for the same reasons [payoff] is.
     */
    fun amortisation(
        balanceMinor: Long,
        aprBasisPoints: Int,
        paymentMinor: Long,
    ): List<AmortisationRow>? {
        if (payoff(balanceMinor, aprBasisPoints, paymentMinor) == null) return null
        val rows = mutableListOf<AmortisationRow>()
        var balance = balanceMinor
        while (balance > 0) {
            val charge = roundDiv(balance * aprBasisPoints, 120_000)
            val payment = minOf(paymentMinor, balance + charge)
            balance = balance + charge - payment
            rows += AmortisationRow(rows.size + 1, payment, charge, payment - charge, balance)
        }
        return rows
    }

    /**
     * Pays every minimum each month and puts [extraMinor], plus minimums freed by debts already paid off, on the target
     * debt: highest APR first for avalanche, smallest balance first for snowball. Null when the payments never cover the
     * interest or the plan would run past fifty years.
     */
    fun plan(
        debts: List<DebtInput>,
        extraMinor: Long,
        strategy: PayoffStrategy,
    ): DebtPlan? {
        val order =
            when (strategy) {
                PayoffStrategy.AVALANCHE -> {
                    debts.sortedWith(
                        compareByDescending<DebtInput> { it.aprBasisPoints }.thenBy { it.balanceMinor },
                    )
                }

                PayoffStrategy.SNOWBALL -> {
                    debts.sortedWith(compareBy<DebtInput> { it.balanceMinor }.thenByDescending { it.aprBasisPoints })
                }
            }.map { it.id }
        val balance = debts.associate { it.id to it.balanceMinor.coerceAtLeast(0) }.toMutableMap()
        val budget = debts.sumOf { it.minimumMinor } + extraMinor.coerceAtLeast(0)
        val payoffMonth = balance.filterValues { it == 0L }.mapValues { 0 }.toMutableMap()
        val balances = mutableListOf<Long>()
        var interest = 0L
        var month = 0
        while (balance.values.any { it > 0 }) {
            month++
            if (month > MAX_MONTHS) return null
            var available = budget
            var charged = 0L
            for (debt in debts) {
                val owed = balance.getValue(debt.id)
                if (owed == 0L) continue
                val charge = roundDiv(owed * debt.aprBasisPoints, 120_000)
                charged += charge
                val pay = minOf(owed + charge, debt.minimumMinor)
                balance[debt.id] = owed + charge - pay
                available -= pay
            }
            if (available <= 0 && charged >= budget) return null
            interest += charged
            for (id in order) {
                if (available <= 0) break
                val pay = minOf(balance.getValue(id), available)
                balance[id] = balance.getValue(id) - pay
                available -= pay
            }
            balance.filterValues { it == 0L }.keys.forEach { payoffMonth.getOrPut(it) { month } }
            balances += balance.values.sum()
        }
        return DebtPlan(month, interest, payoffMonth, order, balances)
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
