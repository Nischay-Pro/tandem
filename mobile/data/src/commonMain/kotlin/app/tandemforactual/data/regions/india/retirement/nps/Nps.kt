package app.tandemforactual.data.regions.india.retirement.nps

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** The extra NPS deduction under section 80CCD(1B), per financial year from April. */
object Nps : RetirementPlan() {
    override val id = "in-nps"
    override val displayName = "NPS (80CCD(1B))"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "National Pension System, extra deduction beyond section 80C"
    override val accountKeywords = listOf("NPS", "Pension")
    override val source = "Income-tax Act section 80CCD(1B) (incometaxindia.gov.in)"

    override val limits = (2015..2026).map { AnnualLimit(it, 5_000_000) }
}
