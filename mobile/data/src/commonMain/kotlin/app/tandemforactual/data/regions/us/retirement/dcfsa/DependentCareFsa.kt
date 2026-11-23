package app.tandemforactual.data.regions.us.retirement.dcfsa

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Dependent care FSA; the limit is per household and rose to $7,500 in 2026. */
object DependentCareFsa : RetirementPlan() {
    override val id = "us-dependent-care-fsa"
    override val displayName = "Dependent care FSA"
    override val region = Region.US
    override val currency = "USD"
    override val description = "Child and dependent care through an employer, per household"
    override val accountKeywords = listOf("Dependent care", "DCFSA", "DCAP")
    override val source = "IRC section 129, as amended in 2025 (irs.gov)"
    override val advanced = true

    override val limits = (2015..2025).map { AnnualLimit(it, 500_000) } + AnnualLimit(2026, 750_000)
}
