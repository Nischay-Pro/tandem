package app.tandemforactual.data.regions.ca.retirement.tfsa

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** The yearly TFSA dollar limit; unused room carries forward. */
object Tfsa : RetirementPlan() {
    override val id = "ca-tfsa"
    override val displayName = "TFSA"
    override val region = Region.CA
    override val currency = "CAD"
    override val description = "Tax-free savings account; unused room carries forward"
    override val source = "CRA TFSA dollar limit (canada.ca)"

    override val limits =
        listOf(
            2015 to 1_000_000L,
            2016 to 550_000L,
            2017 to 550_000L,
            2018 to 550_000L,
            2019 to 600_000L,
            2020 to 600_000L,
            2021 to 600_000L,
            2022 to 600_000L,
            2023 to 650_000L,
            2024 to 700_000L,
            2025 to 700_000L,
            2026 to 700_000L,
        ).map { (year, amount) -> AnnualLimit(year, amount) }
}
