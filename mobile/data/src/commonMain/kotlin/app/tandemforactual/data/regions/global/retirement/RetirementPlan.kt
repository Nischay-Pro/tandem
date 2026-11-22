package app.tandemforactual.data.regions.global.retirement

import app.tandemforactual.data.regions.Region

/** One year's contribution limits, in minor units of the plan's currency. */
data class AnnualLimit(
    val year: Int,
    val standardMinor: Long,
    val catchUpMinor: Long = 0,
    /** A higher limit for family coverage, as with an HSA. */
    val familyMinor: Long? = null,
    /** Employee and employer together, as with a 401(k)'s overall cap. */
    val combinedMinor: Long? = null,
    /** An extra catch-up for the ages in [RetirementPlan.superCatchUpAges]. */
    val superCatchUpMinor: Long? = null,
)

/** The limit that applies in a year, and whether it is that year's published figure or the latest one known. */
data class LimitLookup(
    val limit: AnnualLimit,
    val published: Boolean,
)

/**
 * A tax-advantaged account type in one region. To add one, create `regions/<region>/retirement/<plan>`, extend this class with
 * the published limits by year, and list it in [RetirementPlans]. A new year is one more [AnnualLimit] row.
 */
abstract class RetirementPlan {
    abstract val id: String
    abstract val displayName: String
    abstract val region: Region
    abstract val currency: String
    abstract val description: String

    /** Where the limits come from, so they can be checked each year. */
    abstract val source: String
    abstract val limits: List<AnnualLimit>
    open val catchUpAge: Int? = null
    open val superCatchUpAges: IntRange? = null

    /** That year's limits, or the closest earlier year's when the year isn't published yet, flagged as such. */
    fun limitFor(year: Int): LimitLookup {
        val sorted = limits.sortedBy { it.year }
        sorted.firstOrNull { it.year == year }?.let { return LimitLookup(it, true) }
        val fallback = sorted.lastOrNull { it.year < year } ?: sorted.first()
        return LimitLookup(fallback, false)
    }

    /** How much one person may put in for [year] at [age], including any catch-up. */
    fun allowance(
        year: Int,
        age: Int?,
        family: Boolean = false,
    ): Long {
        val limit = limitFor(year).limit
        val base = if (family) limit.familyMinor ?: limit.standardMinor else limit.standardMinor
        val superCatchUp = limit.superCatchUpMinor?.takeIf { age != null && superCatchUpAges?.contains(age) == true }
        val catchUp = superCatchUp ?: limit.catchUpMinor.takeIf { age != null && catchUpAge?.let { age >= it } == true } ?: 0
        return base + catchUp
    }

    /** What's left to contribute this year after [contributedMinor], never below zero. */
    fun room(
        year: Int,
        age: Int?,
        contributedMinor: Long,
        family: Boolean = false,
    ): Long = (allowance(year, age, family) - contributedMinor).coerceAtLeast(0)
}
