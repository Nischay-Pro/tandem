package app.tandemforactual.data.regions.us.retirement.fsa

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** Health flexible spending account; the employer sets whether any of it carries over. */
object HealthFsa : RetirementPlan() {
    override val id = "us-health-fsa"
    override val displayName = "Health FSA"
    override val region = Region.US
    override val currency = "USD"
    override val description = "Health flexible spending account through an employer"
    override val accountKeywords = listOf("FSA", "Flexible spending")
    override val source = "IRS health FSA salary reduction limit (irs.gov)"

    override val limits =
        listOf(
            2015 to 255_000L,
            2016 to 255_000L,
            2017 to 260_000L,
            2018 to 265_000L,
            2019 to 270_000L,
            2020 to 275_000L,
            2021 to 275_000L,
            2022 to 285_000L,
            2023 to 305_000L,
            2024 to 320_000L,
            2025 to 330_000L,
            2026 to 340_000L,
            2027 to 350_000L,
        ).map { (year, amount) -> AnnualLimit(year, amount) }
}
