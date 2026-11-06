package app.tandemforactual.data.database

data class CategoryMonthAggregate(
    val monthId: String,
    val categoryId: String,
    val spendingMinor: Long,
)

data class MonthlyIncomeSpendingAggregate(
    val monthId: String,
    val incomeMinor: Long,
    val spendingMinor: Long,
)

data class AccountDayAggregate(
    val accountId: String,
    val date: String,
    val amountMinor: Long,
)
