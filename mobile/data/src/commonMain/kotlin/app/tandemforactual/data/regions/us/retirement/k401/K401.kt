package app.tandemforactual.data.regions.us.retirement.k401

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** How a match band is measured: a share of salary, or a fixed amount of your own contributions. */
enum class MatchBand { PERCENT_OF_SALARY, AMOUNT }

/**
 * One step of a match formula: the employer adds [rateBasisPoints] of what you put into the next band. The band is
 * [size] basis points of salary, or [size] minor units of your contributions.
 */
data class MatchTier(
    val rateBasisPoints: Int,
    val band: MatchBand,
    val size: Long,
)

/** An employer's formula: tiers applied in order to your contributions, with an optional yearly cap on the match. */
data class EmployerMatch(
    val tiers: List<MatchTier>,
    val capMinor: Long? = null,
) {
    /** The employer's share for [employeeMinor] contributed in a year on [salaryMinor]. */
    fun on(
        employeeMinor: Long,
        salaryMinor: Long,
    ): Long {
        var remaining = employeeMinor
        var total = 0L
        for (tier in tiers) {
            val band = if (tier.band == MatchBand.PERCENT_OF_SALARY) salaryMinor * tier.size / 10_000 else tier.size
            val portion = minOf(remaining, band).coerceAtLeast(0)
            total += portion * tier.rateBasisPoints / 10_000
            remaining -= portion
        }
        return capMinor?.let { minOf(total, it) } ?: total
    }

    /** What you'd need to put in to collect every dollar of match. */
    fun fullMatchAt(salaryMinor: Long): Long =
        tiers.sumOf { if (it.band == MatchBand.PERCENT_OF_SALARY) salaryMinor * it.size / 10_000 else it.size }

    companion object {
        fun simple(
            rateBasisPoints: Int,
            upToBasisPoints: Int,
        ) = EmployerMatch(listOf(MatchTier(rateBasisPoints, MatchBand.PERCENT_OF_SALARY, upToBasisPoints.toLong())))

        /** Common formulas to start from; any of them can be edited tier by tier. */
        val presets: List<Pair<String, EmployerMatch>> =
            listOf(
                "50% of the first 6% of pay" to simple(5_000, 600),
                "100% of the first 3%, 50% of the next 2%" to
                    EmployerMatch(
                        listOf(MatchTier(10_000, MatchBand.PERCENT_OF_SALARY, 300), MatchTier(5_000, MatchBand.PERCENT_OF_SALARY, 200)),
                    ),
                "100% of the first 4% of pay" to simple(10_000, 400),
                "100% of the first \$1,000, 50% of the next \$1,000" to
                    EmployerMatch(listOf(MatchTier(10_000, MatchBand.AMOUNT, 100_000), MatchTier(5_000, MatchBand.AMOUNT, 100_000))),
            )
    }
}

data class MatchResult(
    val employeeMinor: Long,
    val employerMinor: Long,
    /** Match the employer would add if you contributed enough to get all of it. */
    val missedMinor: Long,
)

/** Employer 401(k), 403(b) and most 457(b) plans share these elective-deferral limits. */
object K401 : RetirementPlan() {
    override val id = "us-401k"
    override val displayName = "401(k)"
    override val region = Region.US
    override val currency = "USD"
    override val description = "Employer plan, including 403(b) and 457(b)"
    override val accountKeywords = listOf("401", "403(b)", "457", "403b")
    override val source = "IRS retirement plan contribution limits (irs.gov)"
    override val catchUpAge = 50
    override val superCatchUpAges = 60..63

    override val limits =
        listOf(
            AnnualLimit(2015, 1_800_000, 600_000, combinedMinor = 5_300_000),
            AnnualLimit(2016, 1_800_000, 600_000, combinedMinor = 5_300_000),
            AnnualLimit(2017, 1_800_000, 600_000, combinedMinor = 5_400_000),
            AnnualLimit(2018, 1_850_000, 600_000, combinedMinor = 5_500_000),
            AnnualLimit(2019, 1_900_000, 600_000, combinedMinor = 5_600_000),
            AnnualLimit(2020, 1_950_000, 650_000, combinedMinor = 5_700_000),
            AnnualLimit(2021, 1_950_000, 650_000, combinedMinor = 5_800_000),
            AnnualLimit(2022, 2_050_000, 650_000, combinedMinor = 6_100_000),
            AnnualLimit(2023, 2_250_000, 750_000, combinedMinor = 6_600_000),
            AnnualLimit(2024, 2_300_000, 750_000, combinedMinor = 6_900_000),
            AnnualLimit(2025, 2_350_000, 750_000, combinedMinor = 7_000_000, superCatchUpMinor = 1_125_000),
            AnnualLimit(2026, 2_450_000, 800_000, combinedMinor = 7_200_000, superCatchUpMinor = 1_125_000),
        )

    /**
     * Splits a year's deposits into a 401(k) account into your part and the employer's, by finding the contribution whose
     * match brings the total to [depositsMinor].
     */
    fun splitDeposits(
        depositsMinor: Long,
        salaryMinor: Long,
        match: EmployerMatch,
    ): Pair<Long, Long> {
        var low = 0L
        var high = depositsMinor
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (mid + match.on(mid, salaryMinor) <= depositsMinor) low = mid else high = mid - 1
        }
        return low to depositsMinor - low
    }

    /** The match on [employeeMinor] contributed this year, capped at the year's limit, and how much more match is available. */
    fun match(
        year: Int,
        age: Int?,
        salaryMinor: Long,
        employeeMinor: Long,
        match: EmployerMatch,
    ): MatchResult {
        val employee = minOf(employeeMinor, allowance(year, age))
        val employer = match.on(employee, salaryMinor)
        val full = match.on(minOf(match.fullMatchAt(salaryMinor), allowance(year, age)), salaryMinor)
        return MatchResult(employee, employer, (full - employer).coerceAtLeast(0))
    }
}
