@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.ui.core.session.TandemMobileSession
import app.tandemforactual.ui.core.session.observeActivitySearchRecords

/** The open budget's transactions as stored on the device, or null until they've loaded. */
@Composable
internal fun rememberBudgetTransactions(
    state: AppState,
    mobileSession: TandemMobileSession,
): List<SearchableBudgetTransaction>? {
    val budgetId = state.selectedBudgetId
    LaunchedEffect(budgetId, mobileSession.databaseGeneration) {
        if (budgetId.isNotBlank()) mobileSession.observeActivitySearchRecords(budgetId)
    }
    return mobileSession.activitySearchRecords.takeIf { mobileSession.activitySearchRecordsBudgetId == budgetId && budgetId.isNotBlank() }
}

/** Ids of categories that hold income, either flagged themselves or sitting in an income group. */
internal fun TandemMobileSession.incomeCategoryIds(): Set<String> {
    val incomeGroups =
        categoryGroupRecords
            .filter { it.recordBody()?.booleanValue("is_income") == true }
            .mapTo(mutableSetOf()) { it.recordId }
    return categoryRecords
        .filter { record ->
            val body = record.recordBody()
            body?.booleanValue("is_income") == true ||
                (body?.stringValue("group") ?: body?.stringValue("cat_group") ?: body?.stringValue("group_id")) in incomeGroups
        }.mapTo(mutableSetOf()) { it.recordId }
}

@Composable
internal fun TandemMobileSession.rememberRecordNames(): RecordNames =
    remember(categoryRecords, payeeRecords, accountRecords) {
        RecordNames(
            categoryRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
            payeeRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
            accountRecords.mapNotNull { r -> r.recordName()?.let { r.recordId to it } }.toMap(),
        )
    }

internal data class RecordNames(
    val categories: Map<String, String>,
    val payees: Map<String, String>,
    val accounts: Map<String, String>,
) {
    fun category(id: String?): String = id?.takeIf { it.isNotEmpty() }?.let { categories[it] ?: "Deleted category" } ?: "Uncategorized"
}

/**
 * Money in for the selected month: income categories without starting balances, plus deposits that aren't categorized yet,
 * and the month's transactions used for counts.
 */
internal fun monthMoneyIn(
    state: AppState,
    month: LiveBudgetMonthSummary?,
    transactions: List<TransactionItem>,
): Pair<Long, List<TransactionItem>> {
    val incomeCategories =
        month
            ?.groups
            .orEmpty()
            .filter { it.isIncome }
            .flatMap { it.categories }
    val startingBalances = incomeCategories.filter { it.name == "Starting Balances" }.map { it.id }.toSet()
    val monthTransactions =
        transactions.filter {
            it.dateIso.startsWith(state.selectedMonthId) &&
                !it.isTransfer &&
                it.categoryId !in startingBalances &&
                it.payee != "Starting Balance"
        }
    val income =
        incomeCategories.filterNot { it.id in startingBalances }.sumOf { it.activityMinor } +
            monthTransactions.filter { it.amountMinorUnits > 0 && it.categoryId.isNullOrBlank() }.sumOf { it.amountMinorUnits }
    return income to monthTransactions
}
