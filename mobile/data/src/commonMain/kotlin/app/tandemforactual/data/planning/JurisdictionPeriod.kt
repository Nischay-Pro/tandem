package app.tandemforactual.data.planning

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlans
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a [JurisdictionPeriod] records. Each is kept apart: living somewhere doesn't decide where you're taxed. */
enum class PeriodKind(
    val label: String,
) {
    RESIDENCE("Lived in"),
    EMPLOYMENT("Worked in"),
    TAX_RESIDENCE("Tax resident of"),
}

/** A stretch of months in one country, from [from] through [through] (yyyy-MM), or still going when [through] is null. */
data class JurisdictionPeriod(
    val id: String,
    val kind: PeriodKind,
    val region: Region,
    val from: String,
    val through: String? = null,
) {
    val isCurrent: Boolean get() = through == null

    fun covers(month: String): Boolean = month >= from && (through == null || month <= through)

    fun toJson(): JsonObject =
        jsonOf(
            "kind" to JsonPrimitive(kind.name),
            "region" to JsonPrimitive(region.name),
            "from" to JsonPrimitive(from),
            "through" to through.json(),
        )

    companion object {
        const val TYPE = "profile.jurisdiction_period"

        fun fromJson(
            id: String,
            body: JsonObject,
        ): JurisdictionPeriod? {
            val kind = PeriodKind.entries.firstOrNull { it.name == body.text("kind") } ?: return null
            val region = Region.entries.firstOrNull { it.name == body.text("region") } ?: return null
            val from = body.text("from") ?: return null
            return JurisdictionPeriod(id, kind, region, from, body.text("through"))
        }
    }
}

/** One thing to look at after a move. Nothing here changes an account; each step is for the person to decide. */
sealed interface RelocationStep {
    /** A plan set up for the old country that you still own; whether you can keep paying in depends on its rules. */
    data class ReviewContributions(
        val planId: String,
    ) : RelocationStep

    /** A plan run by an employer in the old country; contributions usually stop when that job ends, the balance stays. */
    data class EmployerPlanEnds(
        val planId: String,
    ) : RelocationStep

    /** A plan in the new country that isn't set up yet. */
    data class ConsiderPlan(
        val planId: String,
    ) : RelocationStep

    /** The year of the move usually counts in both countries' tax years. */
    data class SplitTaxYear(
        val from: Region,
        val to: Region,
    ) : RelocationStep

    /** Accounts opened in the new country keep their own currency. */
    data class NewCurrency(
        val currency: String,
    ) : RelocationStep
}

object JurisdictionTimeline {
    private val employerPlans = setOf("us-401k", "us-health-fsa", "in-epf")
    private val currencies =
        mapOf(
            Region.US to "USD",
            Region.CA to "CAD",
            Region.EU to "EUR",
            Region.UK to "GBP",
            Region.BR to "BRL",
            Region.NZ to "NZD",
            Region.IN to "INR",
        )

    fun currencyOf(region: Region): String? = currencies[region]

    /** Countries [kind] periods cover in [month]. */
    fun regionsIn(
        periods: List<JurisdictionPeriod>,
        kind: PeriodKind,
        month: String,
    ): List<Region> = periods.filter { it.kind == kind && it.covers(month) }.map { it.region }.distinct()

    /** Periods of the same kind in different countries that share a month, such as two tax residences at once. */
    fun overlaps(periods: List<JurisdictionPeriod>): List<Pair<JurisdictionPeriod, JurisdictionPeriod>> =
        periods.sortedBy { it.from }.let { sorted ->
            sorted.flatMapIndexed { index, first ->
                sorted
                    .drop(index + 1)
                    .filter { second ->
                        second.kind == first.kind && second.region != first.region && first.covers(second.from)
                    }.map { first to it }
            }
        }

    /**
     * The periods to save for a move to [to] starting [month]: where you live now ends the month before, and a new
     * residence starts. With [newJob], current employment ends too and a new one starts. Tax residence is never
     * changed for you, since it's a claim only you can make.
     */
    fun move(
        periods: List<JurisdictionPeriod>,
        to: Region,
        month: String,
        newJob: Boolean,
        newId: () -> String,
    ): List<JurisdictionPeriod> {
        val before = SpendingInsights.shiftMonth(month, -1)
        val kinds = if (newJob) listOf(PeriodKind.RESIDENCE, PeriodKind.EMPLOYMENT) else listOf(PeriodKind.RESIDENCE)
        return kinds.flatMap { kind ->
            val ended =
                periods.filter { it.kind == kind && it.isCurrent && it.region != to }.map {
                    it.copy(
                        through = maxOf(before, it.from),
                    )
                }
            val alreadyThere = periods.any { it.kind == kind && it.isCurrent && it.region == to }
            ended + if (alreadyThere) emptyList() else listOf(JurisdictionPeriod(newId(), kind, to, month))
        }
    }

    /** What to look at when moving from [from] to [to], given the plans set up so far. */
    fun review(
        from: Region?,
        to: Region,
        setUpPlanIds: Set<String>,
    ): List<RelocationStep> {
        val old =
            setUpPlanIds
                .mapNotNull(RetirementPlans::byId)
                .filter { it.region != to }
                .map { if (it.id in employerPlans) RelocationStep.EmployerPlanEnds(it.id) else RelocationStep.ReviewContributions(it.id) }
        val new = RetirementPlans.forRegion(to).filter { it.id !in setUpPlanIds }.map { RelocationStep.ConsiderPlan(it.id) }
        val tax = if (from != null && from != to) listOf(RelocationStep.SplitTaxYear(from, to)) else emptyList()
        val currency = currencyOf(to)?.takeIf { from == null || it != currencyOf(from) }?.let { listOf(RelocationStep.NewCurrency(it)) }
        return old + new + tax + currency.orEmpty()
    }
}
