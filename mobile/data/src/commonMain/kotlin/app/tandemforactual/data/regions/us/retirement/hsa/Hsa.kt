package app.tandemforactual.data.regions.us.retirement.hsa

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Health savings account, with self-only and family limits and a catch-up from 55. */
object Hsa : RetirementPlan() {
    override val id = "us-hsa"
    override val displayName = "HSA"
    override val region = Region.US
    override val currency = "USD"
    override val description = "Health savings account with a high-deductible plan"
    override val source = "IRS HSA inflation-adjusted amounts (irs.gov)"
    override val catchUpAge = 55

    override val limits =
        listOf(
            2015 to (335_000L to 665_000L),
            2016 to (335_000L to 675_000L),
            2017 to (340_000L to 675_000L),
            2018 to (345_000L to 690_000L),
            2019 to (350_000L to 700_000L),
            2020 to (355_000L to 710_000L),
            2021 to (360_000L to 720_000L),
            2022 to (365_000L to 730_000L),
            2023 to (385_000L to 775_000L),
            2024 to (415_000L to 830_000L),
            2025 to (430_000L to 855_000L),
            2026 to (440_000L to 875_000L),
        ).map { (year, amounts) -> AnnualLimit(year, amounts.first, 100_000, familyMinor = amounts.second) }
}
