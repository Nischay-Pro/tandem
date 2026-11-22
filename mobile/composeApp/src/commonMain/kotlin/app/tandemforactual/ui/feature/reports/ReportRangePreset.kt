package app.tandemforactual.ui.feature.reports

internal enum class ReportRangePreset(
    val label: String,
    val monthCount: Int?,
) {
    LAST_3_MONTHS("Last 3 months", 3),
    LAST_6_MONTHS("Last 6 months", 6),
    LAST_12_MONTHS("Last 12 months", 12),
    LAST_3_YEARS("Last 3 years", 36),
    YEAR_TO_DATE("Year to date", null),
    LAST_YEAR("Last year", null),
    ALL_TIME("All time", null),
}
