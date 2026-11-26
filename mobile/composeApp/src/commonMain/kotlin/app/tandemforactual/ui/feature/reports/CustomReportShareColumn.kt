package app.tandemforactual.ui.feature.reports

import app.tandemforactual.ui.core.format.wholePercent

internal data class CustomReportShareColumn(
    val rows: List<String>,
    val total: String,
)

/**
 * Each group's share of everything that added up above zero. Groups that net to zero or less get a dash, and the total
 * only claims 100% when every group counts toward it.
 */
internal fun customReportShareColumn(totals: List<Long>): CustomReportShareColumn? {
    val positive = totals.filter { it > 0L }.sum()
    if (positive <= 0L) return null
    val rows = totals.map { if (it > 0L) wholePercent((it * 10_000 / positive).toInt()) else "—" }
    return CustomReportShareColumn(rows, if (totals.all { it > 0L }) "100%" else "—")
}
