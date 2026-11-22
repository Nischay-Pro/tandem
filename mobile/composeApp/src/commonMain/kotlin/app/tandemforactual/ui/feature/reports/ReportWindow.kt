package app.tandemforactual.ui.feature.reports

internal data class ReportWindow(
    val startDate: String,
    val throughDate: String,
    val startMonthId: String,
    val endMonthId: String,
    val monthIds: List<String>,
    val allDates: Boolean = false,
) {
    val monthCount: Int get() = monthIds.size
}
