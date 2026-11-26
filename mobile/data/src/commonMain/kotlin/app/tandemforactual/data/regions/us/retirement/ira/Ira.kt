package app.tandemforactual.data.regions.us.retirement.ira

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Traditional and Roth IRAs share one yearly limit across all of a person's IRAs. */
object Ira : RetirementPlan() {
    override val id = "us-ira"
    override val displayName = "IRA"
    override val region = Region.US
    override val currency = "USD"
    override val description = "Traditional and Roth IRAs combined"
    override val accountKeywords = listOf("IRA", "Roth")
    override val source = "IRS IRA contribution limits (irs.gov)"
    override val catchUpAge = 50

    override val limits =
        (2015..2018).map { AnnualLimit(it, 550_000, 100_000) } +
            (2019..2022).map { AnnualLimit(it, 600_000, 100_000) } +
            listOf(
                AnnualLimit(2023, 650_000, 100_000),
                AnnualLimit(2024, 700_000, 100_000),
                AnnualLimit(2025, 700_000, 100_000),
                AnnualLimit(2026, 750_000, 110_000),
                AnnualLimit(2027, 750_000, 110_000),
            )
}
