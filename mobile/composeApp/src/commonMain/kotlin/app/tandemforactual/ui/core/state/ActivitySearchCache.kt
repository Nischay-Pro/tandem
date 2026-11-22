@file:Suppress("ktlint:standard:function-naming")

package app.tandemforactual.ui.core.state
import app.tandemforactual.data.database.BudgetRecordEntity
import app.tandemforactual.data.database.SearchableBudgetTransaction
import app.tandemforactual.data.store.BudgetDisplaySettings
import app.tandemforactual.ui.core.search.ActivitySearchRecord

internal data class ActivitySearchCache(
    val budgetId: String,
    val sourceTransactions: List<SearchableBudgetTransaction>,
    val sourceAccounts: List<BudgetRecordEntity>,
    val sourcePayees: List<BudgetRecordEntity>,
    val sourceCategories: List<BudgetRecordEntity>,
    val displaySettings: BudgetDisplaySettings?,
    val searchableRecords: List<ActivitySearchRecord>,
) {
    fun matches(
        budgetId: String,
        transactions: List<SearchableBudgetTransaction>,
        accounts: List<BudgetRecordEntity>,
        payees: List<BudgetRecordEntity>,
        categories: List<BudgetRecordEntity>,
        displaySettings: BudgetDisplaySettings?,
    ): Boolean =
        this.budgetId == budgetId &&
            sourceTransactions === transactions &&
            sourceAccounts === accounts &&
            sourcePayees === payees &&
            sourceCategories === categories &&
            this.displaySettings == displaySettings
}
