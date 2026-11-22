package app.tandemforactual.data.regions.india.retirement.ppf

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Public Provident Fund; years are financial years starting in April, named by their first calendar year. */
object Ppf : RetirementPlan() {
    override val id = "in-ppf"
    override val displayName = "PPF"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "Public Provident Fund, per financial year from April"
    override val accountKeywords = listOf("PPF")
    override val source = "Public Provident Fund Scheme, 2019 (National Savings Institute)"

    override val limits = (2015..2026).map { AnnualLimit(it, 15_000_000) }
}
