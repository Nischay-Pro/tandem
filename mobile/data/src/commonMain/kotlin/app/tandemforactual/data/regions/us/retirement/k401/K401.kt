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

/** One step of a vesting schedule: after [years] of service, [basisPoints] of the employer's money is yours. */
data class VestingStep(
    val years: Int,
    val basisPoints: Int,
)

/** When employer money becomes yours, as steps by years of service. No steps means it's yours right away. */
data class Vesting(
    val steps: List<VestingStep>,
) {
    /** The share vested after [service] full years, in basis points. */
    fun vestedBasisPoints(service: Int): Int =
        if (steps.isEmpty()) 10_000 else steps.filter { it.years <= service }.maxOfOrNull { it.basisPoints } ?: 0

    /** Years of service until all of it is yours, or null when no step reaches 100%. */
    val fullYears: Int? get() = if (steps.isEmpty()) 0 else steps.filter { it.basisPoints >= 10_000 }.minOfOrNull { it.years }

    companion object {
        val IMMEDIATE = Vesting(emptyList())

        /** All of it after [years]. */
        fun cliff(years: Int) = Vesting(listOf(VestingStep(years, 10_000)))

        /** Starting points; three-year cliff and six-year graded are the slowest the law allows. Any can be edited step by step. */
        val presets: List<Pair<String, Vesting>> =
            listOf(
                "Right away" to IMMEDIATE,
                "3-year cliff" to cliff(3),
                "6-year graded" to Vesting((2..6).map { VestingStep(it, (it - 1) * 2_000) }),
            )
    }
}

/**
 * An employer's formula: tiers applied in order to your contributions, with an optional yearly cap on the match, how the
 * match vests, and the month you joined to count service from.
 */
data class EmployerMatch(
    val tiers: List<MatchTier>,
    val capMinor: Long? = null,
    val vesting: Vesting = Vesting.IMMEDIATE,
    val joinedYear: Int? = null,
    /** 1 to 12; when unknown, service is counted from the end of [joinedYear] so it is never overstated. */
    val joinedMonth: Int? = null,
) {
    /** Full years of service on [asOf] (an ISO date or `yyyy-MM`), or null when the year you joined isn't known. */
    fun serviceYears(asOf: String): Int? =
        joinedYear?.let { joined ->
            val months = (asOf.take(4).toInt() - joined) * 12 + asOf.substring(5, 7).toInt() - (joinedMonth ?: 12)
            (months / 12).coerceAtLeast(0)
        }

    /** The share of employer money that's yours on [asOf], or null when the year you joined isn't known. */
    fun vestedBasisPoints(asOf: String): Int? =
        if (vesting == Vesting.IMMEDIATE) 10_000 else serviceYears(asOf)?.let(vesting::vestedBasisPoints)

    /** The month (`yyyy-MM`) all employer money is yours, or null when it already is or the year you joined isn't known. */
    val fullyVestedMonth: String? get() =
        joinedYear?.takeIf { vesting != Vesting.IMMEDIATE }?.let { joined ->
            vesting.fullYears?.let { "${joined + it}-${(joinedMonth ?: 12).toString().padStart(2, '0')}" }
        }

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
    /** False when the year's limit isn't published yet and the latest known one was used. */
    val published: Boolean,
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
            AnnualLimit(2027, 2_550_000, 850_000, combinedMinor = 7_500_000, superCatchUpMinor = 1_175_000),
        )

    /** The 401(a)(17) limit on pay that a match can be worked out from, by year. */
    val compensationLimits: List<AnnualLimit> =
        listOf(
            2015 to 26_500_000L,
            2016 to 26_500_000L,
            2017 to 27_000_000L,
            2018 to 27_500_000L,
            2019 to 28_000_000L,
            2020 to 28_500_000L,
            2021 to 29_000_000L,
            2022 to 30_500_000L,
            2023 to 33_000_000L,
            2024 to 34_500_000L,
            2025 to 35_000_000L,
            2026 to 36_000_000L,
            2027 to 37_500_000L,
        ).map { (year, amount) -> AnnualLimit(year, amount) }

    /** [salaryMinor] capped at the year's 401(a)(17) limit. */
    fun matchablePay(
        year: Int,
        salaryMinor: Long,
    ): Long {
        val cap = compensationLimits.lastOrNull { it.year <= year } ?: compensationLimits.first()
        return minOf(salaryMinor, cap.standardMinor)
    }

    /**
     * Splits deposits made in [year] into your part and the employer's, by finding the contribution whose match brings the
     * total to [depositsMinor]. Pay-based tiers are matched per paycheck, so up to [throughDate] only that share of the
     * year's pay counts; dollar tiers and the cap stay yearly.
     */
    fun splitDeposits(
        year: Int,
        depositsMinor: Long,
        salaryMinor: Long,
        match: EmployerMatch,
        throughDate: String? = null,
    ): Pair<Long, Long> {
        val pay = payThrough(year, matchablePay(year, salaryMinor), throughDate)
        var low = 0L
        var high = depositsMinor
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (mid + match.on(mid, pay) <= depositsMinor) low = mid else high = mid - 1
        }
        return low to depositsMinor - low
    }

    /** The match on [employeeMinor] contributed this year, capped at the year's limits, and how much more match is available. */
    fun match(
        year: Int,
        age: Int?,
        salaryMinor: Long,
        employeeMinor: Long,
        match: EmployerMatch,
    ): MatchResult {
        val pay = matchablePay(year, salaryMinor)
        val allowance = allowance(year, age)
        val employee = minOf(employeeMinor, allowance)
        val employer = withinAdditionsLimit(year, employee, match.on(employee, pay))
        val fullEmployee = minOf(match.fullMatchAt(pay), allowance)
        val full = withinAdditionsLimit(year, fullEmployee, match.on(fullEmployee, pay))
        return MatchResult(employee, employer, (full - employer).coerceAtLeast(0), limitFor(year).published)
    }

    /** Employer money capped so it and your own non-catch-up deferrals stay within the 415(c) annual additions limit. */
    fun withinAdditionsLimit(
        year: Int,
        employeeMinor: Long,
        employerMinor: Long,
    ): Long {
        val limit = limitFor(year).limit
        val combined = limit.combinedMinor ?: return employerMinor
        return minOf(employerMinor, (combined - minOf(employeeMinor, limit.standardMinor)).coerceAtLeast(0))
    }

    /**
     * What you may still defer this year: the deferral room, also held under the 415(c) limit on everything that goes in,
     * which catch-up contributions sit on top of.
     */
    fun roomLeft(
        year: Int,
        allowanceMinor: Long,
        employeeMinor: Long,
        employerMinor: Long,
    ): Long {
        val deferral = allowanceMinor - employeeMinor
        val limit = limitFor(year).limit
        val combined = limit.combinedMinor ?: return deferral.coerceAtLeast(0)
        val catchUp = (allowanceMinor - limit.standardMinor).coerceAtLeast(0)
        return minOf(deferral, combined + catchUp - employeeMinor - employerMinor).coerceAtLeast(0)
    }

    private fun payThrough(
        year: Int,
        payMinor: Long,
        throughDate: String?,
    ): Long {
        if (throughDate == null || throughDate.take(4).toInt() != year) return payMinor
        val month = throughDate.substring(5, 7).toInt()
        val day = throughDate.substring(8, 10).toInt()
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val dayOfYear = MONTH_STARTS[month - 1] + day + if (leap && month > 2) 1 else 0
        return payMinor * dayOfYear / if (leap) 366 else 365
    }

    private val MONTH_STARTS = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
}
