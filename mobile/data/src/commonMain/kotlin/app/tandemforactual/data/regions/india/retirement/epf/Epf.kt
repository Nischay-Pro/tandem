package app.tandemforactual.data.regions.india.retirement.epf

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/**
 * Employees' Provident Fund. Interest stays tax-free on up to ₹2.5 lakh of employee contributions a year, from the financial
 * year starting April 2021; above that, interest is taxed.
 */
object Epf : RetirementPlan() {
    override val id = "in-epf"
    override val displayName = "EPF"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "Employees’ Provident Fund, tax-free interest on up to ₹2.5 lakh a year"
    override val accountKeywords = listOf("EPF", "Provident")
    override val yearStartMonth = 4
    override val source = "Income-tax Act section 10(11), Finance Act 2021 (incometaxindia.gov.in)"

    override val limits = (2021..2026).map { AnnualLimit(it, 25_000_000) }
}
