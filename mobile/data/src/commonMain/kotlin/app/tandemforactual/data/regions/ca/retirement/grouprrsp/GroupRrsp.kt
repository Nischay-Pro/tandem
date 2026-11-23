package app.tandemforactual.data.regions.ca.retirement.grouprrsp

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** An RRSP your employer runs; what goes in, from you or them, uses your own RRSP room. */
object GroupRrsp : RetirementPlan() {
    override val id = "ca-group-rrsp"
    override val displayName = "Group RRSP"
    override val region = Region.CA
    override val currency = "CAD"
    override val description = "RRSP through work, sharing your RRSP room"
    override val accountKeywords = listOf("Group RRSP", "GRRSP")
    override val source = Rrsp.source
    override val advanced = true
    override val sharesRoomWith = Rrsp.id
    override val limits = Rrsp.limits
}
