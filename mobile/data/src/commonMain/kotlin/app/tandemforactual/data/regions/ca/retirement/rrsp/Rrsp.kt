package app.tandemforactual.data.regions.ca.retirement.rrsp

import app.tandemforactual.data.regions.Region
import app.tandemforactual.data.regions.global.retirement.AnnualLimit
import app.tandemforactual.data.regions.global.retirement.RetirementPlan

/** The RRSP dollar maximum; a person's own room is 18% of last year's earned income up to this. */
object Rrsp : RetirementPlan() {
    override val id = "ca-rrsp"
    override val displayName = "RRSP"
    override val region = Region.CA
    override val currency = "CAD"
    override val description = "Registered retirement savings plan, up to 18% of last year’s earned income"
    override val accountKeywords = listOf("RRSP")
    override val source = "CRA MP, DB, RRSP, DPSP, ALDA, TFSA limits (canada.ca)"

    override val limits =
        listOf(
            2015 to 2_493_000L,
            2016 to 2_537_000L,
            2017 to 2_601_000L,
            2018 to 2_623_000L,
            2019 to 2_650_000L,
            2020 to 2_723_000L,
            2021 to 2_783_000L,
            2022 to 2_921_000L,
            2023 to 3_078_000L,
            2024 to 3_156_000L,
            2025 to 3_249_000L,
            2026 to 3_381_000L,
        ).map { (year, amount) -> AnnualLimit(year, amount) }

    /**
     * Your own room for [year]: 18% of last year's earned income up to the dollar limit, less last year's pension adjustment,
     * plus unused room carried forward. Your notice of assessment shows the same figure as your deduction limit.
     */
    fun personalRoom(
        year: Int,
        earnedIncomeMinor: Long,
        pensionAdjustmentMinor: Long,
        carryForwardMinor: Long,
    ): Long =
        (minOf(earnedIncomeMinor * 18 / 100, limitFor(year).limit.standardMinor) - pensionAdjustmentMinor).coerceAtLeast(0) +
            carryForwardMinor
}
