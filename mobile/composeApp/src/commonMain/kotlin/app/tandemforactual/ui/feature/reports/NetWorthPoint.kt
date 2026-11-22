package app.tandemforactual.ui.feature.reports

internal data class NetWorthPoint(
    val monthId: String,
    val month: String,
    val allAccountsAssetsMinor: Long,
    val allAccountsDebtsMinor: Long,
    val onBudgetAssetsMinor: Long,
    val onBudgetDebtsMinor: Long,
) {
    val allAccountsMinor: Long get() = allAccountsAssetsMinor - allAccountsDebtsMinor
    val onBudgetMinor: Long get() = onBudgetAssetsMinor - onBudgetDebtsMinor
}
