package app.tandemforactual.data.regions.india.retirement.vpf

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Voluntary Provident Fund; interest is tax-free on up to ₹2.5 lakh a year shared with EPF. */
object Vpf : RetirementPlan() {
    override val id = "in-vpf"
    override val displayName = "VPF"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "Voluntary Provident Fund, sharing EPF’s ₹2.5 lakh tax-free interest cap"
    override val accountKeywords = listOf("VPF", "Voluntary")
    override val source = "Income-tax Act section 10(11), Finance Act 2021 (incometaxindia.gov.in)"
    override val advanced = true

    override val limits = (2021..2026).map { AnnualLimit(it, 25_000_000) }
}
