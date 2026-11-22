package app.tandemforactual.data.regions.global.retirement

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.ca.retirement.rrsp.Rrsp
import app.tandemforactual.data.regions.ca.retirement.tfsa.Tfsa
import app.tandemforactual.data.regions.india.retirement.epf.Epf
import app.tandemforactual.data.regions.india.retirement.nps.Nps
import app.tandemforactual.data.regions.india.retirement.ppf.Ppf
import app.tandemforactual.data.regions.us.retirement.fsa.HealthFsa
import app.tandemforactual.data.regions.us.retirement.hsa.Hsa
import app.tandemforactual.data.regions.us.retirement.ira.Ira
import app.tandemforactual.data.regions.us.retirement.k401.K401

/** Every retirement and tax-advantaged account type Tandem knows. Register new plans here. */
object RetirementPlans {
    val all: List<RetirementPlan> = listOf(K401, Ira, Hsa, HealthFsa, Rrsp, Tfsa, Epf, Ppf, Nps)

    fun forRegion(region: Region): List<RetirementPlan> = all.filter { it.region == region }

    fun byId(id: String): RetirementPlan? = all.firstOrNull { it.id == id }

    val regions: List<Region> get() = all.map { it.region }.distinct()
}
