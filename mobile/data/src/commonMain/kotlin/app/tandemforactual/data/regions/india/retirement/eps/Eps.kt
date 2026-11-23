package app.tandemforactual.data.regions.india.retirement.eps

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Employees' Pension Scheme: 8.33% of the employer's share, on wages up to ₹15,000 a month. */
object Eps : RetirementPlan() {
    override val id = "in-eps"
    override val displayName = "EPS"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "Employees’ Pension Scheme, the pension part of your employer’s PF share"
    override val accountKeywords = listOf("EPS", "Pension Scheme")
    override val source = "Employees’ Pension Scheme, 1995 (epfindia.gov.in)"
    override val advanced = true

    override val limits = (2015..2026).map { AnnualLimit(it, 1_500_000) }
}
