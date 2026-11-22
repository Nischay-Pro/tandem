package app.tandemforactual.data.regions.us.retirement.k401

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** An employer's match: [rateBasisPoints] of what you put in, on contributions up to [upToBasisPoints] of salary. */
data class EmployerMatch(
    val rateBasisPoints: Int,
    val upToBasisPoints: Int,
)

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

    /** The year's contribution at [contributionBasisPoints] of salary, capped at the year's limit, and the employer match on it. */
    fun match(
        year: Int,
        age: Int?,
        salaryMinor: Long,
        contributionBasisPoints: Int,
        match: EmployerMatch,
    ): MatchResult {
        val employee = minOf(salaryMinor * contributionBasisPoints / 10_000, allowance(year, age))
        val matchable = salaryMinor * match.upToBasisPoints / 10_000
        val employer = minOf(employee, matchable) * match.rateBasisPoints / 10_000
        val full = matchable * match.rateBasisPoints / 10_000
        return MatchResult(employee, employer, (full - employer).coerceAtLeast(0))
    }
}
