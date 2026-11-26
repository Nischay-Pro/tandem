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
}
