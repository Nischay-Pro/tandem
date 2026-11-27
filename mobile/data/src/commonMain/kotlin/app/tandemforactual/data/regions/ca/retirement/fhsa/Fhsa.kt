package app.tandemforactual.data.regions.ca.retirement.fhsa

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** First home savings account: $8,000 a year up to $40,000 for life, from April 2023. */
object Fhsa : RetirementPlan() {
    override val id = "ca-fhsa"
    override val displayName = "FHSA"
    override val region = Region.CA
    override val currency = "CAD"
    override val description = "First home savings account, $8,000 a year up to $40,000 for life"
    override val accountKeywords = listOf("FHSA", "First home")
    override val source = "CRA First Home Savings Account (canada.ca)"
    override val advanced = true

    override val limits = (2023..2027).map { AnnualLimit(it, 800_000) }

    const val LIFETIME_MINOR = 4_000_000L
    const val CARRY_FORWARD_MINOR = 800_000L

    /**
     * Room left in [year] for an FHSA opened in [openedYear]: the yearly limit plus up to one year's unused room carried
     * forward, never more than what's left of the lifetime limit. [startRoomMinor], when known, replaces the worked-out
     * room for [startYear] with the figure CRA shows. Withdrawals don't give room back. Negative means over-contributed.
     */
    fun roomLeft(
        year: Int,
        openedYear: Int,
        contributedByYear: Map<Int, Long>,
        startYear: Int? = null,
        startRoomMinor: Long? = null,
    ): Long {
        if (year < openedYear) return 0
        var lifetimeUsed = 0L
        var room = 0L
        for (y in openedYear..year) {
            val carry = if (y == openedYear) 0 else (room - (contributedByYear[y - 1] ?: 0)).coerceIn(0, CARRY_FORWARD_MINOR)
            if (y > openedYear) lifetimeUsed += contributedByYear[y - 1] ?: 0
            room = minOf(limitFor(y).limit.standardMinor + carry, (LIFETIME_MINOR - lifetimeUsed).coerceAtLeast(0))
            if (y == startYear && startRoomMinor != null) room = startRoomMinor
        }
        return room - (contributedByYear[year] ?: 0)
    }
}
