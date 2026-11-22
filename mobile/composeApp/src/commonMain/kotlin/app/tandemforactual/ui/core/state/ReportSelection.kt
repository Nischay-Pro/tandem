package app.tandemforactual.ui.core.state

internal enum class ReportKind(
    val title: String,
) {
    SPENDING_BY_CATEGORY("Spending by category"),
    SPENDING_OVER_TIME("Spending over time"),
    INCOME_AND_SPENDING("Income and spending"),
    BUDGETED_AND_SPENT("Budgeted and spent"),
    NET_WORTH("Net worth"),
}

internal enum class ReportPeriodMode {
    MONTH,
    RANGE,
}
