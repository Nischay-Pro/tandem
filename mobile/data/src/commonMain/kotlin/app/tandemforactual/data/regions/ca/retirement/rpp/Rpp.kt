package app.tandemforactual.data.regions.ca.retirement.rpp

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/**
 * A registered pension plan through work. The money purchase limit covers you and your employer together; it is the next
 * year's RRSP dollar limit. What goes in comes back as a pension adjustment that lowers next year's RRSP room.
 */
object Rpp : RetirementPlan() {
    override val id = "ca-rpp"
    override val displayName = "RPP"
    override val region = Region.CA
    override val currency = "CAD"
    override val description = "Pension plan through work; lowers your RRSP room through a pension adjustment"
    override val accountKeywords = listOf("RPP", "Pension plan", "DPSP")
    override val source = Rrsp.source
    override val advanced = true

    override val limits = Rrsp.moneyPurchaseLimits
}
