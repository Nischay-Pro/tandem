package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.ca.retirement.fhsa.Fhsa
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.ca.retirement.tfsa.Tfsa
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.us.retirement.k401.K401

/** Where a plan stands in one of its years. */
data class PlanYearRoom(
    val allowanceMinor: Long,
    val yoursMinor: Long,
    val employerMinor: Long,
    val roomMinor: Long,
    /** The room includes unused room carried in from earlier years. */
    val carriesForward: Boolean = false,
)

/** Works out a plan's room for a year from the person's entries and the linked accounts' movements. */
object RetirementRoom {
    /** Adds up several accounts' yearly totals, for plans that share one room. */
    fun merge(accounts: List<Map<Int, YearMovements>>): Map<Int, YearMovements> =
        accounts.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, list) ->
            list.reduce { a, b ->
                YearMovements(
                    a.ownMinor + b.ownMinor,
                    a.employerMinor + b.employerMinor,
                    a.growthMinor + b.growthMinor,
                    a.rolloverMinor + b.rolloverMinor,
                    a.withdrawnMinor + b.withdrawnMinor,
                    listOfNotNull(a.lastOwnDate, b.lastOwnDate).maxOrNull(),
                )
            }
        }

    /** Your part and the employer's in [year]: employer deposits named as such, else a 401(k)'s match worked backwards. */
    fun split(
        plan: RetirementPlan,
        profile: RetirementProfile,
        year: Int,
        totals: YearMovements,
    ): Pair<Long, Long> {
        val match = profile.match
        val salary = profile.salaryMinor
        return when {
            plan != K401 || totals.employerMinor > 0 || match == null || salary == null -> totals.ownMinor to totals.employerMinor
            else -> K401.splitDeposits(year, totals.ownMinor, salary, match, totals.lastOwnDate)
        }
    }

    /** [currentYear] is the plan year today falls in, the only one an RRSP notice of assessment speaks for. */
    fun forYear(
        plan: RetirementPlan,
        profile: RetirementProfile,
        year: Int,
        years: Map<Int, YearMovements>,
        currentYear: Int,
    ): PlanYearRoom {
        val (yours, employer) = split(plan, profile, year, years[year] ?: YearMovements())
        val own = years.mapValues { it.value.ownMinor }
        val start = profile.roomStart
        val carried =
            when {
                year in profile.limitOverrides -> null
                plan == Tfsa && start != null ->
                    Tfsa.roomLeft(
                        year,
                        start.year,
                        start.amountMinor,
                        own,
                        years.mapValues { it.value.withdrawnMinor },
                    )
                plan == Fhsa && (start != null || years.isNotEmpty()) -> {
                    val opened = listOfNotNull(years.keys.minOrNull(), start?.year).min()
                    Fhsa.roomLeft(year, opened, own, start?.year, start?.amountMinor)
                }
                else -> null
            }
        if (carried != null) return PlanYearRoom(carried + yours, yours, employer, carried.coerceAtLeast(0), carriesForward = true)
        val ownRrspRoom = profile.rrspRoom(year).takeIf { plan == Rrsp && year == currentYear && year !in profile.limitOverrides }
        val allowance = ownRrspRoom ?: profile.allowance(plan, year)
        val room = if (plan == K401) K401.roomLeft(year, allowance, yours, employer) else allowance - yours
        return PlanYearRoom(allowance, yours, employer, room.coerceAtLeast(0), carriesForward = ownRrspRoom != null)
    }

    /** All employer money put in up to the end of [throughYear], which vesting applies to as a whole. */
    fun employerThrough(
        plan: RetirementPlan,
        profile: RetirementProfile,
        years: Map<Int, YearMovements>,
        throughYear: Int,
    ): Long = years.filterKeys { it <= throughYear }.entries.sumOf { (year, totals) -> split(plan, profile, year, totals).second }
}
