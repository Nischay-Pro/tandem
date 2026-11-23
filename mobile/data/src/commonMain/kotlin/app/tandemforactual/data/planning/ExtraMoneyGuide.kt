package app.tandemforactual.data.planning

/** A savings goal still to fund, for comparing where extra money goes. */
data class GoalGap(
    val id: String,
    val name: String,
    val remainingMinor: Long,
)

/** What one monthly amount would do in each place it could go. Null fields mean Tandem lacks the details to say. */
data class ExtraMoneyComparison(
    val extraMinor: Long,
    /** Employer money gained this year by contributing the extra to the plan; null when no match is set up. */
    val matchGainMinor: Long?,
    val debt: DebtEffect?,
    val buffer: BufferEffect?,
    val goal: GoalEffect?,
    /** Under a month of spending set aside, which is worth knowing before locking money away or paying debt early. */
    val thinBuffer: Boolean,
)

data class DebtEffect(
    /** Interest saved, or null when minimum payments alone would never clear the debt. */
    val interestSavedMinor: Long?,
    val monthsSooner: Int?,
    val monthsWithExtra: Int,
)

data class BufferEffect(
    /** Months of spending covered now, and after a year of setting the extra aside, in tenths of a month. */
    val tenthsNow: Long,
    val tenthsAfterYear: Long,
)

data class GoalEffect(
    val goal: GoalGap,
    /** Months for the extra alone to close the gap. */
    val months: Int,
)

object ExtraMoneyGuide {
    private const val YEAR = 12

    fun compare(
        extraMinor: Long,
        matchGainMinor: Long?,
        debts: List<DebtInput>,
        cashMinor: Long,
        monthlySpendingMinor: Long,
        goals: List<GoalGap>,
    ): ExtraMoneyComparison {
        val extra = extraMinor.coerceAtLeast(0)
        val buffer =
            monthlySpendingMinor.takeIf { it > 0 }?.let { spending ->
                val cash = cashMinor.coerceAtLeast(0)
                BufferEffect(cash * 10 / spending, (cash + extra * YEAR) * 10 / spending)
            }
        return ExtraMoneyComparison(
            extraMinor = extra,
            matchGainMinor = matchGainMinor,
            debt = debtEffect(debts, extra),
            buffer = buffer,
            goal = goalEffect(goals, extra),
            thinBuffer = buffer != null && buffer.tenthsNow < 10,
        )
    }

    private fun debtEffect(
        debts: List<DebtInput>,
        extra: Long,
    ): DebtEffect? {
        if (debts.isEmpty()) return null
        val base = PlanningMath.plan(debts, 0, PayoffStrategy.AVALANCHE)
        val faster = PlanningMath.plan(debts, extra, PayoffStrategy.AVALANCHE) ?: return null
        if (base == null) return DebtEffect(null, null, faster.months)
        return DebtEffect(base.totalInterestMinor - faster.totalInterestMinor, base.months - faster.months, faster.months)
    }

    private fun goalEffect(
        goals: List<GoalGap>,
        extra: Long,
    ): GoalEffect? {
        val goal = goals.filter { it.remainingMinor > 0 }.minByOrNull { it.remainingMinor } ?: return null
        if (extra <= 0) return null
        return GoalEffect(goal, ((goal.remainingMinor + extra - 1) / extra).toInt())
    }
}
