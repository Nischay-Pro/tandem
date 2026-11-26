package app.tandemforactual.data.regions.india.retirement.vpf

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.RetirementPlan
import app.tandemforactual.data.regions.india.retirement.epf.Epf

/** Voluntary Provident Fund; its contributions count toward the same ₹2.5 lakh a year as EPF before interest is taxed. */
object Vpf : RetirementPlan() {
    override val id = "in-vpf"
    override val displayName = "VPF"
    override val region = Region.IN
    override val currency = "INR"
    override val description = "Voluntary Provident Fund, sharing EPF’s ₹2.5 lakh tax-free interest cap"
    override val accountKeywords = listOf("VPF", "Voluntary")
    override val yearStartMonth = 4
    override val source = "Income-tax Act section 10(11), Finance Act 2021 (incometaxindia.gov.in)"
    override val advanced = true
    override val sharesRoomWith = Epf.id

    override val limits = Epf.limits
}
